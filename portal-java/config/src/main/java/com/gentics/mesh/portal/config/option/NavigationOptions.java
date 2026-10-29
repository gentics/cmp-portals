package com.gentics.mesh.portal.config.option;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.Config;
import com.gentics.mesh.portal.api.config.EnvironmentVariable;

public class NavigationOptions implements Config {

	public static final String NAV_BREADCRUMBS_LEVEL = "NAV_BREADCRUMBS_LEVEL";
	public static final String NAV_SORTORDER_FIELD = "NAV_SORTORDER_FIELD";
	public static final String NAV_HIDDEN_FIELD = "NAV_HIDDEN_FIELD";
	public static final String NAV_EXPIRE_TIME = "NAV_EXPIRE_TIME";

	public static final int DEFAULT_BREADCRUMBS_LEVEL = 0;
	public static final String DEFAULT_SORT_ORDER_FIELD = "navsortorder";
	public static final String DEFAULT_HIDDEN_FIELD = "navhidden";
	public static final int DEFAULT_EXPIRE_TIME = 15;

	@JsonProperty(defaultValue = "unlimited")
	@JsonPropertyDescription("The maximum number of displayed breadcrumb entries (set to 0 for unlimited)")
	@EnvironmentVariable(name = NAV_BREADCRUMBS_LEVEL, description = "Override the maximum breadcrumbs level")
	private int breadcrumbsLevel = DEFAULT_BREADCRUMBS_LEVEL;

	@JsonProperty(defaultValue = DEFAULT_SORT_ORDER_FIELD)
	@JsonPropertyDescription("The name of the field used for sorting elements")
	@EnvironmentVariable(name = NAV_SORTORDER_FIELD, description = "Override the sort order field value")
	private String sortOrderField = DEFAULT_SORT_ORDER_FIELD;

	@JsonProperty(defaultValue = DEFAULT_HIDDEN_FIELD)
	@JsonPropertyDescription("The name of the field indicating that an element is not shown in the navigation")
	@EnvironmentVariable(name = NAV_HIDDEN_FIELD, description = "Override the navhidden field value")
	private String hiddenField = DEFAULT_HIDDEN_FIELD;

	@JsonProperty(defaultValue = DEFAULT_EXPIRE_TIME + "s")
	@JsonPropertyDescription("The time in seconds after which elements in the navigation cache expire")
	@EnvironmentVariable(name = NAV_EXPIRE_TIME, description = "Override the navigation expire time value")
	private int expireTime = DEFAULT_EXPIRE_TIME;

	public int getBreadcrumbsLevel() {
		return breadcrumbsLevel;
	}

	public void setBreadcrumbsLevel(int breadcrumbsLevel) {
		this.breadcrumbsLevel = breadcrumbsLevel;
	}

	public String getSortOrderField() {
		return sortOrderField;
	}

	public void setSortOrderField(String sortOrderField) {
		this.sortOrderField = sortOrderField;
	}

	public String getHiddenField() {
		return hiddenField;
	}

	public void setHiddenField(String hiddenField) {
		this.hiddenField = hiddenField;
	}

	public int getExpireTime() {
		return expireTime;
	}

	public void setExpireTime(int expireTime) {
		this.expireTime = expireTime;
	}

}
