package com.gentics.mesh.portal.content;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;

import com.gentics.mesh.core.rest.branch.BranchResponse;
import com.gentics.mesh.portal.AbstractPortalTest;
import com.gentics.mesh.portal.TestRoutingContext;
import com.gentics.mesh.portal.api.Constants;
import com.gentics.mesh.portal.api.content.LanguageSource;
import com.gentics.mesh.portal.api.queries.QueryStore;
import com.gentics.mesh.portal.cache.DummyCacheProvider;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.gentics.mesh.portal.server.DummyPortalInfo;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Test status redirects for failed requests.
 *
 * <p>
 *     TODO: Add tests for custom redirects
 * </p>
 */
public class RedirectHandlerTest extends AbstractPortalTest {

	private static final int STATUS_CODE = HttpResponseStatus.INTERNAL_SERVER_ERROR.code();
	private static final String REQUEST_URI = "/failing-request-uri";
	private static final String REDIRECT_RAW = "/error.500.html";
	private static final String REDIRECT_GERMAN = "/error.500.de.html";
	private static final String REDIRECT_ENGLISH = "/error.500.en.html";
	private static final String REDIRECT_CHINESE = "/error.500.zh.html";
	private static final String REDIRECT_JAPANESE = "/error.500.ja.html";

	private QueryStore queryStore;
	private TestRoutingContext rc;

	@SuppressWarnings("unchecked")
	@Before
	public void setup() throws Exception {
		super.setup();

		queryStore = mock(QueryStore.class);
		when(queryStore.getLanguagesQuery()).thenReturn("Mocked loadLanguages query");

		rc = mockRoutingContext(REQUEST_URI, "");
		when(rc.statusCode()).thenReturn(STATUS_CODE);

		mockGraphQL(jsonObject("/statusPageLanguages.json"), Pair.of("Other branch", jsonObject("/statusPageLanguagesAltBranch.json")));
	}

	/**
	 * Test empty response with appropriate status code when no redirects are configured.
	 * @throws InterruptedException 
	 */
	@Test
	public void testEmptyStatusResponse() throws InterruptedException {
		new RedirectHandler(config, clientMock, new DummyCacheProvider(), queryStore, new DummyPortalInfo()).handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();

		verify(rc, never()).reroute(anyString());
		verify(rc.response()).setStatusCode(STATUS_CODE);
		verify(rc.response()).end();
	}

	/**
	 * Test rerouting to the status page for the default language.
	 * @throws InterruptedException 
	 */
	@Test
	public void testStatusRedirectDefaultLanguage() throws InterruptedException {
		config.getRedirectOptions().getStatusPages().put(String.valueOf(STATUS_CODE), REDIRECT_RAW);

		new RedirectHandler(config, clientMock, new DummyCacheProvider(), queryStore, new DummyPortalInfo()).handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();

		verify(rc.response(), never()).setStatusCode(anyInt());
		verify(rc.response(), never()).end();
		verify(rc).put(Constants.RC_REROUTE_STATUS, STATUS_CODE);
		verify(rc).reroute(REDIRECT_ENGLISH);
	}

	/**
	 * Test rerouting to the status page for the non-default branch.
	 * @throws InterruptedException 
	 */
	@Test
	public void testStatusRedirectOtherBranch() throws InterruptedException {
		BranchResponse branch = new BranchResponse();
		branch.setUuid("1234567890ABCDEF");
		branch.setName("Other branch");

		config.getRedirectOptions().getStatusPages().put(String.valueOf(STATUS_CODE), REDIRECT_RAW);
		wrap(rc).setBranch(branch);

		new RedirectHandler(config, clientMock, new DummyCacheProvider(), queryStore, new DummyPortalInfo()).handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();

		verify(rc.response(), never()).setStatusCode(anyInt());
		verify(rc.response(), never()).end();
		verify(rc).put(Constants.RC_REROUTE_STATUS, STATUS_CODE);
		BranchResponse branchResponse = new BranchResponse();
		branchResponse.setUuid("1234567890ABCDEF");
		verify(rc).put(PortalRoutingContext.BRANCH_KEY, branchResponse);
		verify(rc).reroute(REDIRECT_JAPANESE);
	}

	/**
	 * Test rerouting to the german status page.
	 * @throws InterruptedException 
	 */
	@Test
	public void testStatusRedirectGerman() throws InterruptedException {
		config.getRedirectOptions().getStatusPages().put(String.valueOf(STATUS_CODE), REDIRECT_RAW);
		wrap(rc).setLanguage("de", LanguageSource.DEFAULT);

		new RedirectHandler(config, clientMock, new DummyCacheProvider(), queryStore, new DummyPortalInfo()).handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();

		verify(rc.response(), never()).setStatusCode(anyInt());
		verify(rc.response(), never()).end();
		verify(rc).put(Constants.RC_REROUTE_STATUS, STATUS_CODE);
		verify(rc).reroute(REDIRECT_GERMAN);
	}

	/**
	 * Test rerouting to the configured status page path for unavailable languages.
	 * @throws InterruptedException 
	 */
	@Test
	public void testStatusRedirectUnavailableLanguage() throws InterruptedException {
		config.getRedirectOptions().getStatusPages().put(String.valueOf(STATUS_CODE), REDIRECT_RAW);
		wrap(rc).setLanguage("fr", LanguageSource.DEFAULT);

		new RedirectHandler(config, clientMock, new DummyCacheProvider(), queryStore, new DummyPortalInfo()).handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();

		verify(rc.response(), never()).setStatusCode(anyInt());
		verify(rc.response(), never()).end();
		verify(rc).put(Constants.RC_REROUTE_STATUS, STATUS_CODE);
		// The loadLangauges query will not return a path for french, so the redirect has to be the path in the configuration.
		verify(rc).reroute(REDIRECT_RAW);
	}

	/**
	 * Test empty response with appropriate status code, when the redirect path is the current request URI.
	 * @throws InterruptedException 
	 */
	@Test
	public void testStatusRedirectLoopPrevention() throws InterruptedException {
		config.getRedirectOptions().getStatusPages().put(String.valueOf(STATUS_CODE), REDIRECT_RAW);

		rc = mockRoutingContext(REDIRECT_ENGLISH, "");
		when(rc.statusCode()).thenReturn(STATUS_CODE);

		new RedirectHandler(config, clientMock, new DummyCacheProvider(), queryStore, new DummyPortalInfo()).handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();

		verify(rc, never()).reroute(anyString());
		verify(rc.response()).setStatusCode(STATUS_CODE);
		verify(rc.response()).end();
	}
}
