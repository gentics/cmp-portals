package com.gentics.mesh.portal.forms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.assertj.core.util.Files;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.FormsOptions;
import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;

/**
 * Test case for rendering a form using the "renderForm" helper
 */
public class RenderFormTest {
	public static Handlebars handlebars;

	@BeforeClass
	public static void setupOnce() throws IOException {
		handlebars = new Handlebars();

		// mock the form handler (pretend rendering a form)
		FormsHandler formsHandler = mock(FormsHandler.class);
		FormsOptions formsOptions = new FormsOptions();
		
		formsOptions.setFormgenStaticFileDirectory(Files.temporaryFolderPath() + "/portal-java-render-form-test");

		// The setFormgenStaticFileDirectory() setter normalizes the path, so we read it back from the options
		// to make sure the files end up exactly where FormsHelper will look for them.
		String formgenStaticFileDirectory = formsOptions.getFormgenStaticFileDirectory();

		File jsFile = new File(formgenStaticFileDirectory + "/_expo/static/js/web/" + "main.js");
		FileUtils.writeStringToFile(jsFile, "dummy", Charset.defaultCharset());

		File cssFile = new File(formgenStaticFileDirectory + "/_expo/static/css/" + "main.css");
		FileUtils.writeStringToFile(cssFile, "dummy", Charset.defaultCharset());

		PortalConfig config = new PortalConfig();

		config.setFormsOptions(formsOptions);
	
		// register the helper
		handlebars.registerHelpers(new FormsHelper(config, null, null));
	}

	/**
	 * Test calling the helper for a form without language
	 * @throws IOException
	 */
	@Test
	public void testRenderHelper() throws IOException {
		Context context = Context.newContext(null);
		assertThat(StringEscapeUtils.unescapeHtml4(handlebars.compileInline("{{ renderForm \"formuuid\" }}").apply(context)))
				.as("Rendered form").contains("main.js", "main.css", "data-formID=\"formuuid\"");
	}

	/**
	 * Test calling the helper for a form with language
	 * @throws IOException
	 */
	@Test
	public void testRenderHelperWithLanguage() throws IOException {
		Context context = Context.newContext(null);
		assertThat(StringEscapeUtils.unescapeHtml4(handlebars.compileInline("{{ renderForm \"formuuid\" language=\"en\"}}").apply(context)))
				.as("Rendered form").contains("main.js", "main.css", "data-formID=\"formuuid\"", "data-culture=\"en\"");
	}
}
