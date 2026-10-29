package com.gentics.mesh.portal.cache;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gentics.mesh.core.rest.MeshEvent;
import com.gentics.mesh.core.rest.branch.BranchResponse;
import com.gentics.mesh.core.rest.user.UserResponse;
import com.gentics.mesh.parameter.client.GenericParametersImpl;
import com.gentics.mesh.portal.api.authentication.PortalUser;
import com.gentics.mesh.portal.api.cache.CacheBuilder;
import com.gentics.mesh.portal.api.cache.CacheProvider;
import com.gentics.mesh.portal.api.cache.ExpireMode;
import com.gentics.mesh.portal.api.cache.PortalCache;
import com.gentics.mesh.portal.client.MeshRestClientProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.CachingOptions;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.gentics.mesh.portal.util.RxUtils;
import com.gentics.mesh.rest.client.EventbusEvent;
import com.gentics.mesh.rest.client.MeshRestClientUtil;
import com.gentics.mesh.rest.client.MeshWebsocket;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.reactivex.core.Vertx;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;


/**
 * Default cache provider.
 *
 * <p>
 * It will put the current users {@link UserResponse#getRolesHash() roles hash} in the routing context, and
 * use it in the default {@link #defaultKeyMapper(RoutingContext) key mapper}.
 * </p>
 *
 * <p>
 * Currently the providers {@link Builder} will only create {@link Caffeine} caches.
 * </p>
 */
@Singleton
public class CacheProviderImpl implements CacheProvider {

	/**
	 * The username used by the
	 * {@link #userRolesKeymapper(RoutingContext) user roles key mapper} when no
	 * user is logged in.
	 */
	private static final String USER_ANONYMOUS = "gpj.cache.user.anonymous";
	/** The default cache. */
	private static final String DEFAULT_NAMESPACE = "gpj.cache.default";
	/** The expiration time for user roles hashes. */
	private static final int USER_ROLES_EXPIRE_TIME = 15;

	private final PortalConfig portalConfig;
	private final CachingOptions options;
	private final MeshRestClientProvider clientProvider;
	private final MeshWebsocket eventbus;
	private final Vertx rxVertx;

	private final Logger log = LoggerFactory.getLogger(CacheProviderImpl.class);

	/** A <code>Map</code> from cache namespaces to caches. */
	private final Map<String, PortalCache<?>> caches;
	/** A cache for user role hashes. */
	private final PortalCache<String> userRoles;
	/** Custom configured cache key parameters from the routing context. */
	private final List<String> customKeyParams;

	/**
	 * Whether caching is currently enabled.
	 *
	 * <p>
	 * As of now, this value is only set during startup, but a special route for activating and deactivating
	 * caching during runtime might be added in the future.
	 * </p>
	 */
	private boolean enabled;

	/** The default events for automatically clearing the caches. */
	private final MeshEvent[] defaultClearEvents;
	/** The schemanames for the {@link #defaultEventFilter(EventbusEvent) default event filter}. */
	private final Set<String> schemaNames;

	@Inject
	public CacheProviderImpl(PortalConfig config, MeshRestClientProvider clientProvider, MeshWebsocket eventbus, Vertx rxVertx) {
		this.portalConfig = config;
		this.options = config.getCachingOptions();
		this.clientProvider = clientProvider;
		this.eventbus = eventbus;
		this.rxVertx = rxVertx;
		this.caches = new LinkedHashMap<>();
		this.enabled = this.options.isEnabled();

		this.userRoles = this.<String>cacheBuilder()
			.keyMapper(this::userRolesKeymapper)
			.namespace("user-roles")
			.expireMode(ExpireMode.Write)
			// TODO Ideally the expireTime for this cache would be rather high, and we clear it only when the
			// permissions might have changed. Currently Mesh does not send events when a user is added to a
			// group, or the roles of a group change.
			// .clearEvents(MeshEvent.USER_UPDATED, MeshEvent.GROUP_UPDATED, MeshEvent.ROLE_UPDATED)
			.expireTime(USER_ROLES_EXPIRE_TIME)
			.build();

		this.customKeyParams = options.getCustomParameters();

		this.defaultClearEvents = new MeshEvent[] {
			MeshEvent.NODE_UPDATED,
			MeshEvent.NODE_CREATED,
			MeshEvent.NODE_DELETED,
			MeshEvent.SCHEMA_UPDATED
		};

		this.schemaNames = new HashSet<>(Arrays.asList(
			config.getFolderSchema(),
			config.getContentSchema(),
			config.getBinaryContentSchema()));
	}

	@Override
	public void clearAll() {
		log.info("Clearing all caches");

		caches.values().forEach(PortalCache::clear);
	}

	@Override
	public void clear(String namespace) {
		if (caches.containsKey(namespace)) {
			log.info("Clearing cache \"{}\"", namespace);
			caches.get(namespace).clear();
		} else {
			log.warn("Cache \"{}\" not found", namespace);
		}
	}

