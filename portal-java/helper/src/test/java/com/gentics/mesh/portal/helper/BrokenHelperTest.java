package com.gentics.mesh.portal.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import com.gentics.mesh.portal.api.cache.CacheProvider;
import com.gentics.mesh.portal.cache.CacheProviderImpl;
import com.gentics.mesh.portal.config.PortalConfig;
import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.TagType;

public class BrokenHelperTest {
	private Options options;

	@Before
	public void setup() throws IOException {
		Handlebars handlebars = new Handlebars();
		handlebars.registerHelper("badHelper", (context, options) -> {
			throw new IllegalStateException("Just because I can!");
		});

		options = new Options.Builder(
				handlebars,
				"evaluate",
				TagType.TRIPLE_VAR,
				Context.newContext(new HashMap<Object, Object>()),
				handlebars.compileInline(""))
			.build();
	}

	@Test
	public void testEvaluateBadHelperTolerateErrors() throws IOException {
		PortalConfig config = new PortalConfig();

		config.setTemplatesPath("/");
		config.setTemplatesTolerateErrors(true);
		config.getCachingOptions().setEnabled(false);

		CacheProvider cacheProvider = new CacheProviderImpl(config, null, null, null);
		EvaluateHelper helper = new EvaluateHelper(cacheProvider, config);

		String result = (String) helper.apply(loadResource("/evalBad.html"), options);

		assertThat(result.trim())
			.as("Rendered template")
			.isEqualTo(loadResource("/evalBad.html").trim());
	}

	@Test(expected = IllegalStateException.class)
	public void testEvaluateBadHelperTolerateNoErrors() throws IOException {
		PortalConfig config = new PortalConfig();

		config.setTemplatesPath("/");
		config.setTemplatesTolerateErrors(false);
		config.getCachingOptions().setEnabled(false);

		CacheProvider cacheProvider = new CacheProviderImpl(config, null, null, null);
		EvaluateHelper helper = new EvaluateHelper(cacheProvider, config);

		helper.apply(loadResource("/evalBad.html"), options);

		fail("Should not get here");
	}

	private String loadResource(String filename) throws IOException {
		URL resource = getClass().getResource(filename);

		System.out.println("Loading resource " + filename + ": " + resource);

		return IOUtils.toString(resource, StandardCharsets.UTF_8);
	}
}
