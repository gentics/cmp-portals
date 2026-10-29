package com.gentics.mesh.portal.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.junit.BeforeClass;

import com.gentics.mesh.json.JsonUtil;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import com.gentics.mesh.rest.client.MeshResponse;
import com.gentics.mesh.util.UUIDUtil;

import io.vertx.core.json.JsonObject;

public abstract class AbstractMeshFormsProxyTest extends AbstractEndToEndTest {

	protected static String formUuid = UUIDUtil.randomUUID();

	protected static boolean initialized = false;

	protected static String formName = "inputfield_simple_text.json";

	@BeforeClass
	public static void initForm() {
		if (!initialized) {
			MeshResponse<JsonObject> response = portal.meshServer().client().put("/%s/plugins/forms/active".formatted(portal.portalConfig().getMeshProject())).getResponse().blockingGet();

			String formSource = loadResource("/forms/" + formName);
			response = portal.meshServer().client().post("/%s/plugins/forms/forms/%s".formatted(portal.portalConfig().getMeshProject(), formUuid), new JsonObject(formSource)).getResponse().blockingGet();
			assertThat(response.getStatusCode()).as("Form save status").isEqualTo(HttpStatus.SC_OK);

			response = portal.meshServer().client().get("/%s/plugins/forms/forms/%s".formatted(portal.portalConfig().getMeshProject(), formUuid)).getResponse().blockingGet();
			assertThat(response.getStatusCode()).as("Form save status").isEqualTo(HttpStatus.SC_OK);
			initialized = true;
		}
	}

	public static String makeFormdataMultipart(String formID, String formDataUuid, Map<String, String> params) {
		return JsonUtil.toJson(makeFormdata(formID, formDataUuid, params));
	}

	public static JsonObject makeFormdata(String formID, String formDataUuid, Map<String, String> params) {
		JsonObject formData = new JsonObject();
		formData.put("formID", formID);
		formData.put("formDataID", formDataUuid);
		formData.put("properties", params.entrySet().stream().map(e -> Pair.of(e.getKey(), new JsonObject().put("value", e.getValue()))).collect(Collectors.toMap(Pair::getKey, Pair::getValue)));
		return formData;
	}
}
