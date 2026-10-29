package com.gentics.mesh.portal.api.content;

/**
 * The render modes of the portal.
 *
 * <p>
 *     Currently the portal can only distinguish between PREVIEW and PUBLISHED, but in the future all render modes
 *     of the Gentics CMS will be available.
 * </p>
 */
public enum PreviewMode {

	/** The page was rendered via the /api/preview endpoint, but no preview mode could be determined. */
	UNKNOWN,

	/** The page is rendered in preview mode for the CMS. */
	PREVIEW,

	/** The page is rendered in edit mode for the CMS. */
	EDIT,

	/** The page is rendered by the front end. */
	PUBLISH
}
