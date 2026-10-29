package com.gentics.mesh.portal.api.authentication;

import com.gentics.mesh.portal.api.handler.PortalHandler;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.auth.authorization.Authorization;
import io.vertx.ext.web.RoutingContext;

import java.util.Collection;

/**
 * Interface for authentication handlers.
 *
 * <p>
 *     Implementations must handlers must take care of {@link PortalUser}
 *     generation, ensuring that a request is authenticated and perform
 *     clean up during logout.
 * </p>
 *
 * <p>
 *     A concrete implementation of {@code AuthenticationHandler} will usually
 *     be coupled with a specific {@link PortalUser} implementation.
 * </p>
 *
 * <p>
 *     The actual routes are configured in the
 *     {@code AbstractAuthenticationHandler}.
 * </p>
 */
public interface AuthenticationHandler extends PortalHandler {

	/** The name of the authorization cookie. */
	String COOKIE_AUTH = "gpj.auth.token";

	/**
	 * Create an instance of this authentication handlers {@link PortalUser}
	 * implementation from the respective authentication cookies and pass it to
	 * the given {@code callback}.
	 *
	 * <p>
	 *     <strong>Important:</strong> this method must use the provided
	 *     routing context <em>read only</em>, so no actions that will interfere
	 *     with registered handlers (e.g. {@code rc.next()}, {@code rc.fail()},
	 *     {@code rc.reroute()}, etc.).
	 * </p>
	 *
	 * @param rc The current routing context
	 * @param callback The callback to pass the created user to
	 */
	void createUser(RoutingContext rc, Handler<AsyncResult<? extends PortalUser>> callback);

	/**
	 * A handler method that will make sure that afterwards a valid
	 * {@link PortalUser} is returned by {@link RoutingContext#user()}.
	 *
	 * <p>
	 *     When there is no user set at the time the handler is called, it must
	 *     initiate the authentication process.
	 * </p>
	 *
	 * @param rc The current routing context
	 */
	void handleAuthentication(RoutingContext rc);

	/**
	 * Create an appropriate instance of {@link Authorization} for this
	 * {@code AuthenticationHandler} implementation.
	 *
	 * @param name The name of the authorization.
	 * @return An {@code Authorization} instance created from the given string
	 */
	Authorization createAuthorization(String name);

	/**
	 * Perform authorization handling.
	 *
	 * <p>
	 *     An {@code AuthenticationHandler} implementation must check whether
	 *     the current request has the required authorization and either call
	 *     {@link RoutingContext#next()} or {@link RoutingContext#fail(int)}
	 *     (or any other overload that finishes the request).
	 * </p>
	 *
	 * @param rc The current routing context
	 * @param required The required authorization
	 */
	void handleAuthorizationInternal(RoutingContext rc, Authorization required);

	/**
	 * Perform any necessary logout actions.
	 *
	 * <p>
	 *     <strong>Note</strong> that the authentication cookies will be already
	 *     deleted when this method is called.
	 * </p>
	 *
	 * <p>
	 *     When this method ends the current request itself (by calling
	 *     {@code rc.fail()} or {@code rc.end()}, it must
	 *     return a result with the value {@code true} to inform the basic
	 *     authentication handler.
	 * </p>
	 *
	 * <p>
	 *     When the implementation uses the routing context "read only", it
	 *     must return a result with the value {@code false}.
	 * </p>
	 *
	 * <p>
	 *     <strong>Important:</strong> An implementation of this method
	 *     <em>must not</em> call {@code rc.next()} or {@code rc.reroute()}.
	 * </p>
	 *
	 * @param rc The current routing context.
	 * @param callback The callback to call after the logout actions are done.
	 * 		The value in the result will be {@code true} when the logout request
	 * 		has already been ended (normally by answering with a redirect),
	 * 		and {@code false} if the portal should continue the logout process
	 * 		itself.
	 */
	void logout(RoutingContext rc, Handler<AsyncResult<Boolean>> callback);
}
