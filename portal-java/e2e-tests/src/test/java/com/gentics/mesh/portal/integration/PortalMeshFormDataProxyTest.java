package com.gentics.mesh.portal.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;

import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestContextStartupCheckMode;
import com.gentics.mesh.portal.TestData;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.multipart.MultipartForm;

@PortalTestSetting(
		meshImage = "docker.gentics.com/gentics/mesh-hsqldb",
		defaultLanguage = "de",
		testData = TestData.REF,
		pluginsDir = "target/plugins",
		startupChecks = TestContextStartupCheckMode.SHOULD_START)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PortalMeshFormDataProxyTest extends AbstractMeshFormsProxyTest {

	protected static String formDataID = null;

	@Test
	@Order(1)
	public void test01ValidateFormData() {
		WebClient client = WebClient.wrap(portal.portalClient());
		String token = portal.meshServer().client().getAuthentication().getToken();
		HttpResponse<Buffer> response = client.post("/api/forms/form/" + formUuid + "/validate?lang=en")
			.bearerTokenAuthentication(token)
			.rxSendMultipartForm(MultipartForm.create()
				.attribute("formdata", makeFormdataMultipart(formUuid, null, Map.of("input1", "Whatever"))))
			.blockingGet();

		assertThat(response.statusCode()).isGreaterThanOrEqualTo(200).isLessThan(400);
		JsonObject form = new JsonObject(response.body());
		assertThat(form.getBoolean("hasValidationErrors")).as("Form data ID").isNotEqualTo(true);
	}

	@Test
	@Order(2)
	public void test02PostFormData() {
		WebClient client = WebClient.wrap(portal.portalClient());
		String token = portal.meshServer().client().getAuthentication().getToken();
		HttpResponse<Buffer> response = client.post("/api/forms/form/" + formUuid + "/submit?lang=en")
//		HttpResponse<Buffer> response = client.post("/api/forms/forms/" + formUuid + "/data")
			.bearerTokenAuthentication(token)
			.rxSendMultipartForm(MultipartForm.create()
				.attribute("formdata", makeFormdataMultipart(formUuid, null, Map.of("input1", "Whatever"))))
			.blockingGet();

		assertThat(response.statusCode()).isGreaterThanOrEqualTo(200).isLessThan(400);
		JsonObject form = new JsonObject(response.body());
		formDataID = form.getString("formDataID");
		assertThat(formDataID).as("Form data ID").isNotNull();
		assertThat(form.getBoolean("hasValidationErrors")).as("Validation errors").isNotEqualTo(true);
	}

	@Test
	@Order(3)
	public void test03LoadFormData() {
		if (formDataID == null) {
			// TODO FIXME test method order works only with JUnit5
			test02PostFormData();
		}
		WebClient client = WebClient.wrap(portal.portalClient());
		String token = portal.meshServer().client().getAuthentication().getToken();
		HttpResponse<Buffer> response = client.post("/api/forms/form/" + formUuid + "/load?lang=en")
//		HttpResponse<Buffer> response = client.post("/api/forms/forms/" + formUuid + "/data/" + formID)
			.bearerTokenAuthentication(token)
			.rxSendJsonObject(new JsonObject().put("formID", formUuid))
			.blockingGet();

		assertThat(response.statusCode()).isGreaterThanOrEqualTo(200).isLessThan(400);
		JsonObject form = new JsonObject(response.body());
		assertThat(form.getString("uuid")).as("Form UUID").isEqualTo(formUuid);
		assertThat(form.getString("name")).as("Form name").isEqualTo("Form with Textfield");
		assertThat(form.getJsonObject("data")).as("Form data").isNotNull();
		assertThat(form.getJsonObject("data").getString("formID")).as("Form ID").isEqualTo(formUuid);
		assertThat(form.getJsonObject("data").getJsonObject("properties")).as("Form data properties").isNotNull();
		assertThat(form.getJsonObject("data").getJsonObject("properties").getJsonObject("input1")).as("Form data field `input1`").isNotNull();
	}

	@Test
	@Order(4)
	public void test04UpdateFormData() {
		if (formDataID == null) {
			// TODO FIXME test method order works only with JUnit5
			test02PostFormData();
		}
		WebClient client = WebClient.wrap(portal.portalClient());
		String token = portal.meshServer().client().getAuthentication().getToken();
		HttpResponse<Buffer> response = client.post("/api/forms/form/" + formUuid + "/save?lang=en")
//		HttpResponse<Buffer> response = client.post("/api/forms/forms/" + formUuid + "/data/" + formID)
			.bearerTokenAuthentication(token)
			.rxSendMultipartForm(MultipartForm.create()
				.attribute("formdata", makeFormdataMultipart(formUuid, formDataID, Map.of("input1", "Wherever"))))
			.blockingGet();

		JsonObject form = new JsonObject(response.body());
		assertThat(response.statusCode()).isGreaterThanOrEqualTo(200).isLessThan(400);
		assertThat(form.getBoolean("hasValidationErrors")).as("Validation errors").isNotEqualTo(true);
	}
}
