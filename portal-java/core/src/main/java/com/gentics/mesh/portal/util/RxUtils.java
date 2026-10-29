package com.gentics.mesh.portal.util;

import com.gentics.mesh.rest.client.MeshRestClientMessageException;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.SingleTransformer;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.reactivex.RxHelper;
import io.vertx.reactivex.core.http.HttpClientRequest;
import io.vertx.reactivex.core.http.HttpClientResponse;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

/**
 * Utility methods for RxJava.
 */
public final class RxUtils {
	private RxUtils() {
	}

	/**
	 * Function that transforms the given <code>Observable</code> of {@link Entry} objects to a <code>Single</code>
	 * with a {@link Map}.
	 *
	 * @param upstream The <code>Observable</code> with the entries
	 * @return A <code>Single</code> with the resulting map
	 */
	public static <K, V> Single<Map<K, V>> map(Observable<? extends Entry<K, V>> upstream) {
		return upstream.toMap(Entry::getKey, Entry::getValue);
	}

	/**
	 * Function that transforms the given single to an observable.
	 *
	 * This ensures, that errors thrown in the success method will call the failure handler, which would not be the case when subscribing to a Single.
	 *
	 * @return A function which transforms a <code>Single</code> into an <code>Observable</code>
	 */
	public static <T> Function<Single<T>, Observable<T>> observable() {
		return Single::toObservable;
	}

	/**
	 * Make sure the specified request is actually performed when it is subscribed to.
	 *
	 * @deprecated In Vert.x 4 this helper is no longer necessary since
	 * 		{@link HttpClientRequest#rxSend()} already makes sure the request
	 * 		is sent.
	 * @param request The HTTP client request to send
	 * @return An <code>Single</code> containing the specified request
	 */
	@Deprecated
	public static Single<HttpClientResponse> sendRequest(HttpClientRequest request, boolean setTimeout) {
		Single<HttpClientResponse> rs = request.rxSend();

		if (setTimeout) {
			rs = rs.timeout(10, TimeUnit.SECONDS);
		}

		return rs;
	}

	/**
	 * Create a <code>SingleTransformer</code> that will perform the provided <code>Action</code> on
	 * success as well as on error.
	 *
	 * @param action The action to perform on success and on errors
	 * @return A transformer that will perform the action on success and on error
	 */
	public static <T> SingleTransformer<T, T> doOnDone(Action action) {
		return upstream -> upstream
			.doOnSuccess(ignored -> action.run())
			.doOnError(ignored -> action.run());
	}

	public static Function<Flowable<Buffer>, Single<String>> string() {
		return upstream -> upstream
			.reduce(Buffer::appendBuffer)
			.map(Buffer::toString)
			.toSingle("");
	}

	/**
	 * Wraps a value in a {@link Maybe}.
	 *
	 * @param item The item to create a {@code Maybe} from, or {@code null}
	 * @param <T> The item type
	 * @return {@link Maybe#empty()} if the value is null, {@link Maybe#just(Object)} otherwise.
	 */
	public static <T> Maybe<T> justNullable(T item) {
		return item == null
			? Maybe.empty()
			: Maybe.just(item);
	}

	/**
	 * Abort the current routing context with error code and message.
	 * The code/message are either taken from the error response, or set to HTTP 500 / localized exception message:
	 *
	 * @see #onErrorRethrowCode(RoutingContext, Logger, String)
	 * @param rc The current routing context
	 * @return An error handler which will log the error, and end the current
	 * 		request with a status code determined via the error.
	 */
	public static Consumer<? super Throwable> onErrorRethrowCode(RoutingContext rc, Logger log) {
		return onErrorRethrowCode(rc, log, null);
	}

	/**
	 * Abort the current routing context with error code and custom message.
	 * The code/message are either taken from the error response, or set to HTTP 500 / localized exception message:
	 *
	 * @param rc The
	 * @param customMessage user defined message to show on error, may be null
	 * @return An error handler which will log the error, and end the current
	 * 		request with a status code determined via the error.
	 */
	public static Consumer<? super Throwable> onErrorRethrowCode(RoutingContext rc, Logger log, String customMessage) {
		return error -> {
			int statusCode;
			String statusMessage;

			if (error instanceof MeshRestClientMessageException) {
				MeshRestClientMessageException restException = (MeshRestClientMessageException) error;
				statusCode = restException.getStatusCode();
				statusMessage = restException.getMessage();
			} else {
				statusCode = HttpResponseStatus.INTERNAL_SERVER_ERROR.code();
				statusMessage = error.getLocalizedMessage();
			}

			if (log != null) {
				if (customMessage != null) {
					log.error("Error during call {} ({}): HTTP {} / {}", rc.request().uri(), customMessage, statusCode, statusMessage);
				} else {
					log.error("Error during call {}: HTTP {} / {}", rc.request().uri(), statusCode, statusMessage);
				}
				log.trace("Raw exception: ", error);
			}

			rc.response()
				.setStatusCode(statusCode)
				.end();
		};
	}

	/**
	 * Get a scheduler that makes sure that code is executed on the same event loop as the request for
	 * the given {@code RoutingContext}.
	 *
	 * @param rc The routing context
	 * @return A scheduler that executes on the same event loop as the request for the given routing context.
	 */
	public static Scheduler scheduler(RoutingContext rc) {
		return RxHelper.scheduler(rc.vertx().getOrCreateContext());
	}
}
