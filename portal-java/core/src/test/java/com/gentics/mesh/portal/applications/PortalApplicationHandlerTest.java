package com.gentics.mesh.portal.applications;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;

import org.junit.Test;
import org.mockito.InOrder;

import com.gentics.mesh.portal.AbstractPortalTest;
import com.gentics.mesh.portal.api.config.InvalidConfigurationException;
import com.gentics.mesh.portal.api.Constants;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Test cases for PortalApplicationHandler
 */
public class PortalApplicationHandlerTest extends AbstractPortalTest {
	/**
	 * Test handling a single portal application
	 * @throws InvalidConfigurationException
	 */
	@Test
	public void testSingleApplication() throws InvalidConfigurationException {
		PortalApplication app = app("app");
		PortalApplicationRegistry registry = registry(app);

		RoutingContext rc = rc("app");

		PortalApplicationHandler handler = new PortalApplicationHandler(config, registry);
		handler.handle(rc);

		verify(app).handle(any());
		verify(rc).next();
	}

	/**
	 * Test handling of multiple portal applications
	 * @throws InvalidConfigurationException
	 */
	@Test
	public void testMultipleApplications() throws InvalidConfigurationException {
		PortalApplication firstApp = app("first_app");
		PortalApplication secondApp = app("second_app");
		PortalApplication thirdApp = app("third_app");

		// we do not register the third app
		PortalApplicationRegistry registry = registry(firstApp, secondApp);

		// Mock routing context with a node containing the field
		RoutingContext rc = rc("first_app, second_app, first_app, third_app");

		PortalApplicationHandler handler = new PortalApplicationHandler(config, registry);
		handler.handle(rc);

		InOrder inOrder = inOrder(rc, firstApp, secondApp, thirdApp);

		inOrder.verify(firstApp).handle(any());
		inOrder.verify(secondApp).handle(any());
		inOrder.verify(thirdApp, never()).handle(any());
		inOrder.verify(rc).next();
	}

	/**
	 * Test empty field
	 * @throws InvalidConfigurationException
	 */
	@Test
	public void testEmpty() throws InvalidConfigurationException {
		PortalApplicationRegistry registry = registry();

		// Mock routing context with a node (without the portalapp field)
		RoutingContext rc = rc(null);

		PortalApplicationHandler handler = new PortalApplicationHandler(config, registry);
		handler.handle(rc);

		verify(rc).next();
	}

	/**
	 * Test portal app, which ends the request
	 * @throws InvalidConfigurationException
	 */
	@Test
	public void testEnding() throws InvalidConfigurationException {
		PortalApplication first = app("first");
		PortalApplication second = app("second", rc -> rc.response().end());
		PortalApplication third = app("third");
		PortalApplicationRegistry registry = registry(first, second, third);

		RoutingContext rc = rc("first,second,third");

		PortalApplicationHandler handler = new PortalApplicationHandler(config, registry);
		handler.handle(rc);

		verify(first).handle(any());
		verify(second).handle(any());
		verify(third, never()).handle(any());
		verify(rc, never()).next();
		verify(rc.response()).end();
	}

	/**
	 * Create a portal application, that just calls "next" on the routing context
	 * @param key key
	 * @return portal application
	 */
	protected PortalApplication app(String key) {
		return app(key, RoutingContext::next);
	}

	/**
	 * Create a portal application with a custom handler
	 * @param key key
	 * @param handler handler
	 * @return portal application
	 */
	protected PortalApplication app(String key, Consumer<RoutingContext> handler) {
		return spy(new PortalApplication() {

			@Override
			public void handle(RoutingContext rc) {
				handler.accept(rc);
			}

			@Override
			public PortalApplication init() throws InvalidConfigurationException {
				return this;
			}

			@Override
			public String getApplicationKey() {
				return key;
			}
		});
	}

	/**
	 * Create a registry having the given apps registered
	 * @param apps list of apps
	 * @return registry
	 * @throws InvalidConfigurationException
	 */
	protected PortalApplicationRegistry registry(PortalApplication... apps) throws InvalidConfigurationException {
		PortalApplicationRegistry registry = new BasicPortalApplicationRegistry() {
			@Override
			public void registerApplications() throws InvalidConfigurationException {
				for (PortalApplication app : apps) {
					applications.put(app.getApplicationKey(), app);
				}
			}
		};

		registry.registerApplications();
		return registry;
	}

	/**
	 * Mock the routing context, containing a node with the portalapp field
	 * @param portalappField value of the field
	 * @return routing context
	 */
	protected RoutingContext rc(String portalappField) {
		// Mock routing context with a node containing the field
		RoutingContext rc = mockRoutingContext("/test", "");
		JsonObject fields = mock(JsonObject.class);
		when(fields.getString(PortalApplicationHandler.RC_PORTAL_APP)).thenReturn(portalappField);
		JsonObject node = mock(JsonObject.class);
		when(node.getJsonObject("fields")).thenReturn(fields);
		when(rc.get(Constants.RC_NODE_TO_RENDER)).thenReturn(node);
		return rc;
	}
}
