package com.gentics.mesh.portal.implementation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts the zipped package of a portal implementation into the configured folders.
 *
 * <p>
 *     Each top level folder of the zip file, which has a target (e.g. {@code templates}), replaces the contents of its
 *     target folder. Target folders whose folder is not contained in the zip file are emptied. All other top level
 *     entries are ignored and a warning is logged.
 * </p>
 *
 * <p>
 *     The zip file is completely unpacked before any target folder is changed, so an invalid zip file leaves all target
 *     folders untouched.
 * </p>
 */
public class PortalImplementationExtractor {

	private static final Logger log = LoggerFactory.getLogger(PortalImplementationExtractor.class);

	/**
	 * Extract the given zip into the target folders, and empty the target folders whose folder is missing in the zip.
	 *
	 * @param zip zip file contents
	 * @param targets map of top level folder names in the zip file to the target folders
	 * @return names of the top level folders that were extracted
	 * @throws IOException when reading the zip file or writing the files fails, or the zip file contains an entry
	 * 		pointing outside its top level folder
	 */
	public Set<String> extract(InputStream zip, Map<String, Path> targets) throws IOException {
		Path tempDir = Files.createTempDirectory("portal-implementation");

		try {
			Set<String> extracted = unzip(zip, tempDir, targets);

			for (Map.Entry<String, Path> entry : targets.entrySet()) {
				String folder = entry.getKey();
				File target = entry.getValue().toAbsolutePath().toFile();

				if (extracted.contains(folder)) {
					log.info("Replacing contents of {} with folder {} of the portal implementation", target, folder);

					if (target.exists()) {
						FileUtils.cleanDirectory(target);
					}

					FileUtils.copyDirectory(tempDir.resolve(folder).toFile(), target);
				} else if (target.isDirectory()) {
					log.info("Emptying {}, because the portal implementation does not contain the folder {}", target, folder);
					FileUtils.cleanDirectory(target);
				}
			}

			return extracted;
		} finally {
			FileUtils.deleteQuietly(tempDir.toFile());
		}
	}

	/**
	 * Unzip the entries belonging to one of the targets into the temporary directory.
	 *
	 * @return names of the top level folders found in the zip file, which have a target
	 */
	private Set<String> unzip(InputStream zip, Path tempDir, Map<String, Path> targets) throws IOException {
		Set<String> extracted = new LinkedHashSet<>();
		Set<String> ignored = new LinkedHashSet<>();

		try (ZipInputStream in = new ZipInputStream(zip)) {
			ZipEntry entry;

			while ((entry = in.getNextEntry()) != null) {
				String name = entry.getName().replace('\\', '/');
				int slash = name.indexOf('/');
				String topLevel = slash < 0 ? name : name.substring(0, slash);

				if (slash < 0 || !targets.containsKey(topLevel)) {
					if (!topLevel.isEmpty() && ignored.add(topLevel)) {
						log.warn("Ignoring entry {} of the portal implementation, only the folders {} are extracted", topLevel, targets.keySet());
					}

					continue;
				}

				Path topLevelDir = tempDir.resolve(topLevel).normalize();
				Path destination = tempDir.resolve(name).normalize();

				if (!destination.startsWith(topLevelDir)) {
					throw new IOException(String.format("Entry %s of the portal implementation points outside of its folder", name));
				}

				extracted.add(topLevel);

				if (entry.isDirectory()) {
					Files.createDirectories(destination);
				} else {
					Files.createDirectories(destination.getParent());
					Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}

		return extracted;
	}
}
