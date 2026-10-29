package com.gentics.mesh.portal.content.preview;

import static com.gentics.mesh.portal.content.ContentPathHandler.RenderMode;
import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gentics.mesh.portal.api.queries.QueryStore;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.server.DummyPortalInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.gentics.mesh.core.rest.graphql.GraphQLRequest;
import com.gentics.mesh.core.rest.graphql.GraphQLResponse;
import com.gentics.mesh.portal.AbstractPortalTest;
import com.gentics.mesh.portal.TestRoutingContext;
import com.gentics.mesh.portal.api.Constants;
import com.gentics.mesh.portal.api.content.LanguageSource;
import com.gentics.mesh.portal.cache.DummyCacheProvider;
import com.gentics.mesh.portal.client.MeshRestClientProvider;
import com.gentics.mesh.portal.content.ContentPathHandler;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.google.common.net.HttpHeaders;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.JsonObject;

public class ContentPathHandlerTest extends AbstractPortalTest {

	private static final String STARTPAGE_RAW = "/startpage.html";
	private static final String STARTPAGE_GERMAN = "/startseite.de.html";
	private static final String STARTPAGE_ENGLISH = "/startpage.en.html";

	private MeshRestClientProvider meshRestClientProvider;
	private QueryStore queryStore;
	private TestRoutingContext rc;

	@Before
	public void setup() throws Exception {
		super.setup();

		meshRestClientProvider = Mockito.mock(MeshRestClientProvider.class);
		when(meshRestClientProvider.get(any())).thenReturn(clientMock);

		queryStore = Mockito.mock(QueryStore.class);

		rc = mockRoutingContext("", "");
	}

	@Test
	public void testUppercaseExtension() throws Exception {
		when(queryStore.getByPathQuery()).thenReturn("Mocked loadByPathQuery");
		mockGraphQL(new JsonObject());

		mockWebroot("test.PDF", true, "test.PDF", "application/pdf", HttpResponseStatus.OK.code());

		// Mock routing context containing the node to preview
		when(rc.pathParam("param0")).thenReturn("test.PDF");

		ContentPathHandler handler = new ContentPathHandler(
			config,
			meshRestClientProvider,
			queryStore,
			new DummyCacheProvider(),
			null,
			new DummyPortalInfo(),
			metricsServiceMock);

		handler.handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();

		// handling must not fail
		verify(rc, never()).fail(any(Throwable.class));
		verify(rc, never()).fail(Mockito.anyInt());
		verify(rc.response(), times(1)).putHeader(HttpHeaders.CONTENT_TYPE, "application/pdf");
		verify(rc.response(), times(1)).end();
		verify(rc, never()).put(eq(PortalRoutingContext.PREVIEW_KEY), any());
	}

	@Test
	public void testStartpageDefaultLanguage() throws Exception {
		makeStartpageRequest();

		JsonObject loadedPage = rc.get(Constants.RC_NODE_TO_RENDER);

		assertThat(loadedPage).as("Loaded startpage").isEqualTo(jsonObject("/startpage.en.json").getJsonObject("node"));
	}

	@Test
	public void testStartpageGerman() throws Exception {
		wrap(rc).setLanguage("de", LanguageSource.DEFAULT);
		makeStartpageRequest();

		JsonObject loadedPage = rc.get(Constants.RC_NODE_TO_RENDER);

		assertThat(loadedPage).as("Loaded startpage").isEqualTo(jsonObject("/startseite.de.json").getJsonObject("node"));
	}

	@Test
	public void testStartpageUnavailableLanguage() throws Exception {
		wrap(rc).setLanguage("fr", LanguageSource.DEFAULT);
		makeStartpageRequest();

		JsonObject loadedPage = rc.get(Constants.RC_NODE_TO_RENDER);

		assertThat(loadedPage).as("Loaded startpage").isEqualTo(jsonObject("/startpage.default.json").getJsonObject("node"));
	}

