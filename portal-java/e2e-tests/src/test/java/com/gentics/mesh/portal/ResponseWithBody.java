package com.gentics.mesh.portal;

import io.vertx.reactivex.core.http.HttpClientResponse;

public class ResponseWithBody {
	private final HttpClientResponse response;
	private final String body;

	public ResponseWithBody(HttpClientResponse response, String body) {
		this.response = response;
		this.body = body;
	}

	public HttpClientResponse getResponse() {
		return response;
	}

	public String getBody() {
		return body;
	}
}
