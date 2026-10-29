package com.gentics.mesh.portal.util;

import org.jsoup.Jsoup;

public final class HtmlUtils {

	/**
	 * Removes all html tags from the input text.
	 * 
	 * @param html
	 * @return
	 */
	public static String stripHtml(String html) {
		if (html == null) {
			return null;
		}
		return Jsoup.parse(html).text();
	}
}
