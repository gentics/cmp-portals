package com.gentics.mesh.portal.content;

public class Redirect {
	private String oldUrl;
	private String newUrl;

	public String getOldUrl() {
		return oldUrl;
	}

	public void setOldUrl(String oldUrl) {
		this.oldUrl = RedirectHandler.normaliseUrl(oldUrl);
	}

	public String getNewUrl() {
		return newUrl;
	}

	public void setNewUrl(String newUrl) {
		this.newUrl = newUrl;
	}

}
