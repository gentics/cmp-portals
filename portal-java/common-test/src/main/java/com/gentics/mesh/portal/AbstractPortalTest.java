package com.gentics.mesh.portal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import com.gentics.mesh.core.rest.branch.BranchResponse;
import com.gentics.mesh.core.rest.graphql.GraphQLRequest;
import com.gentics.mesh.core.rest.graphql.GraphQLResponse;
import com.gentics.mesh.core.rest.tag.TagReference;
import com.gentics.mesh.parameter.VersioningParameters;
import com.gentics.mesh.portal.api.branch.BranchProvider;
import com.gentics.mesh.portal.api.monitoring.MetricsService;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.monitoring.DummyTimerImpl;
import com.gentics.mesh.rest.client.MeshBinaryResponse;
import com.gentics.mesh.rest.client.MeshRequest;
import com.gentics.mesh.rest.client.MeshResponse;
import com.gentics.mesh.rest.client.MeshRestClient;
import com.gentics.mesh.rest.client.MeshWebrootResponse;
import com.gentics.mesh.rest.client.MeshWebsocket;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.Predicate;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.HostAndPort;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.core.http.HttpClientRequest;
import io.vertx.reactivex.core.http.HttpClientResponse;

/**
 * Basis for portal test.
 *
 * <p>
 * This base class provides a {@link Vertx} and a {@link PortalConfig} instance, as well as mocks for
 * {@link MeshRestClient} and {@link BranchProvider}.
 * </p>
 */
public abstract class AbstractPortalTest {

	protected final Vertx vertx = Vertx.vertx();
	protected final io.vertx.reactivex.core.Vertx rxVertx = new io.vertx.reactivex.core.Vertx(vertx);
	protected MeshRestClient clientMock;
	protected PortalConfig config;
	protected BranchProvider branchProviderMock;
	protected MetricsService metricsServiceMock;

	protected final List<String> branchNames = Arrays.asList("branch1", "branch2");

	@Before
	public void setup() throws Exception {
		config = new PortalConfig();
		config.setMeshProject("dummy");
		clientMock = mock(MeshRestClient.class);
		metricsServiceMock = mock(MetricsService.class);

		when(metricsServiceMock.isEnabled()).thenReturn(false);
		when(metricsServiceMock.wrapTimer(any())).thenReturn(new DummyTimerImpl());

		List<BranchResponse> branches = branchNames.stream()
			.map(name -> {
				BranchResponse branch = branch(name, name + "-host");

				branch.setLatest(true);

				return branch;
			})
			.collect(Collectors.toList());

		branchProviderMock = mockBranchProvider(branches.toArray(new BranchResponse[branches.size()]));

		// ServerRunner.setVertx(vertx);
		// ServerRunner.setQueryStore(new QueryStore());
		// options = new ServerOptions();
		// options.setCaching(true);
		// ServerRunner.setOptions(options);
		//
		// MeshRestClient clientMock = mock(MeshRestHttpClientImpl.class);
		// ServerRunner.setMeshClient(clientMock);
	}

	@After
	public void cleanup() {
		if (vertx != null) {
			vertx.close();
		}
	}

	protected TestRoutingContext mockRoutingContext(String uri, String query) throws RuntimeException {
		return mockRoutingContext(uri, query, 1);
	}

