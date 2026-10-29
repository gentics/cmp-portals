package com.gentics.mesh.portal.helper;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gentics.mesh.portal.config.PortalConfig;
import com.google.common.net.HttpHeaders;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.core.http.HttpClientRequest;
import io.vertx.reactivex.core.http.HttpClientResponse;

// TODO: Is this used anywhere? Remove if not.
public abstract class AbstractExternalDataHelper {

	protected final Logger log = LoggerFactory.getLogger(AbstractExternalDataHelper.class);

	protected final HttpClientOptions options;

	protected Vertx rxVertx;

	protected PortalConfig portalConfig;

	public AbstractExternalDataHelper(Vertx rxVertx, PortalConfig portalConfig) {
		this.rxVertx = rxVertx;
		this.portalConfig = portalConfig;
		options = new HttpClientOptions();

		if (portalConfig.getAuthOptions().isInsecureSsl()) {
			log.info("Allowing insecure SSL connections");
			options.setTrustAll(true).setVerifyHost(false);
		}
	}

	public static class ResponseInfo {
		public final String contentType;
		public final Buffer buffer;

		public ResponseInfo(String contentType, Buffer buffer) {
			this.contentType = contentType;
			this.buffer = buffer;
		}

		@Override
		public String toString() {
			return "{ResponseInfo contentType: " + contentType + ", buffer: " + buffer + "}";
		}
	}

	private HttpClient getHttpClient() {
		return rxVertx.createHttpClient(options);
	}

	/*
	 * Create a Single from URL and Headers arguments, then we can use "toBlocking().value()" to get the whole buffer, or subscribe normally.
	 */
	protected Single<ResponseInfo> get(String url, Map<String, String> headers) {
		return Single.create(sub -> {
			Single<HttpClientResponse> rs = getHttpClient().rxRequest(HttpMethod.GET, url)
				.doOnSuccess(rq -> {
					for (Map.Entry<String, String> header : headers.entrySet()) {
						rq.putHeader(header.getKey(), header.getValue());
					}
				})
				.flatMap(HttpClientRequest::rxSend);


			rs.toObservable()
				.flatMap(this::createResponseInfo)
				.singleOrError()
				.subscribe(sub::onSuccess, sub::onError);
		});
	}

	private Observable<ResponseInfo> createResponseInfo(HttpClientResponse response) {
		String contentType;
		String rawType = response.getHeader(HttpHeaders.CONTENT_TYPE);
		int idx = rawType.indexOf(';');

		if (idx < 0) {
			contentType = rawType;
		} else {
			contentType = rawType.substring(0, idx);
		}

		return response.toObservable().map(buffer -> new ResponseInfo(contentType, buffer));
	}
}