	@Test
	public void testOmitChildren() {
		makePageRequest();

		ArgumentCaptor<GraphQLRequest> graphQLRequestArgumentCaptor = ArgumentCaptor.forClass(GraphQLRequest.class);
		verify(clientMock).graphql(eq(config.getMeshProject()), graphQLRequestArgumentCaptor.capture(), any());
		assertThat(graphQLRequestArgumentCaptor.getValue().getVariables().getBoolean("withChildren")).isFalse();
	}

	@Test
	public void testIncludeChildren() {
		config.setFolderTemplateName("someTemplate");
		makePageRequest();

		ArgumentCaptor<GraphQLRequest> graphQLRequestArgumentCaptor = ArgumentCaptor.forClass(GraphQLRequest.class);
		verify(clientMock).graphql(eq(config.getMeshProject()), graphQLRequestArgumentCaptor.capture(), any());
		assertThat(graphQLRequestArgumentCaptor.getValue().getVariables().getBoolean("withChildren")).isTrue();
	}

	@Test
	public void testPrepareRender() {
		ContentPathHandler handler = new ContentPathHandler(
			config,
			meshRestClientProvider,
			queryStore,
			new DummyCacheProvider(),
			null,
			new DummyPortalInfo(),
			metricsServiceMock);

		String templateName = "fake-template-name";
		JsonObject fakePage = new JsonObject()
			.put("fields", new JsonObject()
				.put("templateName", templateName));

		when(rc.get("data")).thenReturn(new JsonObject());
		when(rc.get(Constants.RC_NODE_TO_RENDER)).thenReturn(fakePage);
		when(rc.get(Constants.RC_RENDER_MODE)).thenReturn(RenderMode.Page);

		handler.prepareRender(rc);

		verify(rc.response(), times(1)).putHeader(eq(HttpHeaders.CONTENT_TYPE), eq("text/html"));
		verify(rc, times(1)).put(eq(Constants.RC_CONFIG), any(PortalConfig.class));
		verify(rc, times(1)).put(eq(Constants.RC_TEMPLATE_NAME), eq(templateName + ".hbs"));
		verify(rc, times(1)).put(eq("node"), any());
		verify(rc, times(1)).next();
	}

	private void makePageRequest() {
		when(queryStore.getByPathQuery()).thenReturn("Mocked loadByPathQuery");

		mockWebroot("test.html", false, null, null, HttpResponseStatus.OK.code());
		mockGraphQL(new JsonObject());

		// Mock routing context containing the node to preview
		when(rc.pathParam("param0")).thenReturn("test.html");

		ContentPathHandler handler = new ContentPathHandler(
			config,
			meshRestClientProvider,
			queryStore,
			new DummyCacheProvider(),
			null,
			new DummyPortalInfo(),
			metricsServiceMock);

		handler.handle(rc);
	}

	private void makeStartpageRequest() throws Exception {
		String loadLanguagesQuery = "Mocked loadLanguages query";
		String loadByPathQuery = "Mocked loadByPath query";

		config.setStartPage(STARTPAGE_RAW);

		when(queryStore.getLanguagesQuery()).thenReturn(loadLanguagesQuery);
		when(queryStore.getByPathQuery()).thenReturn(loadByPathQuery);

		when(clientMock.graphql(anyString(), any(GraphQLRequest.class), any())).then(answer -> {
			GraphQLRequest request = answer.getArgument(1, GraphQLRequest.class);
			String path = request.getVariables().getString("path");
			JsonObject responseData;

			if (request.getQuery().equals(loadLanguagesQuery)) {
				responseData = jsonObject("/startPageLanguages.json");
			} else {
				switch (path) {
				case STARTPAGE_ENGLISH:
					responseData = jsonObject("/startpage.en.json");
					break;

				case STARTPAGE_GERMAN:
					responseData = jsonObject("/startseite.de.json");
					break;

				default:
				case STARTPAGE_RAW:
					responseData = jsonObject("/startpage.default.json");
				}
			}

			return mockRequest(new GraphQLResponse().setData(responseData));
		});

		ContentPathHandler handler = new ContentPathHandler(
			config,
			meshRestClientProvider,
			queryStore,
			new DummyCacheProvider(),
			null,
			new DummyPortalInfo(),
			metricsServiceMock);

		handler.handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();
	}
}