	protected TestRoutingContext mockRoutingContext(String uri, String query, int latches) throws RuntimeException {
		Map<String, Object> map = new HashMap<>();
		TestRoutingContext rc = mock(TestRoutingContext.class);
		Session session = mock(Session.class);
		HttpServerRequest request = mock(HttpServerRequest.class);

		when(request.query()).thenReturn(query);

		MultiMap params = createParameterMap(query);
		Route routeMock = mock(Route.class);

		when(routeMock.getPath()).thenReturn(uri);
		when(rc.currentRoute()).thenReturn(routeMock);
		when(rc.pathParam(eq("param0"))).thenReturn(uri);
		when(request.uri()).thenReturn(uri);
		when(request.absoluteURI()).thenReturn(uri);
		when(request.params()).thenReturn(params);
		when(request.getParam(anyString())).thenAnswer(in -> params.get((String) in.getArguments()[0]));
		when(rc.queryParams()).thenReturn(params);
		when(rc.vertx()).thenReturn(vertx);

		when(rc.data()).thenReturn(map);
		when(rc.put(anyString(), any())).thenAnswer(in -> {
			map.put(in.getArgument(0, String.class), in.getArgument(1, Object.class));
			return rc;
		});
		when(rc.get(anyString())).thenAnswer(in -> map.get(in.getArgument(0, String.class)));

		MultiMap headerMap = HttpHeaders.headers();

		headerMap.add("Accept-Language", "en, en-gb;q=0.8, en;q=0.72");
		when(request.headers()).thenReturn(headerMap);
		when(rc.request()).thenReturn(request);
		when(rc.session()).thenReturn(session);

		// Response
		RequestBody bodyMock = mock(io.vertx.ext.web.RequestBody.class);
		HttpServerResponse response = mock(HttpServerResponse.class);
		when(response.setStatusCode(anyInt())).thenReturn(response);
		when(response.putHeader(anyString(), anyString())).thenReturn(response);
		when(response.putHeader(any(CharSequence.class), any(CharSequence.class))).thenReturn(response);
		when(response.putHeader(any(AsciiString.class), any(AsciiString.class))).thenReturn(response);
		when(response.setStatusCode(anyInt())).thenReturn(response);
		when(response.setStatusMessage(anyString())).thenReturn(response);
		when(rc.response()).thenReturn(response);
		when(rc.body()).thenAnswer(in -> bodyMock);
		when(bodyMock.asString()).thenAnswer(in ->  map.getOrDefault("body", StringUtils.EMPTY).toString());

		final CountDownLatch latch = new CountDownLatch(latches);
		Answer<Void> endAnswer = inv -> {
			if (inv.getArguments().length > 0) {
				map.put("body", inv.getArguments()[0]);
			}
			latch.countDown();
			return null;
		};
		doAnswer(endAnswer).when(response).end(Mockito.any(io.vertx.core.buffer.Buffer.class));
		doAnswer(endAnswer).when(response).end(Mockito.anyString());
		doAnswer(endAnswer).when(response).end();

		doAnswer(in -> {
			latch.countDown();
			return null;
		}).when(rc).next();

		doAnswer(in -> {
			latch.countDown();
			return null;
		}).when(rc).fail(Mockito.anyInt());

		doAnswer(in -> {
			latch.countDown();
			return null;
		}).when(rc).fail(Mockito.any());

		doAnswer(in -> {
			latch.countDown();
			return null;
		}).when(rc).reroute(Mockito.anyString());

		try {
			doCallRealMethod().when(rc).await();
			doCallRealMethod().when(rc).await(anyInt());
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		when(rc.latch()).thenReturn(latch);

		return rc;
	}

	/**
	 * Create a parameter {@Link MultiMap} from the specified query string.
	 *
	 * @param query The query part of an URI
	 * @return A parameter map containing the elements in the specified query string
	 */
	private MultiMap createParameterMap(String query) {
		Collector<Entry<String, String>, MultiMap, MultiMap> multiMapCollector = Collector.of(
			MultiMap::caseInsensitiveMultiMap,
			(MultiMap map, Entry<String, String> item) -> map.add(item.getKey(), item.getValue()),
			(u, v) -> u.addAll(v));

		if (query.length() > 0 && query.charAt(0) == '?') {
			query = query.substring(1);
		}

		return Stream.of(query.split("&"))
			.map(p -> p.split("="))
			.map(p -> Pair.of(p[0], p.length > 1 ? p[1] : ""))
			.collect(multiMapCollector);
	}

	/**
	 * Let the routing context mock return the given host
	 * @param rc routing context mock
	 * @param host host
	 */
	protected void mockHost(RoutingContext rc, String host) {
		when(rc.request().authority()).thenAnswer(in -> mock(HostAndPort.class));
		when(rc.request().authority().host()).thenReturn(host);
		when(rc.request().getHeader(HttpHeaders.HOST)).thenReturn(host);
	}

	/**
	 * Mock a request that will return the given response.
	 * @param response The mocked response
	 * @return mocked request
	 */
	protected static <T> MeshRequest<T> mockRequest(T response) {
		return mockRequest(response, HttpResponseStatus.OK.code());
	}

	/**
	 * Mock a request that will return the given response and status
	 * @param response response
	 * @param status The response status code
	 * @return mocked request
	 */
	protected static <T> MeshRequest<T> mockRequest(T response, int status) {
		@SuppressWarnings("unchecked")
		MeshRequest<T> requestMock = mock(MeshRequest.class);
		Map<String, List<String>> requestHeaders = new HashMap<>();

		when(requestMock.toObservable()).thenReturn(Observable.just(response));
		when(requestMock.toSingle()).thenReturn(Single.just(response));
		when(requestMock.toCompletable()).thenReturn(Completable.fromSingle(Single.just(response)));

		@SuppressWarnings("unchecked")
		MeshResponse<T> meshResponse = mock(MeshResponse.class);

		when(meshResponse.getStatusCode()).thenReturn(status);
		when(meshResponse.getHeaders()).thenReturn(Collections.emptyMap());
		when(meshResponse.getHeader(anyString())).thenReturn(Optional.empty());
		when(meshResponse.getBody()).thenReturn(response);
		when(requestMock.getResponse()).thenReturn(Single.just(meshResponse));

		return requestMock;
	}

	/**
	 * Create a mocked request that will cause the specified error.
	 *
	 * <p>
	 * The request mocks calls to
	 * <ul>
	 *   <li><code>toSingle()</code></li>,
	 *   <li><code>toObservable()</code>, and</li>
	 *   <li><code>toCompletable()</code></li>
	 * </ul>
	 * </p>
	 *
	 * @param error The error to cause the request to fail
	 * @return
	 */
	protected <T> MeshRequest<T> mockRequest(Throwable error) {
		@SuppressWarnings("unchecked")
		MeshRequest<T> requestMock = mock(MeshRequest.class);
		when(requestMock.toSingle()).thenReturn(Single.error(error));
		when(requestMock.toObservable()).thenReturn(Observable.error(error));
		when(requestMock.toCompletable()).thenReturn(Completable.error(error));

		return requestMock;
	}

	protected MeshWebsocket mockEventbus() {
		MeshWebsocket eventbus = mock(MeshWebsocket.class);
		when(eventbus.events()).thenReturn(Observable.empty());
		when(eventbus.errors()).thenReturn(Observable.empty());
		when(eventbus.connections()).thenReturn(Observable.empty());
		return eventbus;
	}

	/**
	 * Create an absolute GET request returning the given information.
	 *
	 * <p>
	 * The created mock will return a response with the given parameters when a GET request with the given URL is made.
	 * </p>
	 *
	 * @param client The client to mock the  for
	 * @param url The URL for the mocked request
	 * @param status The desired HTTP status in the response
	 * @param contentType The desired <code>Content-Type</code> of the response
	 * @param body The body returned in the response
	 */
	@SuppressWarnings("unchecked")
	protected void mockRequest(HttpClient client, String url, int status, String contentType, String body) {
		HttpClientRequest request = mock(HttpClientRequest.class);
		HttpClientResponse response = mock(HttpClientResponse.class);

		when(client.rxRequest(eq(HttpMethod.GET), eq(url))).thenReturn(Single.just(request));
		when(request.rxSend()).thenReturn(Single.just(response));
		when(response.statusCode()).thenReturn(status);
		when(response.getHeader(HttpHeaders.CONTENT_TYPE.toString())).thenReturn(contentType);
		when(response.getHeader(HttpHeaders.CONTENT_LENGTH.toString())).thenReturn(Integer.toString(body.length()));
		when(response.rxBody()).thenReturn(Single.just(Buffer.buffer(body)));
	}

	/**
	 * Mock the GraphQL client calls.
	 *
	 * @param json
	 */
	@SuppressWarnings("unchecked")
	protected void mockGraphQL(JsonObject json, Pair<String, JsonObject> ...alternativeBranches) {
		var branches = Arrays.stream(alternativeBranches).collect(Collectors.toMap(Pair::getKey, Pair::getValue));

		when(clientMock.graphql(anyString(), any(GraphQLRequest.class), any()))
			.thenAnswer(invocation -> {
				VersioningParameters requestBranch = invocation.getArgument(2);
				var branchName = requestBranch.getBranch();
				var response = new GraphQLResponse();

				if (branchName != null && branches.containsKey(branchName)) {
					response.setData(branches.get(branchName));
				} else {
					response.setData(json);
				}

				return mockRequest(response);
			});
			// .thenReturn(requestMock);
	}

	/**
	 * Mock the Webroot client calls.
	 *
	 * @param path
	 */
	protected void mockWebroot(String path, boolean isDownload, String filename, String mimeType, int status) {
		MeshWebrootResponse response = mock(MeshWebrootResponse.class);

		when(response.isBinary()).thenReturn(isDownload);

		if (isDownload) {
			MeshBinaryResponse binaryResponse = mock(MeshBinaryResponse.class);

			when(binaryResponse.getFilename()).thenReturn(filename);
			when(binaryResponse.getContentType()).thenReturn(mimeType);
			when(binaryResponse.getFlowable()).thenReturn(Flowable.empty());
			when(response.getBinaryResponse()).thenReturn(binaryResponse);
		}

		MeshRequest<MeshWebrootResponse> requestMock = mockRequest(response, status);

		when(clientMock.webroot(anyString(), eq("/" + path), any())).thenReturn(requestMock);
	}

	/**
	 * Create a branch provider instance returning the given branches
	 * @param branches branches
	 * @return branch provider instance
	 */
	protected BranchProvider mockBranchProvider(BranchResponse...branches) {
		Observable<BranchResponse> branchList = Observable.fromArray(branches);
		BranchProvider branchProvider = mock(BranchProvider.class);

		when(branchProvider.findBranch(any())).then(invocation -> {
			@SuppressWarnings("unchecked")
			Predicate<? super BranchResponse> predicate = invocation.getArgument(0, Predicate.class);

			return branchList.filter(predicate).firstElement();
		});

		when(branchProvider.getBranches()).thenReturn(branchList);

		return branchProvider;
	}

	/**
	 * Read the json file into a JsonObject
	 * @param filename filename
	 * @return JsonObject
	 * @throws IOException
	 */
	protected JsonObject jsonObject(String filename) throws IOException {
		return new JsonObject(io.vertx.core.buffer.Buffer.buffer(IOUtils.resourceToString(filename, Charsets.toCharset("UTF-8"))));
	}

	/**
	 * Read the json file into a JsonArray
	 * @param filename filename
	 * @return JsonObject
	 * @throws IOException
	 */
	protected JsonArray jsonArray(String filename) throws IOException {
		return new JsonArray(io.vertx.core.buffer.Buffer.buffer(IOUtils.resourceToString(filename, Charsets.toCharset("UTF-8"))));
	}

	/**
	 * Create branch
	 * @param name name
	 * @param hostname hostname
	 * @param tags optional tags
	 * @return branch
	 */
	protected BranchResponse branch(String name, String hostname, TagReference...tags) {
		return branch(name, hostname, null, tags);
	}

	/**
	 * Create branch
	 * @param name name
	 * @param hostname hostname
	 * @param pathPrefix path prefix
	 * @param tags optional tags
	 * @return branch
	 */
	protected BranchResponse branch(String name, String hostname, String pathPrefix, TagReference...tags) {
		BranchResponse branch = new BranchResponse().setName(name);
		branch.setUuid(name);
		branch.setHostname(hostname);
		branch.setPathPrefix(pathPrefix);
		branch.setTags(Arrays.asList(tags));
		return branch;
	}

	/**
	 * Create tag reference
	 * @param tagFamily tag family
	 * @param tagName tag name
	 * @return tag reference
	 */
	protected TagReference tag(String tagFamily, String tagName) {
		return new TagReference().setTagFamily(tagFamily).setName(tagName);
	}
}
