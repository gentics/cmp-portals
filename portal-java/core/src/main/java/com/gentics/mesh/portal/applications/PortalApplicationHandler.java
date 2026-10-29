package com.gentics.mesh.portal.applications;

import com.gentics.mesh.portal.api.Constants;
import com.gentics.mesh.portal.api.config.InvalidConfigurationException;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.gentics.mesh.portal.handler.AbstractBaseHandler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;

/**
 * Checks if a loaded page needs business logic from the portal itself.
 *
 * When a page has the <code>portalapp</code> object property set to a name that is configured in the <code>applications</code> section of the
 * <code>server.yml</code>, the corresponding handling will be called with the current routing context.
 */
@Singleton
public class PortalApplicationHandler extends AbstractBaseHandler {

	/** The portal registry to be used by the portal handler. */
	private final PortalApplicationRegistry registry;

	/**
	 * The name of the object property.
	 *
	 * TODO Maybe make this configurable?
	 */
	public static final String RC_PORTAL_APP = "portalapp";

	private static final Logger log = LoggerFactory.getLogger(PortalApplicationHandler.class);

	/**
	 * Default constructor.
	 *
	 * @param config The portal configuration
	 * @param registry The application registry used by the portal application handler
	 */
	@Inject
	public PortalApplicationHandler(PortalConfig config, PortalApplicationRegistry registry) {
		try {
			registry.registerApplications();
		} catch (InvalidConfigurationException e) {
			throw new RuntimeException("Could not initialise portal applications: " + e.getMessage(), e);
		}

		this.registry = registry;
	}

	@Override
	public void addRoutes(Router router) {
		// Nothing to do, the routes need to be interleaved with the ContentPathHandler, so this has to be done
		// explicitly in the PortalHttpVerticle.
	}

	/**
	 * Checks if the {@link Constants#RC_NODE_TO_RENDER node to render} in the current routing context specifies portal applications, and calls the
	 * respective handlers if applicable.
	 *
	 * Note that this handler will only call {@link RoutingContext#next()} when no page data was found in the routing context, or when the
	 * <code>portalapp</code> field of the page is empty. Otherwise the configured application handlers are responsible for either calling the next handler, or
	 * {@link HttpServerResponse#end() ending} the response.
	 */
	@Override
	public void handle(RoutingContext rc) {
		Object node = rc.get(Constants.RC_NODE_TO_RENDER);

		PortalRoutingContext prc = wrap(rc);
		prc.setPortalApplications(null);

		if (node instanceof JsonObject) {
			String appConfig = Optional.of(((JsonObject) node))
				.map(n -> n.getJsonObject("fields"))
				.map(fields -> fields.getString(RC_PORTAL_APP))
				.orElse(null);

			if (appConfig != null) {
				String[] apps = appConfig.split(",");
				Set<String> added = new HashSet<>();
				List<PortalApplication> portalApplications = new ArrayList<>();

				for (String app : apps) {
					app = app.trim();

					if (registry.contains(app)) {
						if (!added.contains(app)) {
							added.add(app);
							log.debug("Found application handler: {}", app);
							portalApplications.add(registry.get(app));
						}
					}
				}

				if (!portalApplications.isEmpty()) {
					prc.setPortalApplications(portalApplications);
					prc.next();

					return;
				}
			}
		} else {
			log.warn("No valid JSON object \"{}\" found in routing context", Constants.RC_NODE_TO_RENDER);
			log.warn("This should have been provided by the content path handler");
		}

		rc.next();
	}
}
