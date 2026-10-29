package com.gentics.mesh.portal.config.util;

import java.util.Objects;

public class TestMailConfig {

	String hostname;

	int port;

	SigningOption signingOption;


	public String getHostname() {
		return hostname;
	}

	public TestMailConfig setHostname(String hostname) {
		this.hostname = hostname;
		return this;
	}

	public int getPort() {
		return port;
	}

	public TestMailConfig setPort(int port) {
		this.port = port;
		return this;
	}

	public SigningOption getSigningOption() {
		return signingOption;
	}

	public TestMailConfig setSigningOption(
			SigningOption signingOption) {
		Objects.requireNonNull(signingOption);
		this.signingOption = signingOption;
		return this;
	}

	public static class SigningOption {

	}

}
