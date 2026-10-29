package com.gentics.mesh.portal.context;

import io.vertx.core.MultiMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Adds query parameters to an existing URI.
 *
 * <p>
 *     It can add <em>static</em> parameters, or <em>dynamic</em> ones which depend on the current
 *     {@link PortalRoutingContext}.
 * </p>
 */
public class UrlParameterInjector {

	private static final Logger log = LoggerFactory.getLogger(UrlParameterInjector.class);

	/** The static parameters to add. */
	private final MultiMap params = MultiMap.caseInsensitiveMultiMap();
	/** The dynamic parameters to add. */
	private final List<BiConsumer<PortalRoutingContext, MultiMap>> customInjectors = new ArrayList<>();

	/**
	 * Add a static parameter which should be appended by one of the <code>inject</code> methods.
	 *
	 * @see #inject(String)
	 * @see #inject(PortalRoutingContext, String)
	 *
	 * @param name The name of the parameter to add
	 * @param value The value of the parameter to add
	 * @return Fluent API
	 */
	public UrlParameterInjector add(String name, String value) {
		params.add(name, value);

		return this;
	}

	/**
	 * Add a custom injector which will add a dynamic parameter.
	 *
	 * <p>
	 *     <strong>Important:</strong> parameters added with this methid, will only be used if the final URI is
	 *     created via {@link #inject(PortalRoutingContext, String)}.
	 * </p>
	 *
	 * @param customInjector The injector which will add a parameter from the given <code>PortalRoutingContext</code>
	 * 		to the given <code>MultiMap</code>
	 * @return Fluent API
	 */
	public UrlParameterInjector add(BiConsumer<PortalRoutingContext, MultiMap> customInjector) {
		customInjectors.add(customInjector);

		return this;
	}

	/**
	 * Add all parameters from the specified map.
	 *
	 * @param other The map containing the parameters to add
	 * @return Fluent API
	 */
	public UrlParameterInjector addAll(MultiMap other) {
		if (other != null) {
			params.addAll(other);
		}

		return this;
	}

	/**
	 * Add all parameters from {@link #params} and {@link #customInjectors} to the specified <code>uri</code>.
	 *
	 * @param rc The current routing context
	 * @param uri The URI to extend
	 * @return The given <code>uri</code> with the injected query parameters
	 */
	public String inject(PortalRoutingContext rc, String uri) {
		MultiMap extendedParams;

		if (!customInjectors.isEmpty()) {
			extendedParams = MultiMap.caseInsensitiveMultiMap().addAll(params);

			customInjectors.forEach(injector -> injector.accept(rc, extendedParams));
		} else {
			extendedParams = params;
		}

		return injectInternal(uri, extendedParams);
	}

	/**
	 * Add all parameters from {@link #params} to the specified <code>uri</code>.
	 *
	 * @param uri The URI to extend
	 * @return The given <code>uri</code> with the injected query parameters
	 */
	public String inject(String uri) {
		return injectInternal(uri, params);
	}

	/**
	 * Creates the actual URI from the specified <code>uri</code> and the given <code>params</code>.
	 *
	 * @param uri The URI to extend
	 * @param params All static and dynamic parameters to add
	 * @return The given <code>uri</code> with the injected query parameters
	 */
	private String injectInternal(String uri, MultiMap params) {
		if (params.isEmpty()) {
			return uri;
		}

		StringBuilder ret = new StringBuilder(uri);
		boolean firstParam = uri.indexOf('?') < 0;

		for (Map.Entry<String, String> entry : params) {
			String key = entry.getKey();
			String val = entry.getValue();

			if (firstParam) {
				ret.append('?');
				firstParam = false;
			} else {
				ret.append('&');
			}

			try {
				ret
					.append(URLEncoder.encode(key, "utf-8"))
					.append('=')
					.append(URLEncoder.encode(val, "utf-8"));
				log.debug("Added parameter {}={}", key, val);
			} catch (UnsupportedEncodingException e) {
				log.error("Could not append parameter {}={}: {}", key, val, e.getMessage(), e);
			}
		}

		return ret.toString();
	}
}
