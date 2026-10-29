package com.gentics.mesh.portal.sitemap;

import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.util.JsonUtils;
import com.redfin.sitemapgenerator.WebSitemapGenerator;
import com.redfin.sitemapgenerator.WebSitemapUrl;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class SitemapGenerator {

	private static final Logger log = LoggerFactory.getLogger(SitemapGenerator.class);

	private final String folderSchema;
	private final String baseUrl;

	public SitemapGenerator(PortalConfig config) {
		this.folderSchema = config.getFolderSchema();
		this.baseUrl = StringUtils.removeEnd(config.getServerUrl().toString(), "/");

		log.info("Initialized sitemap generator with base URL: {}", baseUrl);
	}

	public List<String> generateSitemap(JsonObject data) throws MalformedURLException {
		WebSitemapGenerator generator = new WebSitemapGenerator(baseUrl);

		data.getJsonObject("nodes").getJsonArray("elements")
			.stream()
			.forEach(page -> addToSitemap(generator, (JsonObject) page));

		return generator.writeAsStrings();
	}

	private void addToSitemap(WebSitemapGenerator generator, JsonObject element) {
		if (folderSchema.equals(JsonUtils.getValue(element, "schema.name", null))
				|| element.getString("path") == null) {
			return;
		}

		String path = element.getString("path").toLowerCase();
		if (path.startsWith("http://") || path.startsWith("https://")) { // MAJA Is it possible to have external links in path?
			return;
		}
		path = element.getString("path");

		try {
			JsonObject fields = element.getJsonObject("fields");
			String editDate = Optional.ofNullable(fields)
				.map(f -> f.getString("editdate"))
				.orElse(DateFormatUtils.format(new Date(), "yyyy-MM-dd HH:mm:SS"));
			boolean noIndex = Optional.ofNullable(fields)
				.map(f -> f.getBoolean("noindex"))
				.orElse(false);

			if (!noIndex) { // can be also null
				path = baseUrl + path;
				WebSitemapUrl url = new WebSitemapUrl.Options(path)
					.lastMod(editDate)
					.build();
				generator.addUrl(url);
			}
		} catch (MalformedURLException | ParseException e) {
			log.error("Problem with adding element {} to sitemap.", element.getJsonObject("uuid"));
		}
	}
}
