package com.gentics.mesh.portal;

import java.net.URL;
import java.util.function.Supplier;

import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.PortalConfigLoader;
import com.gentics.mesh.portal.config.option.MonitoringOptions;

public class DefaultPortalConfig implements Supplier<PortalConfig> {
	@Override
	public PortalConfig get() {
		try {
			PortalConfig config = new PortalConfig();
			config.setDefaultLanguage("de");
			config.setMeshProject("Reference");
			config.getVertxOptions().setEventPoolSize(1);
			config.setTemplatesPath("src/test/resources/templates");
			config.setQueryPath("src/test/resources/queries");

			MonitoringOptions monitoring = config.getMonitoringOptions();
			monitoring.setEnabled(true);
			monitoring.setPort(0);
			monitoring.setHost("localhost");

			// We'll set the valid key after init
			config.setPortalAPIKey("bogus");
			config.getMeshOptions().setApiKey("bogus");
			config.setServerUrl(new URL("http://localhost"));
			config.setServerPort(0);

			config.getCachingOptions().setDiskCacheRegionName(null);

			return config;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
