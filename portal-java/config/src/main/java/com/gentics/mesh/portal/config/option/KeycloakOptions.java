package com.gentics.mesh.portal.config.option;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.Config;
import com.gentics.mesh.portal.api.config.EnvironmentVariable;

public class KeycloakOptions implements Config {

	private static final String REALM_URL_ENV = "AUTH_REALM_URL";
	private static final String CALLBACK_ENDPOINT_ENV = "AUTH_CALLBACK_ENDPOINT";
	private static final String CLIENT_ID_ENV = "AUTH_CLIENT_ID";
	private static final String RP_INITIATED_LOGOUT_EVN = "AUTH_RP_INITIATED_LOGOUT";
	private static final String CLIENT_SECRET_ENV = "AUTH_CLIENT_SECRET";
	private static final String RETRY_PERIOD_SECONDS = "AUTH_RETRY_PERIOD_SECONDS";

	private static final String DEFAULT_CALLBACK_PATH = "/auth/callback";
	private static final boolean DEFAULT_RP_INITIATED_LOGOUT = false;
	private static final int DEFAULT_RETRY_PERIOD_SECONDS = 60;

	@JsonProperty(required = true)
	@JsonPropertyDescription("The URL to the Keycloak realm this portal uses")
	@EnvironmentVariable(name = REALM_URL_ENV, description = "Override the realm URL")
	private String realmUrl;

	@JsonProperty(defaultValue = DEFAULT_CALLBACK_PATH)
	@JsonPropertyDescription("The path to the OAuth2 callback endpoint")
	@EnvironmentVariable(name = CALLBACK_ENDPOINT_ENV, description = "Override the path to the OAuth2 callback endpoint")
	private String callbackPath = DEFAULT_CALLBACK_PATH;

	@JsonProperty(defaultValue = "" + DEFAULT_RP_INITIATED_LOGOUT)
	@JsonPropertyDescription("Whether the portal should redirect to Keycloaks \"end_session_endpoint\" on a logout request")
	@EnvironmentVariable(name = RP_INITIATED_LOGOUT_EVN, description = "Override whether the portal should redirect to Keycloaks \"end_session_endpoint\" on a logout request")
	private boolean rpInitiatedLogout = DEFAULT_RP_INITIATED_LOGOUT;

	@JsonProperty(required = true)
	@JsonPropertyDescription("The client ID the portal should use")
	@EnvironmentVariable(name = CLIENT_ID_ENV, description = "Override the client ID")
	private String clientId;

	@JsonProperty
	@JsonPropertyDescription("The client secret taken from the configuration of the Keycloak realm")
	@EnvironmentVariable(name = CLIENT_SECRET_ENV, description = "Override the client secret")
	private String clientSecret;

	@JsonProperty
	@JsonPropertyDescription("If set to greater than zero, the Keycloak connection initialization will try to start again, if Keycloak server is unreachable. If zero, the portal is shut down. If less than zero, the old behavior (instant fail) is applied")
	@EnvironmentVariable(name = RETRY_PERIOD_SECONDS, description = "Override the 'retry' period value")
	private int retryPeriodSeconds = DEFAULT_RETRY_PERIOD_SECONDS;

	public int getRetryPeriodSeconds() {
		return retryPeriodSeconds;
	}

	public KeycloakOptions setRetryPeriodSeconds(int retryPeriodSeconds) {
		this.retryPeriodSeconds = retryPeriodSeconds;
		return this;
	}

	public String getRealmUrl() {
		return realmUrl;
	}

	public KeycloakOptions setRealmUrl(String realmUrl) {
		this.realmUrl = realmUrl;

		return this;
	}

	public String getCallbackPath() {
		if (callbackPath == null) {
			callbackPath = DEFAULT_CALLBACK_PATH;
		}

		return callbackPath;
	}

	public KeycloakOptions setCallbackPath(String callbackPath) {
		this.callbackPath = callbackPath;

		return this;
	}

	public boolean isRpInitiatedLogout() {
		return rpInitiatedLogout;
	}

	public KeycloakOptions setRpInitiatedLogout(boolean rpInitiatedLogout) {
		this.rpInitiatedLogout = rpInitiatedLogout;

		return this;
	}

	public String getClientId() {
		return clientId;
	}

	public KeycloakOptions setClientId(String clientId) {
		this.clientId = clientId;

		return this;
	}

	public String getClientSecret() {
		return clientSecret;
	}

	public KeycloakOptions setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;

		return this;
	}
}
