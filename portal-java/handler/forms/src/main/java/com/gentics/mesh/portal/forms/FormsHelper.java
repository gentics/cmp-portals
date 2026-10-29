package com.gentics.mesh.portal.forms;

import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.FormsOptions;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Options;
import io.reactivex.Single;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.core.http.HttpClientRequest;
import io.vertx.reactivex.ext.web.templ.handlebars.HandlebarsTemplateEngine;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import static com.gentics.mesh.util.URIUtils.encodeSegment;

/**
 * Helper for rendering a form (optionally prefilled with input and error messages).
 *
 * <p>
 *     TODO Documentation for this helper is missing.
 * </p>
 */
public class FormsHelper {

	private static final Logger log = LoggerFactory.getLogger(FormsHelper.class);

	private final String projectName;
	private final String meshApiKey;
	private final FormsOptions formsOptions;
	private final String templatesPath;
	private final String pollResultsTemplate;
	private final HttpClient httpClient;
	private final HandlebarsTemplateEngine engine;

	public FormsHelper(PortalConfig portalConfig, HttpClient httpClient, io.vertx.ext.web.templ.handlebars.HandlebarsTemplateEngine engine) {
		this.projectName = portalConfig.getMeshProject();
		this.meshApiKey = portalConfig.getMeshOptions().getApiKey();
		this.formsOptions = portalConfig.getFormsOptions();
		this.templatesPath = Strings.CI.removeEnd(portalConfig.getTemplatesPath(), "/");
		this.pollResultsTemplate = Strings.CI.removeEnd(portalConfig.getPollResultsTemplate(), ".hbs");
		this.httpClient = httpClient;
		this.engine = new HandlebarsTemplateEngine(engine);
	}

	/**
	 * Include React.Native Formgen App in the given context.
	 * adds data-attributes for
	 * - formID ... which forms plugin form should be rendered
	 * - language ... which language should be used
	 *
	 * @param context context
	 * @param options options
	 * @return rendered form
	 */
	public CharSequence renderForm(Object context, Options options) {
		if (context instanceof String formUuid) {
			String language = options.hash("language", null);
			String pluginName = options.hash("pluginName", null);
			String externalId = options.hash("externalId", null);
			String templateContext = options.hash("templateContext", null);
			String voted = options.get(FormsHandler.COOKIE_NAME_VOTED);
			var showResults = Optional.ofNullable(voted)
				.map(val -> val.split(FormsHandler.COOKIE_VOTED_SEPARATOR))
				.map(votedUuids -> Arrays.asList(votedUuids).contains(formUuid))
				.orElse(options.hash("showResults", false));

			if (showResults) {
				var templatePath = "%s%s/%s".formatted(
					templatesPath,
					StringUtils.isBlank(templateContext) ? "" : "/" + templateContext,
					pollResultsTemplate);
				var url = "/api/v1/%s/plugins/forms/forms/%s/preview%s"
					.formatted(encodeSegment(projectName), encodeSegment(formUuid), StringUtils.isBlank(language) ? "" : "?lang=" + language);
				var rqOpt = new RequestOptions()
					.setMethod(HttpMethod.GET)
					.setURI(url)
					.putHeader(HttpHeaders.AUTHORIZATION, "Bearer " + meshApiKey)
					.putHeader(HttpHeaders.ACCEPT, "application/json")
					.putHeader(HttpHeaders.COOKIE, FormsHandler.COOKIE_NAME_VOTED + "=" + voted);

				return httpClient.rxRequest(rqOpt)
					.flatMap(HttpClientRequest::rxSend)
					.flatMap(rs -> {
						// Check if status code is 2xx.
						if (rs.statusCode() / 100 != 2) {
							log.error("Could not fetch poll results for form {} from {}: HTTP {}", formUuid, url, rs.statusCode());
							return Single.just("");
						}

						return rs.toObservable()
							.reduce(Buffer::appendBuffer)
							.toSingle(Buffer.buffer())
							.map(Buffer::toJsonObject)
							.flatMap(data -> engine.rxRender(data, templatePath))
							.map(Buffer::toString);
					})
					.blockingGet();
			}

			StringBuffer ret = new StringBuffer();

			//Include builded CSS Files			
			File directory = new File(this.formsOptions.getFormgenStaticFileDirectory() + "/_expo/static/css/");

			if (!directory.exists()) {
				log.error("Directory {} does not exist, can't include formgen app", directory.getAbsolutePath());
				return "";
			}

			var formgenBasePath = this.formsOptions.getFormgenStaticServePath();

			Stream.ofNullable(directory.listFiles((name, filename) -> Strings.CI.endsWith(filename, ".css")))
				.flatMap(Arrays::stream)
				.map(File::getName)
				.forEach(filename -> {
					ret.append("\n<link rel=\"stylesheet\" href=\"")
						.append(formgenBasePath)
						.append("_expo/static/css/")
						.append(filename)
						.append("\"/>");
				});

			ret.append("\n<div id=\"root\" tabindex=\"0\"");

			if (StringUtils.isNotBlank(formUuid)) {
				ret.append(" data-formID=\"")
					.append(formUuid)
					.append("\"");
			}

			if (!StringUtils.isBlank(externalId)) {
				ret.append(" data-externalId=\"")
					.append(externalId)
					.append("\"");
			}

			if (!StringUtils.isBlank(language)) {
				ret.append(" data-culture=\"")
					.append(language)
					.append("\"");
			}

			if (!StringUtils.isBlank(pluginName)) {
				ret.append(" data-plugin-name=\"")
					.append(pluginName)
					.append("\"");
			}

			if (!StringUtils.isBlank(formgenBasePath)) {
				ret.append(" data-static-serve-path=\"")
					.append(formgenBasePath)
					.append("\"");
			}

			ret.append("></div>");
			
			//Include builded JS Files 
			directory = new File(this.formsOptions.getFormgenStaticFileDirectory() + "/_expo/static/js/web/");

			if (!directory.exists()) {
				log.error("Directory {} does not exist, can't include formgen app", directory.getAbsolutePath());
				return "";
			}

			Stream.ofNullable(directory.listFiles((name, filename) -> Strings.CI.endsWith(filename, ".js")))
				.flatMap(Arrays::stream)
				.map(File::getName)
				.forEach(filename -> {
					ret.append("\n<script type=\"module\" src=\"")
						.append(formgenBasePath)
						.append("_expo/static/js/web/")
						.append(filename)
						.append("\"></script>");
				});

			return ret.append("\n").toString();
		}

		Handlebars.error("renderForm: No form UUID given");

		return "";
	}
}
