package com.gentics.mesh.portal.api.monitoring;

public enum PortalMetric {
	CACHE_HIT("cache.hit", "Meter which counts the cache hits of the CachingHandler"),
	CACHE_MISS("cache.miss", "Meter which counts the cache misses of the CachingHandler"),
	MESH_LOAD_CONTENT("mesh.load.content", "PortalTimer which measures duration of GraphQL queries for content"),
	MESH_LOAD_BINARY("mesh.load.binary", "PortalTimer which measures duration of webroot requests for binaries"),
	MESH_LOAD_GIS("mesh.load.gis", "PortalTimer which measures duration of webroot requests Gentics Image Store"),
	MESH_LOAD_NAVIGATION("mesh.load.navigation", "PortalTimer which measures duration of GraphQL queries for navigation"),
	RENDER_TEMPLATE("template.render", "PortalTimer which measures template rendering times");

	private final String key;
	private final String description;

	PortalMetric(String key, String description) {
		this.key = "portal." + key;
		this.description = description;
	}

	public String key() {
		return key;
	}

	public String description() {
		return description;
	}
}
