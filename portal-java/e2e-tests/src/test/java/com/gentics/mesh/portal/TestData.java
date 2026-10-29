package com.gentics.mesh.portal;

public enum TestData {
	REF("Reference", "ref"),
	DEMO(null, null);

	private final String projectName;
	private final String schemaPrefix;

	TestData(String projectName, String schemaPrefix) {
		this.projectName = projectName;
		this.schemaPrefix = schemaPrefix;
	}

	public String projectName() {
		return projectName;
	}

	public String schemaPrefix() {
		return schemaPrefix;
	}
}
