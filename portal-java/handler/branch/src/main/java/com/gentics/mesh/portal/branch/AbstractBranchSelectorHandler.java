package com.gentics.mesh.portal.branch;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;

import com.gentics.mesh.portal.util.RxUtils;
import org.apache.commons.lang3.StringUtils;

import com.gentics.mesh.core.rest.branch.BranchResponse;
import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.api.branch.BranchProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.gentics.mesh.portal.handler.AbstractBaseHandler;

import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Abstract base class for branch selection handlers
 */
public abstract class AbstractBranchSelectorHandler extends AbstractBaseHandler {

	private static final Logger log = LoggerFactory.getLogger(AbstractBranchSelectorHandler.class);

	protected PortalConfig config;

	protected BranchProvider branchProvider;

	/**
	 * Create instance
	 * @param config portal config
	 * @param branchProvider branch provider
	 */
	public AbstractBranchSelectorHandler(PortalConfig config, BranchProvider branchProvider) {
		this.config = config;
		this.branchProvider = branchProvider;
	}

	/**
	 * Add handler to router (before api routes)
	 * @param router router
	 */
	public void addRoutes(Router router) {
		router.route().order(RouteOrder.BEFORE_API_ROUTES.getOrder()).handler(this);
	}

	@Override
	public void handle(RoutingContext rc) {
		branchProvider.findBranch(filter(rc))
			.observeOn(RxUtils.scheduler(rc))
			.subscribe(onSuccess(rc), onError(rc), onComplete(rc));
	}

	/**
	 * Filter for branch selection
	 * @param rc routing context
	 * @return filter
	 */
	protected abstract Predicate<? super BranchResponse> filter(RoutingContext rc);

	/**
	 * Success handler. The default implementation will just set the selected branch into the {@link PortalRoutingContext}.
	 * Implementation note: handler must always call {@link RoutingContext#next()} on the passed routing context
	 * @param rc routing context
	 * @return success handler
	 */
	protected Consumer<? super BranchResponse> onSuccess(RoutingContext rc) {
		return branch -> {
			log.trace("Selected branch {} for request", branch.getName());
			wrap(rc).setBranch(branch);
			rc.next();
		};
	}

	/**
	 * Error handler. The default implementation will call {@link RoutingContext#fail(Throwable)}
	 * Implementation note: handler must either call {@link RoutingContext#next()} or {@link RoutingContext#fail(int)} or {@link RoutingContext#fail(Throwable)} on the passed routing context.
	 * @param rc routing context
	 * @return error handler
	 */
	protected Consumer<? super Throwable> onError(RoutingContext rc) {
		return e -> {
			log.error("Error while selecting branch", e);
			rc.fail(e);
		};
	}

	/**
	 * Complete handler (called when no matching branch could be selected). The default implementation will proceed with the next handler
	 * Implementation note: handler must either call {@link RoutingContext#next()} or {@link RoutingContext#fail(int)} or {@link RoutingContext#fail(Throwable)} on the passed routing context.
	 * @param rc routing context
	 * @return "not found" handler
	 */
	protected Action onComplete(RoutingContext rc) {
		return () -> rc.next();
	}

	/**
	 * Check whether the branch matches the configured implementation version.
	 * Will always return <code>true</code> if no implementation version is configured.
	 * @param branch branch to match
	 * @return true if branch matches or no implementation version configured
	 */
	protected boolean matchesImplementationVersion(BranchResponse branch) {
		String implementationVersion = config.getImplementationVersion();

		if (StringUtils.isEmpty(implementationVersion)) {
			return true;
		}
		return branch.getTags().stream().filter(tag -> {
			return StringUtils.equals(BranchProvider.IMPLEMENTATION_VERSION_TAGFAMILY, tag.getTagFamily()) &&
					StringUtils.equals(implementationVersion, tag.getName());
		}).findFirst().isPresent();
	}

	/**
	 * Check whether the branch matches the channel uuid
	 * @param branch branch to match
	 * @param channelUuid channel uuid
	 * @return true if branch matches, false if not
	 */
	protected boolean matchesChannelUuid(BranchResponse branch, String channelUuid) {
		if (StringUtils.isEmpty(channelUuid)) {
			// when channel uuid is empty, the branch matches, if it does not have any channelUuid tag
			return !branch.getTags().stream().filter(tag -> {
				return StringUtils.equals(BranchProvider.CHANNEL_UUID_TAGFAMILY, tag.getTagFamily());
			}).findFirst().isPresent();
		} else {
			// when channel uuid is not empty, the branch matches, if it has a channelUuid tag with the proper name
			return branch.getTags().stream().filter(tag -> {
				return StringUtils.equals(BranchProvider.CHANNEL_UUID_TAGFAMILY, tag.getTagFamily()) &&
						StringUtils.equals(channelUuid, tag.getName());
			}).findFirst().isPresent();
		}
	}
}
