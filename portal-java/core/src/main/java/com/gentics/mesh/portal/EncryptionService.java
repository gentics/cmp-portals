package com.gentics.mesh.portal;

import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.SecurityOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Service for encrypting and decrypting strings.
 *
 * <p>
 *     The behavior depends on the setting {@code authentication.encryptionMode}:
 *     <ul>
 *         <li>
 *             DISABLED: no encryption is performed, the result of
 *             {@link #encrypt(String)} and {@link #decrypt(String)} will
 *             always be empty strings.
 *         </li>
 *         <li>
 *             PASSWORD_ONLY_KEY: the value of
 *             {@code authentication.encryptionPassword} is used as a secret
 *             key for encrypting with {@code AES/ECB/PKCS5Padding}, which is
 *             less secure than AUTO_GENERATED_KEY, but cookies will be valid
 *             across server restarts and multiple portal instances.
 *         </li>
 *         <li>
 *             AUTO_GENERATED_KEY: a random secret key and initial vector are
 *             generated for encrypting with {@code AES/CBC/PKCS5Padding}. This
 *             is the most secure encryption but will not work when multiple
 *             portal instances are used, and cookies will become invalid after
 *             a server restart.
 *         </li>
 *     </ul>
 * </p>
 */
@Singleton
public class EncryptionService {

	private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

	private static final String ALGORITHM_PASSWORD_ONLY = "AES/ECB/PKCS5Padding";
	private static final String ALGORITHM_AUTO_GENERATED_KEY = "AES/CBC/PKCS5Padding";

	private final SecretKey secretKey;
	private final IvParameterSpec initialVector;

	@Inject
	public EncryptionService(PortalConfig config) {
		SecurityOptions options = config.getSecurityOptions();

		switch (options.getEncryptionMode()) {
		case PASSWORD_ONLY_KEY:
			secretKey = generateKeyFromPassword(options.getEncryptionPassword());
			initialVector = null;
			break;

		case AUTO_GENERATED_KEY:
			secretKey = generateKey();
			initialVector = generateIv();
			break;

		case DISABLED:
		default:
			secretKey = null;
			initialVector = null;
			break;
		}

		if (secretKey == null) {
			log.info("Encryption service disabled");
		}
	}

	/**
	 * Encrypt the given string.
	 *
	 * @param plainText The string to encrypt
	 * @return The Base64 representation of the encrypted string
	 */
	public String encrypt(String plainText) {
		if (plainText == null) {
			return null;
		}

		Cipher cipher = getCipher(Cipher.ENCRYPT_MODE);

		if (cipher != null) {
			try {
				return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
			} catch (IllegalBlockSizeException | BadPaddingException e) {
                log.error("Could not encrypt input string: {}", e.getMessage(), e);
			}
		}

		return "";
	}

	/**
	 * Descrypt the given string.
	 *
	 * @param cipherText A Base64 representation of an encrypted string
	 * @return The decrypted string
	 */
	public String decrypt(String cipherText) {
		if (cipherText == null) {
			return null;
		}

		Cipher cipher = getCipher(Cipher.DECRYPT_MODE);

		if (cipher != null) {
			try {
				byte[] input = Base64.getDecoder().decode(cipherText);

				return new String(cipher.doFinal(input), StandardCharsets.UTF_8);
			} catch (Exception e) {
                log.error("Could not decode/decrypt input string: {}", e.getMessage(), e);
			}
		}

		return "";
	}

	/**
	 * Generate a {@link SecretKey} from the given password by taking the first
	 * 16 bytes from its SHA-1 hash.
	 *
	 * @param password The password to generate a key from
	 * @return A secret key generated from the SHA-1 hash of the given password
	 */
	private SecretKey generateKeyFromPassword(String password) {
		log.info("Creating secret key from configured password");

		MessageDigest sha = null;

		try {
			sha = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
            log.error("Could not generate key from password: {}", e.getMessage(), e);

			return null;
		}

		byte[] digest = sha.digest(password.getBytes(StandardCharsets.UTF_8));

		return new SecretKeySpec(Arrays.copyOf(digest, 16), "AES");
	}

	/**
	 * Generate a random 256 bit AES key.
	 *
	 * @return A random 256 bit AES key
	 */
	private SecretKey generateKey() {
		KeyGenerator generator = null;

		try {
			generator = KeyGenerator.getInstance("AES");
		} catch (NoSuchAlgorithmException e) {
			log.error("Could not instantiate key generator: {}", e.getMessage());

			return null;
		}

		generator.init(256);

		return generator.generateKey();
	}

	/**
	 * Generate a random initial vector of 16 bytes.
	 *
	 * @return A random initival vector of 16 bytes
	 */
	private IvParameterSpec generateIv() {
		byte[] iv = new byte[16];

		new SecureRandom().nextBytes(iv);

		return new IvParameterSpec(iv);
	}

	/**
	 * Create a cipher for the specified mode and initialize it with the
	 * secret key, and if available the initial vector.
	 *
	 * @param mode The cipher mode ({@link Cipher#ENCRYPT_MODE}
	 * 		or {@link Cipher#DECRYPT_MODE})
	 * @return An initialized cipher for the given mode
	 */
	private Cipher getCipher(int mode) {
		Cipher cipher = null;

		try {
			if (initialVector == null) {
				cipher = Cipher.getInstance(ALGORITHM_PASSWORD_ONLY);
				cipher.init(mode, secretKey);
			} else {
				cipher = Cipher.getInstance(ALGORITHM_AUTO_GENERATED_KEY);
				cipher.init(mode, secretKey, initialVector);
			}
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e) {
            log.error("Could not initialize cipher: {}", e.getMessage(), e);
		}

		return cipher;
	}
}
