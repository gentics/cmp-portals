package com.gentics.mesh.portal.content.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mockito;

import com.gentics.mesh.core.rest.graphql.GraphQLRequest;
import com.gentics.mesh.core.rest.graphql.GraphQLResponse;
import com.gentics.mesh.portal.AbstractPortalTest;
import com.gentics.mesh.portal.TestRoutingContext;
import com.gentics.mesh.portal.api.PortalFlags;
import com.gentics.mesh.portal.api.content.PreviewMode;
import com.gentics.mesh.portal.api.queries.QueryStore;
import com.gentics.mesh.portal.cache.DummyCacheProvider;
import com.gentics.mesh.portal.client.MeshRestClientProvider;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.gentics.mesh.portal.server.DummyPortalInfo;
import com.gentics.mesh.rest.client.MeshRequest;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RequestBody;

/**
 * Test cases for content preview
 */
@RunWith(Parameterized.class)
public class ContentPreviewHandlerTest extends AbstractPortalTest {

	@Parameters(name = "{index}: query param: {0}, compatibiliy: {1}, expected preview mode: {2}, enable mapper: {3}")
	public static List<Object[]> data() {
		return Arrays.asList(
			new Object[] { "", false, PreviewMode.UNKNOWN, true },
			new Object[] { "", true, PreviewMode.PREVIEW, false },
			new Object[] { "invalid", false, PreviewMode.UNKNOWN, true },
			new Object[] { "invalid", true, PreviewMode.PREVIEW, false },
			new Object[] { "preview", false, PreviewMode.PREVIEW, true },
			new Object[] { "edit", false, PreviewMode.EDIT, false },
			// The parameter must be case insensitive.
			new Object[] { "Preview", false, PreviewMode.PREVIEW, true });
	}

	@Parameter(0)
	public String queryParam;

	@Parameter(1)
	public Boolean compatibilityMode;

	@Parameter(2)
	public PreviewMode expectedMode;

	@Parameter(3)
	public boolean mapNodeResponse;

	/**
	 * Check whether posted JSON is in the routing context even if the folder is not found,
	 * and the <code>PreviewMode</code> is set correctly.
	 * @throws Exception When the JSON resources cannot be loaded
	 */
	@Test
	public void testPreview() throws Exception {
		config.getCompatibilityOptions()
			.setPreview(compatibilityMode)
			.setMapNodeResponse(mapNodeResponse);

		String mockedByPathQuery = "Mocked loadByPathQuery";

		MeshRestClientProvider meshRestClientProviderMock = Mockito.mock(MeshRestClientProvider.class);
		when(meshRestClientProviderMock.get(Mockito.any())).thenReturn(clientMock);
		when(meshRestClientProviderMock.get()).thenReturn(clientMock);

		QueryStore queryStore = Mockito.mock(QueryStore.class);
		when(queryStore.getByPathQuery()).thenReturn(mockedByPathQuery);		// mock the graphql response (containing "folder": null)
		GraphQLResponse response = new GraphQLResponse();
		response.setData(jsonObject("/graphqlResponseFolderNull.json"));
		MeshRequest<GraphQLResponse> requestMock = mockRequest(response);
		when(clientMock.graphql(Mockito.anyString(), Mockito.any(GraphQLRequest.class), Mockito.any())).thenReturn(requestMock);

		// mock routing context containing the node to preview
		TestRoutingContext rc = mockRoutingContext("", "?renderMode=" + queryParam, 2);
		JsonObject previewJson = jsonObject("/previewNode.json");
		RequestBody mockBody = mock(RequestBody.class);

		when(mockBody.asJsonObject()).thenReturn(previewJson);
		when(rc.body()).thenReturn(mockBody);

		// call the preview handler
		ContentPreviewHandler handler = new ContentPreviewHandler(
			config,
			meshRestClientProviderMock,
			queryStore,
			new DummyCacheProvider(),
			null,
			new DummyPortalInfo(),
			metricsServiceMock,
			new PreviewDataMapper(config));

		handler.setPreviewMode(rc);
		handler.handle(rc);

		// ContentPreviewHandler.processPageData() calls super.processPageData() (which itself
		// calls next()) and then prepareRender() again, both asynchronously on the Vert.x
		// event-loop thread once the mocked GraphQL response is delivered. That is more next()
		// calls than a fixed-count CountDownLatch can reliably wait for, so instead wait for the
		// actual last mutation performed by prepareRender() to avoid racing its remaining work.
		verify(rc, timeout(2000)).put("node", previewJson);

		// handling must not fail
		verify(rc, never()).fail(Mockito.any(Throwable.class));
		verify(rc, never()).fail(Mockito.anyInt());

		verify(rc).put(PortalRoutingContext.PREVIEW_KEY, expectedMode);

		JsonObject nodeToRender = rc.get(PortalFlags.RC_NODE_TO_RENDER);
		String expectedField = mapNodeResponse ? "parent" : "parentNode";

		assertThat(nodeToRender.containsKey(expectedField))
			.as("Node to render contains correct parent field")
			.isTrue();
	}
}
