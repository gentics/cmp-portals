package com.gentics.mesh.portal.context;

import io.vertx.core.MultiMap;
import org.junit.Test;

import java.util.Arrays;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to check if {@link UrlParameterInjector} creates correct URL query parameter strings.
 */
public class UrlParameterInjectorTest {

	private final MultiMap otherParams = MultiMap.caseInsensitiveMultiMap()
		.add("foo", "1")
		.add("bar", Arrays.<String>asList("2", "3"))
		.add("doo", "");

	/**
	 * Test injection without parameters in the original URL.
	 */
	@Test
	public void testInjectionsNoInitialParam() {
		testAllInjections(false);
	}

	/**
	 * Test injection where the original URL already has query parameters.
	 */
	@Test
	public void testInjectionsWithInitialParam() {
		testAllInjections(true);
	}

	/**
	 * Check that calling {@link UrlParameterInjector#inject(String) inject()} without a routing context
	 * never returns dynamic parameters.
	 */
	@Test
	public void testInjectKeepsState() {
		String baseUrl = "https://localhost/";
		String customKey = "custom";
		String customVal = "injection";
		String expected = String.format("%s?%s=%s", baseUrl, customKey, customVal);
		UrlParameterInjector injector = new UrlParameterInjector().add(customInjector(customKey, customVal));

		assertThat(injector.inject(baseUrl))
			.as("First empty injection")
			.isEqualTo(baseUrl);

		assertThat(injector.inject(null, baseUrl))
			.as("First custom injection")
			.isEqualTo(expected);


		// Do the injections again to verify that the internal state did not change
		// by performing the injections.
		assertThat(injector.inject(baseUrl))
			.as("Second empty injection")
			.isEqualTo(baseUrl);

		assertThat(injector.inject(null, baseUrl))
			.as("Second custom injection")
			.isEqualTo(expected);
	}

	/**
	 * Use all methods to add parameters and check the results.
	 *
	 * <p>
	 *     At least one of the added parameters will have values, that need to be URL escaped.
	 * </p>
	 *
	 * @param initialParam Whether the original URL should already have a parameter or not
	 */
	private void testAllInjections(boolean initialParam) {
		String baseUrl;
		String expected;

		if (initialParam) {
			baseUrl = "http://localhost/?fixed=param";
			expected = baseUrl + "&a=%3A%3F%2F";
		} else {
			baseUrl = "http://localhost/";
			expected = baseUrl + "?a=%3A%3F%2F";
		}

		UrlParameterInjector injector = new UrlParameterInjector();

		assertThat(injector.add("a", ":?/").inject(baseUrl))
			.as("Normal injection")
			.isEqualTo(expected);

		expected += "&foo=1&bar=2&bar=3&doo=";
		assertThat(injector.addAll(otherParams).inject(baseUrl))
			.as("Add all injection")
			.isEqualTo(expected);

		String customKey = "custom";
		String customVal = "injection";

		expected += String.format("&%s=%s", customKey, customVal);
		assertThat(injector.add(customInjector(customKey, customVal)).inject(null, baseUrl))
			.as("Custom injection")
			.isEqualTo(expected);
	}

	/**
	 * Create a custom injector that adds the specified parameter.
	 *
	 * @param name The name of the parameter to inject
	 * @param value The value of the parameter to inject
	 * @return A custom injector that adds the specified parameter
	 */
	private BiConsumer<PortalRoutingContext, MultiMap> customInjector(String name, String value) {
		return (rc, map) -> map.add(name, value);
	}
}
