package com.gentics.mesh.portal;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.function.Supplier;

import org.apache.commons.lang.StringUtils;

import com.gentics.mesh.portal.config.PortalConfig;

@Retention(RetentionPolicy.RUNTIME)
public @interface PortalTestSetting {

	String meshImage() default StringUtils.EMPTY;

	String defaultLanguage() default "en";

	TestData testData() default TestData.DEMO;

	Class<? extends Supplier<PortalConfig>> configProvider() default DefaultPortalConfig.class;

	TestContextStartupCheckMode startupChecks() default TestContextStartupCheckMode.SKIP;

	int startupCheckDelay() default 0;

	String keycloakConfig() default StringUtils.EMPTY;

	String keycloakRealmName() default "master-test";

	String keycloakClientId() default "portal";

	boolean startKeycloak() default true;

	int keycloakInitRetryPeriodSeconds() default -1;

	String pluginsDir() default StringUtils.EMPTY;
}
