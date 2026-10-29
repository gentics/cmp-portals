package com.gentics.mesh.portal.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.assertj.core.util.Files;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gentics.mesh.portal.DefaultPortalConfig;
import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestContextStartupCheckMode;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.forms.FormsHelper;
import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.templ.handlebars.HandlebarsTemplateEngine;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.multipart.MultipartForm;

@PortalTestSetting(
		meshImage = "docker.gentics.com/gentics/mesh-hsqldb",
		defaultLanguage = "de",
		testData = TestData.REF,
		pluginsDir = "target/plugins",
		configProvider = PortalRenderFormTest.Config.class,
		startupChecks = TestContextStartupCheckMode.SHOULD_START)
public class PortalRenderFormTest extends AbstractMeshFormsProxyTest {

	protected static final String POLL_RESULTS_RENDERED = "<div>Element: input1 = Total: 1</div>";

	protected static final String LANGUAGE = "en";

	public static class Config implements Supplier<PortalConfig> {
		@Override
		public PortalConfig get() {
			PortalConfig portalConfig = new DefaultPortalConfig().get();
			portalConfig.getFormsOptions().setFormgenStaticFileDirectory(Files.temporaryFolderPath() + "/portal-java-render-form-test");

			// The setFormgenStaticFileDirectory() setter normalizes the path, so we read it back from the options
			// to make sure the files end up exactly where FormsHelper will look for them.
			String formgenStaticFileDirectory = portalConfig.getFormsOptions().getFormgenStaticFileDirectory();

			try {
				File jsFile = new File(formgenStaticFileDirectory + "/_expo/static/js/web/" + "main.js");
				FileUtils.writeStringToFile(jsFile, "dummy", Charset.defaultCharset());
				File cssFile = new File(formgenStaticFileDirectory + "/_expo/static/css/" + "main.css");
				FileUtils.writeStringToFile(cssFile, "dummy", Charset.defaultCharset());
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}

			return portalConfig;
		}
	}

	@BeforeClass
	public static void initForm() {
		formName = "poll.json";
		AbstractMeshFormsProxyTest.initForm();
	}

	@Test
	public void testPoll() throws IOException {
		postData();

		HttpClient helperMeshClient = HttpClient.newInstance(
				portal.portalServerComponent().vertx().createHttpClient(
						new HttpClientOptions()
							.setDefaultHost(portal.meshServer().getHost())
							.setDefaultPort(portal.meshServer().getPort())
							.setTrustAll(true)
							.setVerifyHost(false)));
		HandlebarsTemplateEngine engine = portal.portalServerComponent().runner().getHandlebarsTemplateEngine();
		Handlebars handlebars = engine.unwrap();
		handlebars.registerHelpers(new FormsHelper(portal.portalConfig(), helperMeshClient, engine));
		Context context = Context.newContext(null).data("forms-voted", formUuid);
		assertThat(StringEscapeUtils.unescapeHtml4(
				handlebars.compileInline("{{ renderForm \"%s\" templateContext=\"ref\" language=\"%s\" }}".formatted(formUuid, LANGUAGE)).apply(context)))
					.as("Rendered form").isEqualTo(POLL_RESULTS_RENDERED);
	}

	protected void postData() {
		WebClient client = WebClient.wrap(portal.portalClient());
		String token = portal.meshServer().client().getAuthentication().getToken();
		HttpResponse<Buffer> response = client.post("/api/forms/form/%s/submit?lang=%s".formatted(formUuid, LANGUAGE))
			.bearerTokenAuthentication(token)
			.rxSendMultipartForm(MultipartForm.create()
				.attribute("formdata", makeFormdataMultipart(formUuid, null, Map.of("input1", "Whatever"))))
			.blockingGet();

		assertThat(response.statusCode()).isGreaterThanOrEqualTo(200).isLessThan(400);
		JsonObject form = new JsonObject(response.body());
		assertThat(form.getString("formDataID")).as("Form data ID").isNotNull();
		assertThat(form.getBoolean("hasValidationErrors")).as("Validation errors").isNotEqualTo(true);
	}
}
