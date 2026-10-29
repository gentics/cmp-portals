package com.gentics.mesh.portal.utils;

import static com.gentics.mesh.portal.utils.HelperUtils.langFallback;
import static com.gentics.mesh.portal.utils.HelperUtils.pathLangFallback;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import io.reactivex.Observable;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class HelperUtilsTest {
	@Test
	public void testLangFallback() {
		assertThat(langFallback(languageFields(
			"name", "testName"
		),"name", "de", "en")
		).isEqualTo("testName");
		assertThat(langFallback(languageFields(
			"name", "testName",
			"name_de", "testNameDE"
			),"name", "de", "en")
		).isEqualTo("testNameDE");
		assertThat(langFallback(languageFields(
			"name", "testName",
			"name_en", "testNameEN"
			),"name", "de", "en")
		).isEqualTo("testNameEN");
		assertThat(langFallback(languageFields(
			"name", "testName",
			"name_en", "testNameEN"
			),"name", "de", "en")
		).isEqualTo("testNameEN");

		// Not found field must return an empty string
		assertThat(langFallback(languageFields(),"name", "de", "en")
		).isEqualTo("");

		// Invalid cases must return empty string
		assertThat(langFallback(null, null, null, null)).isEqualTo("");
		assertThat(langFallback(languageFields(), "name", null, null)).isEqualTo("");
	}

	@Test
	public void testPathLangFallback() {
		assertThat(
			pathLangFallback(languagePaths("en", "enPath", "de", "dePath"), "en", "de")
		).isEqualTo("enPath");

		assertThat(
			pathLangFallback(languagePaths("en", "enPath", "de", "dePath"), "de", "en")
		).isEqualTo("dePath");

		assertThat(
			pathLangFallback(languagePaths("jp", "jpPath"), "en", "de")
		).isEqualTo("jpPath");

		assertThat(pathLangFallback(null, null, null)).isEqualTo("");
	}

	private JsonObject languagePaths(String... keyValues) {
		JsonArray languages = new JsonArray();

		Observable.fromArray(keyValues)
			.buffer(2)
			.blockingForEach(pair -> languages.add(stringFieldObject(
				"language", pair.get(0),
				"path", pair.get(1)
			)));

		return new JsonObject()
			.put("languages", languages);
	}

	private JsonObject languageFields(String... keyValues) {
		return new JsonObject()
			.put("fields", stringFieldObject(keyValues));
	}

	private JsonObject stringFieldObject(String... keyValues) {
		if (keyValues.length % 2 != 0) {
			throw new RuntimeException("Parameters must be an even amount");
		}
		JsonObject fields = new JsonObject();

		Observable.fromArray(keyValues)
			.buffer(2)
			.blockingForEach(pair -> fields.put(pair.get(0), pair.get(1)));
		return fields;
	}
}