	/**
	 * Load the {@link UserResponse#getRolesHash() user roles hash} from cache or from Mesh,
	 * and put it in the routing context.
	 *
	 * @see PortalRoutingContext#setUserRolesHash(String)
	 * @param rc The current routing context
	 */
	@Override
	public void handle(RoutingContext rc) {
		log.debug("Setting up user info");
		userRoles.get(rc, this::loadUserRoles)
			.subscribe(
				(hash, error) -> {
					if (error != null) {
						log.error("Could not load user info: {}", error.getMessage(), error);
					} else {
						log.trace("User roles hash: {}", hash);
						wrap(rc).setUserRolesHash(hash);
					}

					rc.next();
				});
	}

	@Override
	public boolean enabled() {
		return enabled;
	}

	@Override
	public <T> CacheBuilder<T> cacheBuilder() {
		return new Builder<>();
	}

	@Override
	public MeshEvent[] defaultClearEvents() {
		return defaultClearEvents;
	}

	@Override
	public boolean defaultEventFilter(EventbusEvent event) {
		ObjectNode json = event.getBodyAsJson();

		String schemaName = Optional.ofNullable(json.path("schemaName").textValue())
			.orElseGet(() -> json.path("schema").path("name").textValue());

		String projectName = json.path("project").path("name").textValue();

		return schemaNames.contains(schemaName) || Objects.equals(projectName, portalConfig.getMeshProject());
	}

	/**
	 * Load the current users <code>rolesHash</code> from Mesh.
	 *
	 * @param rc The current routing context
	 * @return A <code>Single</code> containing the users <code>rolesHash</code>
	 */
	private Single<String> loadUserRoles(RoutingContext rc) {
		log.debug("Loading user info from Mesh");

		return clientProvider.get(rc)
			.me(new GenericParametersImpl().setFields("rolesHash"))
			.toSingle()
			.observeOn(RxUtils.scheduler(rc))
			.onErrorReturn(error -> {
				log.error("Could not retrieve user roles hash");

				return new UserResponse().setRolesHash("missing-user-roles");
			})
			.map(UserResponse::getRolesHash);
	}

	/**
	 * Returns the {@link PortalUser#userId() user ID} of the logged in
	 * {@code PortalUser}, or {@link #USER_ANONYMOUS} if no user is logged
	 * in.
	 *
	 * @param rc The current routing context
	 * @return The user ID of the logged in user, or a string representing
	 * 		an anonymous user
	 */
	private String userRolesKeymapper(RoutingContext rc) {
		User user = rc.user();

		if (user instanceof PortalUser) {
			return ((PortalUser) user).userId();
		}

		return USER_ANONYMOUS;
	}

	/**
	 * Creates a cache key from the data in the routing context.
	 *
	 * The cache key will consist of the current {@link PortalRoutingContext#getLanguage() portal language} and the user
	 * roles hash loaded by the {@link #handle(RoutingContext) handler}.
	 *
	 * @param rc The current routing context
	 * @return A cache key containing the current language and user role hash
	 */
	private String defaultKeyMapper(RoutingContext rc) {
		if (!enabled) {
			return "";
		}

		PortalRoutingContext prc = wrap(rc);
		BranchResponse branch = prc.getBranch();

		return prc.getLanguage()
			+ '/'
			+ prc.getUserRolesHash()
			+ '/'
			+ (branch == null ? "default-branch" : branch.getUuid());
	}

	private String defaultKeyMapperWithCustomParams(RoutingContext rc) {
		StringBuilder cacheKey = new StringBuilder(defaultKeyMapper(rc));

		for (String key : customKeyParams) {
			Object val = rc.get(key);

			cacheKey.append('/').append(key).append("=").append(val == null ? "" : val.toString());
		}

		return cacheKey.toString();
	}

	/**
	 * Helper to create {@link PortalCache PortalCaches}.
	 *
	 * @param <T> The type of the objects stored in the cache
	 */
	private class Builder<T> implements CacheBuilder<T> {
		private String namespace;
		private Function<RoutingContext, String> keyMapper;
		private int maximumSize = -1;
		private ExpireMode expireMode = ExpireMode.Access;
		private int expireTime;
		private MeshEvent[] clearEvents;
		private Predicate<EventbusEvent> eventFilter;
		private boolean useCustomKeyParams = false;
		private String diskCacheRegion = null;

		private Builder() {
		}

		@Override
		public CacheBuilder<T> namespace(String namespace) {
			this.namespace = namespace;

			return this;
		}

		@Override
		public CacheBuilder<T> keyMapper(Function<RoutingContext, String> keyMapper) {
			this.keyMapper = keyMapper;

			return this;
		}

		@Override
		public CacheBuilder<T> maximumSize(int maximumSize) {
			this.maximumSize = maximumSize;

			return this;
		}

		@Override
		public CacheBuilder<T> expireMode(ExpireMode expireMode) {
			this.expireMode = expireMode;

			return this;
		}

		@Override
		public CacheBuilder<T> expireTime(int expireTime) {
			this.expireTime = expireTime;

			return this;
		}

		@Override
		public CacheBuilder<T> clearEvents(MeshEvent ...clearEvents) {
			this.clearEvents = clearEvents;

			return this;
		}

