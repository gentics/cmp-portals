package com.gentics.mesh.portal.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gentics.mesh.portal.api.Constants;
import com.gentics.mesh.portal.api.cache.CacheProvider;
import com.gentics.mesh.portal.cache.CacheProviderImpl;
import com.gentics.mesh.portal.config.PortalConfig;
import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.TagType;

import io.vertx.core.json.JsonObject;

/**
 * Tests for the evaluate helper.
 *
 * <p>
 *     Currently there is only one test, and it is actually for profiling the helpers performance.
 * </p>
 */
public class EvaluateHelperTest {

	// Set to true, when the tests are run for profiling reasons.
	private static final boolean profiling = false;
	private static final Map<String, Object> model = new HashMap<>();

	private Options options;

	@BeforeClass
	public static void setupOnce() {
		List<Map<String, Object>> data = new ArrayList<>();

		for (char c : "abcdef".toCharArray()) {
			String stringValue = String.join("", Collections.nCopies(3, String.valueOf(c)));

			for (int i = 0; i < 10; i++) {
				data.add(createEntry(stringValue, i, true));
				data.add(createEntry(stringValue, i, false));
			}
		}

		model.put("data", data);
		model.put(Constants.RC_NODE_TO_RENDER, new JsonObject().put("uuid", "1"));
	}

	private static Map<String, Object> createEntry(String stringValue, Integer intValue, Boolean booleanValue) {
		Map<String, Object> entry = new HashMap<>();

		entry.put("stringValue", stringValue);
		entry.put("intValue", intValue);
		entry.put("booleanValue", booleanValue);

		return entry;
	}

	@Before
	public void setup() throws IOException {
		Handlebars handlebars = new Handlebars();

		options = new Options.Builder(
				handlebars,
				"evaluate",
				TagType.TRIPLE_VAR,
				Context.newContext(model),
				handlebars.compileInline(""))
			.build();
	}

	@Test
	public void testEvaluateHelperWithoutCache() throws IOException {
		PortalConfig config = new PortalConfig();

		config.setTemplatesPath("/");
		config.getCachingOptions().setEnabled(false);

		CacheProvider cacheProvider = new CacheProviderImpl(config, null, null, null);
		EvaluateHelper helper = new EvaluateHelper(cacheProvider, config);

		String result = null;
		int runs = profiling ? 50_000 : 1;

		for (int i = 0; i < runs; i++) {
			result = (String) helper.apply(loadResource("/evalTemplate.html"), options);
		}

		assertThat(result.trim())
			.as("Rendered template")
			.isEqualTo(loadResource("/renderedTemplate.html").trim());
	}

	private String loadResource(String filename) throws IOException {
		URL resource = getClass().getResource(filename);

		System.out.println("Loading resource " + filename + ": " + resource);

		return IOUtils.toString(resource, StandardCharsets.UTF_8);
	}

}
