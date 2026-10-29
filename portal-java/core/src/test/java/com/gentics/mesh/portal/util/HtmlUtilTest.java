package com.gentics.mesh.portal.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.gentics.mesh.portal.util.HtmlUtils;

public class HtmlUtilTest {

	@Test
	public void testEscaping() {
		assertEquals("text", HtmlUtils.stripHtml("<b>text</b>"));
	}
}
