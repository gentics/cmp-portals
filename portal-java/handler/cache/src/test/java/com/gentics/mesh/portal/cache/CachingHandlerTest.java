package com.gentics.mesh.portal.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;

import io.vertx.core.net.HostAndPort;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.gentics.mesh.portal.AbstractPortalTest;
import com.gentics.mesh.portal.TestRoutingContext;
import com.gentics.mesh.portal.api.Constants;
import com.gentics.mesh.portal.api.cache.CacheProvider;
import com.gentics.mesh.portal.client.MeshRestClientProvider;
import com.gentics.mesh.portal.context.PortalRoutingContext;

import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

/**
 * Test the {@link CachingHandler}.
 */
@RunWith(Parameterized.class)
public class CachingHandlerTest extends AbstractPortalTest {

	@Parameters(name = "{index}: authenticated: {0}")
	public static Collection<Object> data() {
		return Arrays.asList(false, true);
	}

	private final User userMock = mock(User.class);

	@Parameter
	public boolean authenticated;

	private CacheProvider cacheProvider;

	@Before
	public void setup() throws Exception {
		super.setup();

		config.getCachingOptions().setDiskCacheConfig("src/test/resources/cache.ccf");

		cacheProvider = new CacheProviderImpl(
			config,
			new MeshRestClientProvider(clientMock, config),
			mockEventbus(),
			rxVertx);
	}

	@Test
	public void testCacheHandlerEmpty() throws InterruptedException {
		CachingHandler handler = new CachingHandler(vertx, config, cacheProvider, metricsServiceMock);
		TestRoutingContext rc = mockRoutingContext("/test", "");

		when(rc.request().authority()).thenReturn(mock(HostAndPort.class));

		if (authenticated) {
			when(rc.user()).thenReturn(userMock);
		}

		// The parameter is no longer added by default.
		config.getCachingOptions().getCustomParameters().add("CookieDisclaimerAccepted");
		handler.handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();

		verify(rc).get(PortalRoutingContext.DISABLE_CACHE_KEY);
		verify(rc.request()).scheme();
		verify(rc.request().authority()).host();
		verify(rc.request()).uri();
		verify(rc.request(), times(2)).authority();
		verify(rc, times(8)).request(); // rc.request() is called 1 time in the setup of this test, 3 times in the handle() method and 4 times in the verify() calls above.
		verify(rc).next();
		verify(rc).get(PortalRoutingContext.BRANCH_KEY);
		verify(rc).get("CookieDisclaimerAccepted");
		verify(rc).get(PortalRoutingContext.USER_ROLES_KEY);
		verify(rc).get(Constants.RC_LANGUAGE);
		// Comment the next line, if using rc.await(TIMEOUT) for debugging.
		verify(rc).await();
		verify(rc).await(anyInt());
		verify(rc).latch();
		verifyNoMoreInteractions(rc);
		verifyNoMoreInteractions(rc.request());
	}

	/**
	 * Verify that a cache hit is returned if the cache contains a response for the uri
	 * @throws Exception
	 */
	@Test
	public void testCacheHandlerFull() throws Exception {
		String content = "bliblablub";
		CachingHandler handler = new CachingHandler(vertx, config, cacheProvider, metricsServiceMock);

		// Simulate the cache write
		TestRoutingContext rc = mockRoutingContext("/test", "");

		when(rc.request().authority()).thenReturn(mock(HostAndPort.class));

		if (authenticated) {
			when(rc.user()).thenReturn(userMock);
		}

		handler.cache(rc, content);
		verify(rc).get(PortalRoutingContext.DISABLE_CACHE_KEY);

		// Simulate the cache access
		rc = mockRoutingContext("/test", "");
		when(rc.request().authority()).thenReturn(mock(HostAndPort.class));

		handler.handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();

		verify(rc.response()).end(content);
		// The request should be directly handled by the caching handler
		verify(rc, never()).next();
	}

	@Test
	public void testNoCacheFlag() throws Exception {
		CachingHandler handler = new CachingHandler(vertx, config, cacheProvider, metricsServiceMock);
		RoutingContext rc = mockRoutingContext("/test", "");

		if (authenticated) {
			when(rc.user()).thenReturn(userMock);
		}

		when(rc.get(PortalRoutingContext.DISABLE_CACHE_KEY)).thenReturn(true);
		handler.handle(rc);

		verify(rc).next();
		verify(rc).get(PortalRoutingContext.DISABLE_CACHE_KEY);
		verifyNoMoreInteractions(rc);
	}

	@Test
	public void testDisableHandling() throws Exception {
		CachingHandler handler = new CachingHandler(vertx, config, cacheProvider, metricsServiceMock);
		handler.disable();
		RoutingContext rc = mockRoutingContext("/test", "");

		if (authenticated) {
			when(rc.user()).thenReturn(userMock);
		}

		handler.handle(rc);

		verify(rc).next();
		verifyNoMoreInteractions(rc);
	}

}
