package com.gentics.mesh.portal.config.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gentics.mesh.portal.api.config.Config;

public class TestConfig implements Config {

	TestMailConfig mailConfig = new TestMailConfig();

	@JsonProperty("mail")
	public TestMailConfig getMailConfig() {
		return mailConfig;
	}

	@JsonProperty("mail")
	public void setMailConfig(TestMailConfig mailConfig) {
		this.mailConfig = mailConfig;
	}


}
