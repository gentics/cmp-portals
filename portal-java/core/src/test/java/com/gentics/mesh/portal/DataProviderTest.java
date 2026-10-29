package com.gentics.mesh.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import io.vertx.core.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;

import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.http.HttpConstants;
import com.gentics.mesh.portal.api.config.InvalidConfigurationException;
import com.gentics.mesh.portal.config.option.DataProviderOptions;
import com.gentics.mesh.portal.config.option.WebDataOptions;
import com.gentics.mesh.portal.config.option.WebDataOptions.WebDataFormat;
import com.gentics.mesh.rest.client.MeshRequest;
import com.gentics.mesh.rest.client.MeshRestClient;
import com.gentics.mesh.rest.client.MeshWebrootResponse;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.reactivex.core.http.HttpClient;

/**
 * Tests for the DataProviderVerticle.
 */
public class DataProviderTest extends AbstractPortalTest {

	/**
	 * Enum for differend kinds web datasource tests.
	 *
	 * @see DataProviderTest#doWebRefresh(HttpClient, int, WebRequest, String, Boolean)
	 */
	private enum WebRequest {
		JSON,
		RAW,
		MISSING_TYPE
	}

	/** JsonPath configuration which suppresses exceptione. */
	private static final Configuration jpConf = Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);

	/** The data provider verticle used in the tests. */
	private DataProviderVerticle dataProvider;
	/** The data provider configuration used in the tests. */
	private DataProviderOptions options;
	/** Counter for how often {@link DataProviderVerticle#refreshNow()} was called. Used in Mesh data tests.*/
	private int meshRefreshCount;
	/** Counter for how often {@link DataProviderVerticle#refreshNow()} was called. Used in web data tests. */
	private int webRefreshCount;

	/**
	 * Resets the data provider itself as well as the options and the refresh counters.
	 */
	@Before
	public void setupDataProvider() {
		options = new DataProviderOptions();
		dataProvider = new DataProviderVerticle(branchProviderMock, clientMock, config);
		dataProvider.init(vertx, null);
		meshRefreshCount = webRefreshCount = 0;
	}

	/**
	 * Makes sure data will be put in the routing context under the given key.
	 *
	 * <p>
	 * {@link DataProviderVerticle#addData(RoutingContext) addData()} is called with a mocked routing context.
	 * Then it checks whether there is a {@link JsonObject} in the routing context under <code>key</code>, which
	 * has the <code>dpvmeta.fresh</code> field set to <code>expected</code>.
	 * </p>
	 *
	 * <p>
	 * This method will result in a failed assertion, when the loaded object is not a <code>JsonObject</code> or
	 * the boolean values do not match.
	 * </p>
	 *
	 * @param key The key to look for in the routing context
	 * @param expected The expected value for <code>dpvmeta.fresh</code>
	 * @return The <code>JsonObject</code> from the routing context
	 */
	private JsonObject assertFresh(String key, Boolean expected) {
		TestRoutingContext rc = mockRoutingContext("/", "");

		dataProvider.addData(rc);

		try {
			assertThat(rc.await()).as("rc was ended").isTrue();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		Object data = rc.get(key);

		if (data instanceof JsonObject) {
			Object fresh = JsonPath.compile("dpvmeta.fresh").read(((JsonObject) data).encode(), jpConf);

			assertEquals("Wrong fresh marker for data", expected, fresh);

			return (JsonObject) data;
		}

		fail("Data was not found in routing context");
		return null;
	}

	/**
	 * Creates a MeshWebrootResponse that will contain the given noderesponse.
	 * @param node The node to be returned when calling {@link MeshWebrootResponse#getNodeResponse()}
	 * @return The mocked response
	 */
	private MeshWebrootResponse mockNodeWebrootResponse(NodeResponse node) {
		MeshWebrootResponse response = mock(MeshWebrootResponse.class);
		when(response.getNodeResponse()).thenReturn(node);
		return response;
	}

	/**
	 * Causes a refresh and makes sure the given mocked request is processed.
	 *
	 * <p>
	 * The requests can be created with {@link #mockRequest(Object)} for successful requests and with
	 * {@link #mockRequest(Throwable)} for failed requests.
	 * </p>
	 *
	 * <p>
	 * The method asserts that {@link MeshRestClient#webroot(String, String, com.gentics.mesh.parameter.ParameterProvider...) MeshRestClient.webroot()}
	 * is called the correct number of times, and that the fresh marker matches <code>expected</code>.
	 * </p>
	 *
	 * @param request The request which will be returned by the mocked <code>MeshRestClient</code>.
	 * @param expected The excpected value for <code>dpvmeta.fresh</code>
	 * @return The JsonObject put in the routing context after the refresh
	 */
	private JsonObject doMeshRefresh(MeshRequest<MeshWebrootResponse> request, Boolean expected) throws InvalidConfigurationException {
		String key = "key";
		String path = "/datasource";

		if (meshRefreshCount++ == 0) {
			options.getMeshData().put(key, Arrays.asList(path));
			dataProvider.init(options);
		}

		when(clientMock.webroot(eq(config.getMeshProject()), eq(path), any(), any())).thenReturn(request);

		dataProvider.refreshNow();

		verify(clientMock, times((options.isRetryOnError() ? meshRefreshCount : 1) * branchNames.size()))
			.webroot(eq(config.getMeshProject()), eq(path), any(), any());

		return assertFresh(key, expected);
	}

	/**
	 * Causes a refresh and fakes an HTTP answer with the given <code>status</code> and <code>body</code>.
	 *
	 * <p>
	 * The method asserts that {@link HttpClient#getAbs(String, io.vertx.core.Handler) HttpClient.getAbs()}
	 * is called the correct number of times, and that the fresh marker matches <code>expected</code>.
	 * </p>
	 *
	 * <p>
	 * The <code>requestType</code> determines the used {@link WebDataOptions#getFormat() web data format} and
	 * the <code>Content-Type</code> of the request:
	 * <ul>
	 *   <li><code>JSON</code>: used <code>WebDataFormat</code> is <code>JSON</code>, <code>Content-Type</code> is
	 *     <code>application/json</code></li>
	 *   <li><code>RAW</code>: used <code>WebDataFormat</code> is <code>RAW</code>, <code>Content-Type</code> is
	 *     <code>text/plain</code></li>
	 *   <li><code>MISSING_TYPE</code>: used <code>WebDataFormat</code> is <code>RAW</code>, <code>Content-Type</code> is
	 *     <code>null</code></li>
	 * </ul>
	 * </p>
	 *
	 * @param client The mocked HTTP client to use
	 * @param status The HTTP status of the response
	 * @param requestType The desired request type
	 * @param body The response body
	 * @param expected The excpected value for <code>dpvmeta.fresh</code>
	 * @return The JsonObject put in the routing context after the refresh
	 */
	private JsonObject doWebRefresh(HttpClient client, int status, WebRequest requestType, String body, Boolean expected) throws InvalidConfigurationException {
		String key = "key";
		String url = "http://localhost/datasource";

		if (webRefreshCount++ == 0) {
			WebDataOptions webDatasource = new WebDataOptions();

			webDatasource.setUrl(url);

			if (requestType == WebRequest.JSON) {
				webDatasource.setFormat(WebDataFormat.JSON);
			}

			options.getWebData().put(key, webDatasource);
			dataProvider.init(options);
		}

		String contentType;

		switch (requestType) {
		case JSON:
			contentType = HttpConstants.APPLICATION_JSON;
			break;

		case MISSING_TYPE:
			contentType = null;
			break;

		default:
			contentType = "text/plain";
			break;
		}

		mockRequest(client, url, status, contentType, body);

		dataProvider.setHttpClient(client);
		dataProvider.refreshNow();

		verify(client, times(options.isRetryOnError() ? webRefreshCount : 1)).rxRequest(eq(HttpMethod.GET), eq(url));

		return assertFresh(key, expected);
	}

	@Test
	public void testWebPostProcessors() throws Exception {
		String rawUrl = "http://localhost/test/raw";
		String rawKey = "raw";
		String rawOriginal = "TEST";
		String jsonUrl = "http://localhost/test/json";
		String jsonKey = "json";
		WebDataOptions webDatasource = new WebDataOptions();

		webDatasource.setUrl(rawUrl);
		webDatasource.setFormat(WebDataFormat.RAW);
		webDatasource.setPostProcessor(AppendTestStringPostProcessor.class.getName());
		options.getWebData().put(rawKey, webDatasource);

		webDatasource = new WebDataOptions();
		webDatasource.setUrl(jsonUrl);
		webDatasource.setFormat(WebDataFormat.JSON);
		webDatasource.setPostProcessor(AddTestFieldPostProcessor.class.getName());
		options.getWebData().put(jsonKey, webDatasource);

		dataProvider.init(options);

		HttpClient client = mock(HttpClient.class);

		mockRequest(client, rawUrl, HttpResponseStatus.OK.code(), "text/plain", rawOriginal);
		mockRequest(client, jsonUrl, HttpResponseStatus.OK.code(), HttpConstants.APPLICATION_JSON, "{}");

		dataProvider.setHttpClient(client);
		dataProvider.refreshNow();

		TestRoutingContext rc = mockRoutingContext("/", "");

		dataProvider.addData(rc);
		assertThat(rc.await()).as("rc was ended").isTrue();

		Object rawData = rc.get(rawKey);

		if (rawData instanceof JsonObject) {
			assertEquals(
				"Raw post processor returned wrong value",
				rawOriginal + AppendTestStringPostProcessor.ADDED_STRING,
				((JsonObject) rawData).getString(AppendTestStringPostProcessor.DATA_FIELD));
		} else {
			fail("Postprocessor for raw data did not return JsonObject");
		}

		Object jsonData = rc.get(jsonKey);

		if (jsonData instanceof JsonObject) {
			assertEquals(
				"JSON post processor returned wrong value",
				AddTestFieldPostProcessor.VALUE,
				((JsonObject) jsonData).getString(AddTestFieldPostProcessor.FIELD));
		} else {
			fail("Post processor for JSON data did not return JsonObject");
		}
	}

	/**
	 * Checks if the refresh fails on unexpected web content.
	 */
	@Test
	public void testWebRefreshFailureContent() throws InvalidConfigurationException {
		doWebRefresh(mock(HttpClient.class), HttpResponseStatus.OK.code(), WebRequest.JSON, "not-json", Boolean.FALSE);
	}

	/**
	 * Checks if the data provider stops trying to load a web datasource after it failed once with <code>retryOnError</code> set.
	 */
	@Test
	public void testWebRefreshFailureStatusWithoutRetry() throws InvalidConfigurationException {
		HttpClient client = mock(HttpClient.class);

		options.setRetryOnError(false);
		doWebRefresh(client, HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), WebRequest.RAW, "", Boolean.FALSE);
		doWebRefresh(client, HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), WebRequest.RAW, "", Boolean.FALSE);
	}

	/**
	 * Checks if the data provider still tries to load a web datasource after it failed once.
	 */
	@Test
	public void testWebRefreshFailureStatusWithRetry() throws InvalidConfigurationException {
		HttpClient client = mock(HttpClient.class);

		doWebRefresh(client, HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), WebRequest.RAW, "", Boolean.FALSE);
		doWebRefresh(client, HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), WebRequest.RAW, "", Boolean.FALSE);
	}

	/**
	 * Checks if the data provider correctly loads a raw web datasource with missing <code>Content-Type</code>.
	 */
	@Test
	public void testWebRefreshInvalidContentType() throws InvalidConfigurationException {
		String expected = "OK";
		JsonObject data = doWebRefresh(mock(HttpClient.class), HttpResponseStatus.OK.code(), WebRequest.MISSING_TYPE, expected, Boolean.TRUE);

		assertEquals("Loaded data does not match", expected, data.getString("data"));
	}

	/**
	 * Checks if the data provider correctly loads a raw web datasource.
	 */
	@Test
	public void testWebRefreshSuccessRaw() throws InvalidConfigurationException {
		String expected = "OK";
		JsonObject data = doWebRefresh(mock(HttpClient.class), HttpResponseStatus.OK.code(), WebRequest.RAW, expected, Boolean.TRUE);

		assertEquals("Loaded data does not match", expected, data.getString("data"));
	}

	/**
	 * Checks if the data provider correctly loads a JSON web datasource.
	 */
	@Test
	public void testWebRefreshSuccessJson() throws InvalidConfigurationException {
		JsonObject expected = new JsonObject().put("testField", "test data");
		JsonObject data = doWebRefresh(mock(HttpClient.class), HttpResponseStatus.OK.code(), WebRequest.JSON, expected.encode(), Boolean.TRUE);

		// The dpvmeta field is added by the DataProviderVerticle itself.
		data.remove("dpvmeta");

		assertEquals("Loaded data does not match", expected, data);
	}

	/**
	 * Checks whether the correct data is put the in the routing context after the request has failed once.
	 */
	@Test
	public void testMeshRefreshRecover() throws InvalidConfigurationException {
		Throwable error = new Exception("Intentional error");
		// First refresh should fail.
		JsonObject data = doMeshRefresh(mockRequest(error), Boolean.FALSE);
		NodeResponse node = new NodeResponse();
		String expectedDisplayName = "DisplayName";
		JsonPath displayNamePath = JsonPath.compile("displayName");
		String actualDisplayName = displayNamePath.read(data.encode(), jpConf);

		assertNull("displayName must be null", actualDisplayName);

		node.setDisplayName(expectedDisplayName);
		MeshWebrootResponse response = mockNodeWebrootResponse(node);
		// Second refresh should succeed and the correct data should be in the routing context.
		data = doMeshRefresh(mockRequest(response), Boolean.TRUE);
		actualDisplayName = displayNamePath.read(data.encode(), jpConf);

		assertEquals("Wrong displayName in routing context data", expectedDisplayName, actualDisplayName);
	}

	/**
	 * Checks if the data provider still tries to load a Mesh datasource after it failed once.
	 */
	@Test
	public void testMeshRefreshFailureWithRetry() throws InvalidConfigurationException {
		Throwable error = new Exception("Intentional error");
		MeshRequest<MeshWebrootResponse> request = mockRequest(error);

		doMeshRefresh(request, Boolean.FALSE);
		doMeshRefresh(request, Boolean.FALSE);
	}

	/**
	 * Checks if the data provider stops trying to load a Mesh datasource after it failed once with <code>retryOnError</code> set.
	 */
	@Test
	public void testMeshRefreshFailureWithoutRetry() throws InvalidConfigurationException {
		Throwable error = new Exception("Intentional error");
		MeshRequest<MeshWebrootResponse> request = mockRequest(error);

		options.setRetryOnError(false);

		doMeshRefresh(request, Boolean.FALSE);
		doMeshRefresh(request, Boolean.FALSE);
	}

	/**
	 * Checks if refresh fails on a failed Mesh request.
	 */
	@Test
	public void testMeshRefreshFailure() throws InvalidConfigurationException {
		Throwable error = new Exception("Intentional error");

		doMeshRefresh(mockRequest(error), Boolean.FALSE);
	}

	/**
	 * Checks if the data provider correctly loads a Mesh datasource.
	 */
	@Test
	public void testMeshRefreshSuccess() throws InvalidConfigurationException {
		MeshWebrootResponse response = mockNodeWebrootResponse(new NodeResponse());

		doMeshRefresh(mockRequest(response), Boolean.TRUE);
	}

	/**
	 * Checks if data provider initialisation fails when there are duplicate keys.
	 *
	 * <p>
	 * Note that this is only true, when {@link DataProviderOptions#isPreventStartupOnError() preventStartupOnError} is
	 * enabled. Otherwise it will just log a warning.
	 * </p>
	 */
	@Test
	public void testInvalidConfigDuplicateKey() {
		String key = "duplicateKey";

		options.setPreventStartupOnError(true);
		options.getMeshData().put(key, Arrays.asList("/not-relevant"));
		options.getWebData().put(key, new WebDataOptions());

		try {
			dataProvider.init(options);
			fail("Initialisation should fail because of duplicate keys");
		} catch (InvalidConfigurationException e) {
		}
	}

	/**
	 * Checks if data provider initialisation fails when an invalid post processor has beed defined.
	 */
	@Test
	public void testInvalidConfigPreProcessor() {
		WebDataOptions webDataOptions = new WebDataOptions();

		webDataOptions.setPostProcessor("non.existent.class");
		options.getWebData().put("dummyKey", webDataOptions);

		try {
			dataProvider.init(options);
			fail("Initialisation should fail because of invalid postprocessor class");
		} catch (InvalidConfigurationException e) {
		}
	}

	/**
	 * Tests that no requests are made to Mesh or with the HTTP client, when no datasources are defined.
	 */
	@Test
	public void testInitEmptyConfig() {
		try {
			dataProvider.init(options);
		} catch (InvalidConfigurationException e) {
			fail("Initialisation must succeed");
		}

		HttpClient httpClientMock = mock(HttpClient.class);

		dataProvider.setHttpClient(httpClientMock);
		dataProvider.refreshNow();
		verify(clientMock, never()).webroot(anyString(), anyString());
		verify(httpClientMock, never()).rxRequest(any(), anyString(), any());
	}
}
