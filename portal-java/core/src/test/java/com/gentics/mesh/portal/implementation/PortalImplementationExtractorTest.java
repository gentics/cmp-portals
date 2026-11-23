package com.gentics.mesh.portal.implementation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Test cases for {@link PortalImplementationExtractor}.
 */
public class PortalImplementationExtractorTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final PortalImplementationExtractor extractor = new PortalImplementationExtractor();

	private Map<String, Path> targets;

	private Path base;

	@Before
	public void setup() throws IOException {
		base = tmp.newFolder("data").toPath();
		targets = new LinkedHashMap<>();
		targets.put("templates", base.resolve("my-templates"));
		targets.put("queries", base.resolve("my-queries"));
		targets.put("i18n", base.resolve("my-i18n"));
		targets.put("static", base.resolve("my-static"));
	}

	/**
	 * Test that the known folders are extracted into their configured target folders, including subfolders.
	 */
	@Test
	public void testFoldersExtractedToTargets() throws IOException {
		Set<String> extracted = extractor.extract(zip(
			"templates/page.hbs", "page",
			"templates/partials/header.hbs", "header",
			"queries/loadByPath.graphql", "query",
			"i18n/translations.yml", "i18n",
			"static/css/main.css", "css"), targets);

		assertEquals(Set.of("templates", "queries", "i18n", "static"), extracted);
		assertContent(base.resolve("my-templates/page.hbs"), "page");
		assertContent(base.resolve("my-templates/partials/header.hbs"), "header");
		assertContent(base.resolve("my-queries/loadByPath.graphql"), "query");
		assertContent(base.resolve("my-i18n/translations.yml"), "i18n");
		assertContent(base.resolve("my-static/css/main.css"), "css");
	}

	/**
	 * Test that unknown folders and files in the root of the zip are not extracted anywhere.
	 */
	@Test
	public void testUnknownEntriesIgnored() throws IOException {
		Set<String> extracted = extractor.extract(zip(
			"templates/page.hbs", "page",
			"other/file.txt", "other",
			"readme.txt", "readme"), targets);

		assertEquals(Set.of("templates"), extracted);
		assertContent(base.resolve("my-templates/page.hbs"), "page");

		try (var files = Files.walk(base)) {
			assertEquals("Only the templates folder and its file must exist", 2,
				files.filter(path -> !path.equals(base)).count());
		}
	}

	/**
	 * Test that the existing contents of a target folder are replaced, and target folders not contained in the zip
	 * are emptied.
	 */
	@Test
	public void testExistingFilesReplaced() throws IOException {
		Path templates = Files.createDirectories(base.resolve("my-templates"));
		Path queries = Files.createDirectories(base.resolve("my-queries"));

		Files.writeString(templates.resolve("page.hbs"), "old page");
		Files.writeString(Files.createDirectories(templates.resolve("old")).resolve("removed.hbs"), "removed");
		Files.writeString(queries.resolve("local.graphql"), "local query");
		Files.writeString(Files.createDirectories(queries.resolve("fragments")).resolve("fragment.graphql"), "fragment");

		extractor.extract(zip("templates/page.hbs", "new page"), targets);

		assertContent(templates.resolve("page.hbs"), "new page");
		assertFalse("Files not contained in the package must be removed", Files.exists(templates.resolve("old")));
		assertTrue("Folder missing in the package must still exist", Files.isDirectory(queries));

		try (var files = Files.list(queries)) {
			assertEquals("Folder missing in the package must be empty", 0, files.count());
		}

		assertFalse("Non-existing target folders must not be created", Files.exists(base.resolve("my-i18n")));
	}

	/**
	 * Test that an invalid zip file leaves all target folders untouched, also the ones missing in the zip.
	 */
	@Test
	public void testInvalidZipLeavesFoldersUntouched() throws IOException {
		Path queries = Files.createDirectories(base.resolve("my-queries"));

		Files.writeString(queries.resolve("local.graphql"), "local query");

		try {
			extractor.extract(zip("templates/../../evil.txt", "evil"), targets);
			fail("Extraction must fail");
		} catch (IOException e) {
			// expected
		}

		assertContent(queries.resolve("local.graphql"), "local query");
	}

	/**
	 * Test that entries pointing outside their folder are rejected, and nothing is extracted.
	 */
	@Test
	public void testZipSlipRejected() throws IOException {
		try {
			extractor.extract(zip(
				"templates/page.hbs", "page",
				"templates/../../evil.txt", "evil"), targets);
			fail("Extraction must fail");
		} catch (IOException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("points outside"));
		}

		assertFalse(Files.exists(base.resolve("my-templates")));
		assertFalse(Files.exists(base.getParent().resolve("evil.txt")));
	}

	private void assertContent(Path file, String expected) throws IOException {
		assertTrue("Missing file " + file, Files.isRegularFile(file));
		assertEquals(expected, Files.readString(file));
	}

	/**
	 * Create a zip file from alternating entry names and contents.
	 */
	private InputStream zip(String... namesAndContents) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			for (int i = 0; i < namesAndContents.length; i += 2) {
				zip.putNextEntry(new ZipEntry(namesAndContents[i]));
				zip.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}

		return new ByteArrayInputStream(out.toByteArray());
	}
}
