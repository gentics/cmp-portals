package com.gentics.mesh.portal.queries;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.gentics.mesh.portal.AbstractPortalTest;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class QueryStoreTest extends AbstractPortalTest {

	protected String queryPath;
	protected static String expectedQuery;

	@BeforeClass
	public static void setupClass() throws IOException {
		expectedQuery = new String(Files.readAllBytes(
				Paths.get("src","test","resources", "expected", "expectedQuery.graphql")
			)).trim();
	}

	@Before
	@Override
	public void setup() throws Exception {
		super.setup();
		queryPath = config.getQueryPath();
	}

	private Path mockFilesystem(Configuration configuration, Map<String, String> files) throws IOException {
		FileSystem fs = Jimfs.newFileSystem(configuration);
		Path rootQueryPath = Files.createDirectories(fs.getPath(config.getQueryPath()));

		for (Entry<String, String> file: files.entrySet()) {
			Path path = fs.getPath(rootQueryPath.toAbsolutePath().toString(), file.getKey());
			Files.createDirectories(path.getParent());
			assertNotNull(Files.write(
					path,
					file.getValue().getBytes(StandardCharsets.UTF_8),
					StandardOpenOption.CREATE));
		}
		return rootQueryPath;
	}

	private Path setupFilesystem(Configuration configuration) throws IOException {
		Path resourceDirectory = Paths.get("src","test","resources", "querystore");
		Map<String, String> files = Files
				.walk(resourceDirectory)
				.filter(Files::isRegularFile)
				.map(file -> {
					try {
						return Pair.of(
								resourceDirectory.relativize(file).toString(),
								new String(Files.readAllBytes(file))
							);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				})
				.collect(Collectors.toMap(Pair::getKey, Pair::getValue));
		return mockFilesystem(configuration, files);
	}

	private void testFilesystem(Configuration configuration) throws IOException {
		Path rootQueryPath = setupFilesystem(configuration);
		QueryStoreImpl queryStore = new QueryStoreImpl(config, rootQueryPath.toAbsolutePath(), config.getMeshProject());
		String query = queryStore.loadQuery(QueryType.GraphQL, "query");
		assertEquals("Loaded query differs from expected one!", expectedQuery, query.trim());
	}

	@Test
	public void testUnix() throws IOException {
		testFilesystem(Configuration.unix());
	}

	@Test
	public void testWindows() throws IOException {
		testFilesystem(Configuration.windows());
	}

	@Test
	public void testMacos() throws IOException {
		testFilesystem(Configuration.osX());
	}
}
