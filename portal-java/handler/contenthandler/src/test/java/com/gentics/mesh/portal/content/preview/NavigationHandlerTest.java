package com.gentics.mesh.portal.content.preview;

import com.gentics.mesh.core.rest.user.UserResponse;
import com.gentics.mesh.portal.AbstractPortalTest;
import com.gentics.mesh.portal.TestRoutingContext;
import com.gentics.mesh.portal.api.cache.CacheProvider;
import com.gentics.mesh.portal.api.content.NavigationTransformer;
import com.gentics.mesh.portal.api.queries.QueryStore;
import com.gentics.mesh.portal.cache.CacheProviderImpl;
import com.gentics.mesh.portal.client.MeshRestClientProvider;
import com.gentics.mesh.portal.content.BasicNavigationTransformer;
import com.gentics.mesh.portal.content.MultipleRootNavigationTransformer;
import com.gentics.mesh.portal.content.NavigationHandler;
import com.gentics.mesh.rest.client.MeshRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;
import org.mockito.Mockito;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the {@link NavigationHandler}.
 */
public class NavigationHandlerTest extends AbstractPortalTest {

	private final static String FMT_INPUT = "/navigation/input/%s.json";
	private final static String FMT_EXPECTED = "/navigation/expected/%s.json";

	@Test
	public void testNavigation() throws Exception {
		JsonArray nav = doTest(true, new JsonObject(), new BasicNavigationTransformer(config));

		assertThat(nav)
			.as("Processed navigation")
			.isEmpty();
	}

	@Test
	public void testNavigationDisabled() throws Exception {
		JsonArray nav = doTest(false, new JsonObject(), new BasicNavigationTransformer(config));

		assertThat(nav)
			.as("Processed navigation")
			.isNull();
	}

	@Test
	public void testNavigationWithProjectPerNode() throws Exception {
		config.setMeshSchemaPrefix("fakePrefix");

		String testCase = "with-project-per-node";
		JsonArray nav = doTest(true, jsonObject(String.format(FMT_INPUT, testCase)), new BasicNavigationTransformer(config));

		assertThat(nav)
			.as("Processed Navigation")
			.isEqualTo(jsonArray(String.format(FMT_EXPECTED, testCase)));
	}

	@Test
	public void testNavigationWithoutProjectPerNode() throws Exception {
		String testCase = "without-project-per-node";
		JsonArray nav = doTest(true, jsonObject(String.format(FMT_INPUT, testCase)), new BasicNavigationTransformer(config));

		assertThat(nav)
			.as("Processed Navigation")
			.isEqualTo(jsonArray(String.format(FMT_EXPECTED, testCase)));	}

	@Test
	public void testNavigationMultipleRoot() throws Exception {
		String testCase = "multiple-root";
		JsonArray nav = doTest(true, jsonObject(String.format(FMT_INPUT, testCase)), new MultipleRootNavigationTransformer(config));

		assertThat(nav)
			.as("Processed Navigation")
			.isEqualTo(jsonArray(String.format(FMT_EXPECTED, testCase)));
	}

	private JsonArray doTest(boolean navigationEnabled, JsonObject navResponse, NavigationTransformer transformer) throws Exception {
		String mockedNavQuery = "Mocked loadNavigationQuery";
		MeshRestClientProvider meshRestClientProvider = Mockito.mock(MeshRestClientProvider.class);

		when(meshRestClientProvider.get(any())).thenReturn(clientMock);
		when(meshRestClientProvider.get()).thenReturn(clientMock);

		QueryStore queryStore = mock(QueryStore.class);

		when(queryStore.getNavigationQuery()).thenReturn(mockedNavQuery);

		CacheProvider cacheProvider = new CacheProviderImpl(config, meshRestClientProvider, mockEventbus(), rxVertx);
		NavigationHandler handler = new NavigationHandler(
			config,
			meshRestClientProvider,
			queryStore,
			cacheProvider,
			transformer,
			metricsServiceMock);
		TestRoutingContext rc = mockRoutingContext("/index.html", "", 2);
		MeshRequest<UserResponse> requestMock = mockRequest(new UserResponse().setRolesHash("fake-roles-hash"));

		when(clientMock.me()).thenReturn(requestMock);
		when(clientMock.me(any())).thenReturn(requestMock);

		if (navigationEnabled) {
			wrap(rc).enableNavigation();
		}

		mockGraphQL(navResponse);
		cacheProvider.handle(rc);
		handler.handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();

		// handling must not fail
		verify(rc, never()).fail(any(Throwable.class));
		verify(rc, never()).fail(anyInt());
		// Once for the caching handler, and once for the Navigation handler.
		verify(rc, times(2)).next();

		if (navigationEnabled) {
			verify(queryStore, only()).getNavigationQuery();
		}

		return rc.get("navigation");
	}
}
