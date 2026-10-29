package com.gentics.mesh.portal.helper;

import java.io.IOException;

import javax.inject.Inject;

import com.gentics.mesh.portal.api.cache.CacheProvider;
import com.gentics.mesh.portal.api.cache.PortalCache;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.util.RenderContextUtils;
import com.gentics.mesh.portal.utils.HelperUtils;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.Template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EvaluateHelper that will use the context as template, containing handlebars code
 * with delimiters &lt;eval&gt; and &lt;/eval&gt;
 *
 * <p>
 *     The created templates will be cached using the default caching configuration without clear events. The cache
 *     key will contain the UUID of the currently requested page.
 *
 *     The helper accepts the following arguments:
 *     <ul>
 *         <li>
 *             <code>context</code> (String, mandatory): The template source.
 *         </li>
 *         <li>
 *             <code>cacheKey</code> (String, optinal): Additional part to use in the cache key. When this argument
 *             is missing, the template source code itself will be used.
 *         </li>
 *     </ul>
 * </p>
 */
public class EvaluateHelper implements Helper<Object> {

	private static final String startDelimiter = "<eval>";
	private static final String endDelimiter = "</eval>";

	private static final Logger log = LoggerFactory.getLogger(EvaluateHelper.class);

	private final PortalCache<Template> cache;
	private final PortalConfig config;

	@Inject
	public EvaluateHelper(CacheProvider cacheProvider, PortalConfig config) {
		this.config = config;
		this.cache = cacheProvider.<Template>cacheBuilder()
			.clearEvents()
			.build();
	}

	/**
	 * Tries to load the respective template from the cache or creates a new one, and applies it to
	 * the given options context.
	 *
	 * @param context The template source
	 * @param options The current options
	 * @return The result of applying the options context to the created template, or the template itself when
	 *     the template could not be created
	 */
	@Override
	public Object apply(Object context, Options options) {
		if (context instanceof String) {
			Template template = getTemplate(
				options,
				(String) context,
				HelperUtils.getBooleanParam(options).orElse(true));

			if (template != null) {
				try {
					return template.apply(options.context);
				} catch (Exception e) {
					log.error("Error at templating {} on {}", context, options, e);
					if (config.isTemplatesTolerateErrors()) {
						Handlebars.error(e.getMessage());
					} else {
						throw new IllegalStateException(e);
					}
				}
			}
		}

		// When the context could not be compiled as a template, just return it without changes.
		return context;
	}

	/**
	 * Try to load the template from cache or recreate it according to the <code>disableCache</code> argument.
	 *
	 * @param options The current Handlebars options
	 * @param rawTemplate The template source
	 * @param disableCache Whether to force recreation of the template, or use the cache
	 * @return The template newly created or loaded from cache
	 */
	private Template getTemplate(Options options, String rawTemplate, boolean disableCache) {
		if (disableCache) {
			log.debug("Cache disabled by helper argument");

			return createTemplate(options.handlebars, rawTemplate);
		}

		String uuid = RenderContextUtils.getNodeToRender(options.context).getString("uuid");
		String cacheKey = uuid == null
			? HelperUtils.getStringParam(options).orElse(rawTemplate)
			: uuid + "/" + HelperUtils.getStringParam(options).orElse(rawTemplate);

		return cache.get(cacheKey, ignored -> createTemplate(options.handlebars, rawTemplate));
	}

	/**
	 * Create a template using the provided Handlebars and template source.
	 *
	 * @param handlebars The Handlebars instance to create the template with
	 * @param template The template source
	 * @return The compiled template or <code>null</code> when an error occured during compilation
	 */
	private Template createTemplate(Handlebars handlebars, String template) {
		log.trace("Creating template from\n{}", template);

		try {
			return handlebars.compileInline(template, startDelimiter, endDelimiter);
		} catch (IOException e) {
			log.error("Error at templating {}", template, e);
			if (config.isTemplatesTolerateErrors()) {
				Handlebars.error(e.getMessage());
			} else {
				throw new IllegalStateException(e);
			}
		}
		return null;
	}
}
