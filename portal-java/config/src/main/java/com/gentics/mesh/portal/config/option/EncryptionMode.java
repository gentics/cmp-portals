package com.gentics.mesh.portal.config.option;

/**
 * Available encryption modes.
 */
public enum EncryptionMode {
	/** Encryption is disabled. */
	DISABLED,
	/** Secret key is generated from configured password. */
	PASSWORD_ONLY_KEY,
	/** Secret key is auto-generated during portal startup. */
	AUTO_GENERATED_KEY
}