		@Override
		public CacheBuilder<T> eventFilter(Predicate<EventbusEvent> eventFilter) {
			this.eventFilter = eventFilter;

			return this;
		}

		@Override
		public CacheBuilder<T> useCustomKeyParams() {
			this.useCustomKeyParams = true;

			return this;
		}

		@Override
		public CacheBuilder<T> diskCacheRegion(String region) {
			this.diskCacheRegion = region;

			return this;
		}

		@Override
		public PortalCache<T> build() {
			if (namespace == null) {
				namespace = DEFAULT_NAMESPACE;
			}

			@SuppressWarnings("unchecked")
			PortalCache<T> cache = (PortalCache<T>) caches.get(namespace);

			if (cache != null) {
				// TODO check if the already created cache matches the current configuration.
				log.debug("Returning existing cache \"{}\"", namespace);

				return cache;
			}

			if (expireTime <= 0) {
				expireTime = Math.max(0, options.getExpireTime());
			}

			if (expireTime == 0) {
				log.warn("Expire time is set to 0 which effectively disables caching");
			}

			boolean defaultKeymapper = false;

			if (keyMapper == null) {
				defaultKeymapper = true;
				keyMapper = useCustomKeyParams
					? CacheProviderImpl.this::defaultKeyMapperWithCustomParams
					: CacheProviderImpl.this::defaultKeyMapper;
			}

			if (maximumSize <= 0) {
				maximumSize = Math.max(0, options.getMaximumSize());
			}

			if (maximumSize == 0) {
				log.warn("Maximum cache size is set to 0 which effectively disables caching");
			}

			if (log.isDebugEnabled()) {
				String keyMapperMsg;

				if (defaultKeymapper) {
					keyMapperMsg = useCustomKeyParams ? "default with custom params" : "default";
				} else {
					keyMapperMsg = "custom";
				}

				log.debug(
					"Creating {} cache \"{}\": maximum size: {}, expire mode: {}, expire time: {}, key mapper: {}",
					diskCacheRegion == null ? "Caffeine" : "JCS (" + diskCacheRegion + ")",
					namespace,
					maximumSize,
					expireMode,
					expireTime,
					keyMapperMsg);
			}

			if (StringUtils.isEmpty(diskCacheRegion)) {
				cache = new CaffeinePortalCache<>(
					CacheProviderImpl.this,
					keyMapper,
					maximumSize,
					expireMode,
					expireTime);
			} else {
				cache = new JcsPortalCache<>(
					options.getDiskCacheConfig(),
					CacheProviderImpl.this,
					keyMapper,
					maximumSize,
					expireMode,
					expireTime,
					diskCacheRegion,
					rxVertx);
			}

			caches.put(namespace, cache);

			if (clearEvents != null && clearEvents.length > 0) {
				if (DEFAULT_NAMESPACE.equals(namespace)) {
					log.warn("Registering clear events for default namespace");
				}

				if (eventFilter == null) {
					eventFilter = ignored -> true;
				}

				registerClearEvents(namespace, clearEvents, eventFilter);
			}

			return cache;
		}

		/**
		 * Add listeners to eventbus events that clear the cache when any of the specified events occur.
		 *
		 * <p>
		 * Only events which match the <code>eventFilter</code> will actually cause the cache to be cleared.
		 * </p>
		 *
		 * @param cacheId The cache to clear
		 * @param clearEvents The list of events which should cause a cache clear
		 * @param eventFilter A predicate to filter <code>clearEvents</code>
		 */
		private void registerClearEvents(String cacheId, MeshEvent[] clearEvents, Predicate<EventbusEvent> eventFilter) {
			eventbus.registerEvents(clearEvents);

			if (log.isDebugEnabled()) {
				log.debug(
					"Registering cache clear events for \"{}\": [{}]",
					cacheId,
					Stream.of(clearEvents).map(MeshEvent::getAddress).collect(Collectors.joining(", ")));
			}

			Observable.merge(
				eventbus.connections(),
				eventbus.events().filter(MeshRestClientUtil.isOneOf(clearEvents))
			).subscribe(toClearEventHandler(cacheId, eventFilter));
		}

		/**
		 * Create an event handler that will clear the specified cache, when the received event
		 * is either a reconnect event or it matches the specified <code>eventFilter</code>.
		 *
		 * @param cacheId The cache to clear
		 * @param eventFilter The predicate to filter events
		 * @return A handler which will clear the cache on matching received events
		 */
		private Consumer<Object> toClearEventHandler(String cacheId, Predicate<EventbusEvent> eventFilter) {
			return e -> {
				PortalCache<?> cache = caches.get(cacheId);

				if (cache == null) {
					log.debug("No cache for namespace \"{}\" found", cacheId);

					return;
				}

				if (e instanceof EventbusEvent) {
					EventbusEvent event = (EventbusEvent) e;

					if (!eventFilter.test(event)) {
						return;
					}

					cache.clear();

					log.debug("Clearing cache \"{}\" due event at address {}: {}",
						cacheId,
						event.getAddress(),
						event.getBody());
				} else {
					cache.clear();

					log.debug("Clearing cache \"{}\" due to eventbus reconnect", cacheId);
				}
			};
		}
	}
}
