package com.gentics.mesh.portal.formgenerator.helper;

import java.io.IOException;

import javax.inject.Inject;

import com.gentics.mesh.portal.formgenerator.FormGeneratorHandler;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Options;

/**
 * Helper for rendering a form (optionally prefilled with input and error messages)
 */
public class FormHelper {

	private FormGeneratorHandler handler;

	/**
	 * Create an instance
	 * 
	 * @param handler
	 *            handler
	 */
	@Inject
	public FormHelper(FormGeneratorHandler handler) {
		this.handler = handler;
	}

	/**
	 * Render the form with uuid in the given context.
	 * 
	 * @param context
	 *            context
	 * @param options
	 *            options
	 * @return rendered form
	 * @throws IOException
	 */
	public CharSequence fgRender(Object context, Options options) throws IOException {
		if (context instanceof String) {
			String formUuid = (String) context;

			String formDataUuid = options.get(FormGeneratorHandler.COOKIE_NAME);
			if (formDataUuid != null) {
				return handler.render(formUuid, formDataUuid).blockingGet();
			} else {
				return handler.render(formUuid).blockingGet();
			}
		} else {
			Handlebars.error("fgRender: No uuid given");
			return "";
		}
	}
}
