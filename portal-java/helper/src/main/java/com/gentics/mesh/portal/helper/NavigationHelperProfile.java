package com.gentics.mesh.portal.helper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

/**
 * A profile for rendering navigation and breadcrumb elements.
 *
 * <p>
 *     When a {@link #prefix} and/or {@link #postfix} are set, navigation elements rendered with this profile
 *     will be wrapped accordingly.
 * </p>
 *
 * <p>
 *     The {@link #classFormat} is used to generate a {@link #cssClass(boolean, int) CSS class} for an element
 *     where the first placeholder is replaced by {@link #activeClass} when the element is active, and an empty
 *     string otherwise. The second placeholder is replaced by the level of the element.
 * </p>
 *
 * <p>
 *     {@link #autoLanguageFallback Auto language fallback} is used by several navigation and breadcrumb helpers to
 *     determine whether names and URLs should use language fallback automatically.
 * </p>
 *
 * <p>
 *     When no wrapping is required {@link #noWrapping()} can be used as a shorthand to remove both the {@link #prefix}
 *     and {@link #postfix} at the same time, while {@link #hasWrapper()} can be used to determine whether
 *     wrapping is necessary or not.
 * </p>
 */
public class NavigationHelperProfile {

	/** The name of the profile (only used for the string representation). */
	@JsonProperty
	private String name;
	/** The prefix for all navigation elements rendered with this profile. */
	@JsonProperty
	private String prefix = "<ul>";
	/** The postfix for all navigation elements rendered with this profile. */
	@JsonProperty
	private String postfix = "</ul>";
	/** The CSS class to mark active navigation elements. */
	@JsonProperty
	private String activeClass = "active";
	/** The format string for creating the CSS class of an navigation element. */
	@JsonProperty
	private String classFormat = "nav %s level-%d";
	/** Whether navigation and breadcrumb elements rendered with this profile should do language fallback automatically. */
	@JsonProperty
	private boolean autoLanguageFallback = true;

	public String getName() {
		return name;
	}

	public NavigationHelperProfile setName(String name) {
		this.name = name;

		return this;
	}

	public String getPrefix() {
		return prefix;
	}

	public NavigationHelperProfile setPrefix(String prefix) {
		if (prefix == null) {
			prefix = "";
		}

		this.prefix = prefix;

		return this;
	}

	public String getPostfix() {
		return postfix;
	}

	public NavigationHelperProfile setPostfix(String postfix) {
		if (postfix == null) {
			postfix = "";
		}

		this.postfix = postfix;

		return this;
	}

	public String getActiveClass() {
		return activeClass;
	}

	public NavigationHelperProfile setActiveClass(String activeClass) {
		if (activeClass == null) {
			activeClass = "";
		}

		this.activeClass = activeClass;

		return this;
	}

	public String getClassFormat() {
		return classFormat;
	}

	public NavigationHelperProfile setClassFormat(String classFormat) {
		if (classFormat == null) {
			classFormat = "";
		}

		this.classFormat = classFormat;

		return this;
	}

	public boolean isAutoLanguageFallback() {
		return autoLanguageFallback;
	}

	public NavigationHelperProfile setAutoLanguageFallback(boolean autoLanguageFallback) {
		this.autoLanguageFallback = autoLanguageFallback;

		return this;
	}

	/**
	 * Checks if either {@link #prefix} or {@link #postfix} is not empty.
	 *
	 * @return <code>true</code> when either {@link #prefix} or {@link #postfix} is not empty
	 */
	@JsonIgnore
	public boolean hasWrapper() {
		return !(prefix.isEmpty() || postfix.isEmpty());
	}

	/**
	 * Remove {@link #prefix} and {@link #postfix}.
	 * @return A reference to this for fluent API
	 */
	@JsonIgnore
	public NavigationHelperProfile noWrapping() {
		prefix = postfix = "";

		return this;
	}

	/**
	 * Create the CSS class for an element with the given active state and level
	 * @param active Whether the element is in the path of the currently rendered page
	 * @param level The level of the navigation element
	 * @return The CSS class for the element
	 */
	@JsonIgnore
	public String cssClass(boolean active, int level) {
		return String.format(classFormat, active ? activeClass : "", level);
	}

	@Override
	public String toString() {
		return StringUtils.isBlank(name) ? super.toString() : name;
	}
}
