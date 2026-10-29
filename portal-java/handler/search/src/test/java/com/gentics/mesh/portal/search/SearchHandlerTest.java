package com.gentics.mesh.portal.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.Charset;

import org.apache.commons.io.FileUtils;
import org.assertj.core.util.Files;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.gentics.mesh.portal.AbstractPortalTest;
import com.gentics.mesh.portal.TestRoutingContext;
import com.gentics.mesh.portal.client.MeshRestClientProvider;
import com.gentics.mesh.rest.client.MeshRequest;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.templ.handlebars.HandlebarsTemplateEngine;
import io.vertx.ext.web.templ.handlebars.impl.HandlebarsTemplateEngineImpl;

public class SearchHandlerTest extends AbstractPortalTest {

	private MeshRestClientProvider meshRestClientProvider;
	private HandlebarsTemplateEngine engine;
	private TestRoutingContext rc;

	@Before
	public void setup() throws Exception {
		super.setup();

		config.getSearchOptions().setEnabled(true);
		config.setTemplatesPath(Files.temporaryFolderPath());
		File template = new File(Files.temporaryFolderPath() + "/" + "searchresults.hbs");
		FileUtils.writeStringToFile(template, MOCK_TEMPLATE, Charset.defaultCharset());
		meshRestClientProvider = Mockito.mock(MeshRestClientProvider.class);
		when(meshRestClientProvider.get(any())).thenReturn(clientMock);

		engine = new HandlebarsTemplateEngineImpl(vertx, "hbs");

		rc = mockRoutingContext("/search", "q=seite");
	}

	@Test
	public void testPageAssembly() throws InterruptedException {
		MeshRequest<JsonObject> requestMock = mockRequest(new JsonObject(MOCK_RESULTS));
		when(clientMock.get(any())).thenReturn(requestMock);
		SearchHandler handler = new SearchHandler(config, engine, clientMock);

		handler.handle(rc);
		handler.renderResults(rc);
		handler.exposeSearchConfig(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();

		// handling must not fail
		verify(rc, never()).fail(any(Throwable.class));
		verify(rc, never()).fail(Mockito.anyInt());

		assertEquals(EXPECTED_MARKUP, rc.body().asString());
	}

	private static final String EXPECTED_MARKUP = "<h2>2 results</h2><ul><li><a href=\"/foo/bar.html\">Testseite: de</a></li><li><a href=\"/doo/tmp.html\">Eine weitere Seite: de</a></li></ul>";

	private static final String MOCK_TEMPLATE = "{{#if gpj_search_results.totalHits }}"
			+ "<h2>{{ gpj_search_results.totalHits }} result{{#if equals gpj_search_results.totalHits 1}}{{else}}s{{/if}}</h2>"
			+ "<ul>{{#each gpj_search_results.results}}"
			+ "<li><a href=\"{{ url }}\">{{ title }}: {{ language }}</a></li>"
			+ "{{/each}}</ul>"
			+ "{{else}}"
			+ "<h2>No results</h2>"
			+ "{{/if}}";

	private static final String MOCK_RESULTS = "{"
			+ "  \"results\": ["
			+ "    {"
			+ "      \"branch\": \"12345\","
			+ "      \"uuid\": \"1\","
			+ "      \"language\": \"de\","
			+ "      \"url\": \"/foo/bar.html\","
			+ "      \"title\": \"Testseite\","
			+ "      \"isBinary\": false,"
			+ "      \"excerpt\": \"Testseite\""
			+ "    },"
			+ "    {"
			+ "      \"branch\": \"12345\","
			+ "      \"uuid\": \"2\","
			+ "      \"language\": \"de\","
			+ "      \"url\": \"/doo/tmp.html\","
			+ "      \"title\": \"Eine weitere Seite\","
			+ "      \"isBinary\": false,"
			+ "      \"excerpt\": \"Eine weitere Seite\""
			+ "    }"
			+ "  ],"
			+ "  \"totalHits\": 2,"
			+ "  \"tags\": ["
			+ "    {"
			+ "      \"Verschiedenes\": 1"
			+ "    },"
			+ "    {"
			+ "      \"Tests\": 2"
			+ "    }"
			+ "  ],"
			+ "  \"hasMore\": false,"
			+ "  \"didYouMean\": \"Taste\""
			+ "}";
}
