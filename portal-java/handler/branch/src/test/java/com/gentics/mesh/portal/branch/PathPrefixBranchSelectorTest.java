package com.gentics.mesh.portal.branch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.gentics.mesh.core.rest.branch.BranchResponse;
import com.gentics.mesh.portal.AbstractPortalTest;
import com.gentics.mesh.portal.TestRoutingContext;
import com.gentics.mesh.portal.api.branch.BranchProvider;
import com.gentics.mesh.portal.context.PortalRoutingContext;

/**
 * Test cases for {@link PathPrefixBranchSelector}
 */
public class PathPrefixBranchSelectorTest extends AbstractPortalTest {

	/**
	 * Clean the config
	 */
	@Before
	public void cleanConfig() {
		config.setImplementationVersion(null);
	}

	/**
	 * Test filtering by path prefix
	 * @throws Exception
	 */
	@Test
	public void testFilter() throws Exception {
		BranchResponse branch1 = branch("branch1", "host", "/path1");
		BranchResponse branch2 = branch("branch2", "host", "/path2");
		BranchResponse branch3 = branch("branch3", "host", "/path3");

		BranchProvider branchProvider = mockBranchProvider(branch1, branch2, branch3);
		PathPrefixBranchSelector branchSelector = new PathPrefixBranchSelector(config, branchProvider);

		TestRoutingContext rc = mockRoutingContext("/path2/bla", "");
		when(rc.request().path()).thenReturn("/path2/bla");
		mockHost(rc, "host");

		branchSelector.handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();
		verify(rc).put(PortalRoutingContext.BRANCH_KEY, branch2);
		verify(rc).next();
	}

	/**
	 * Test not finding branch
	 * @throws Exception
	 */
	@Test
	public void testNotFound() throws Exception {
		BranchResponse branch1 = branch("branch1", "host", "/path1");
		BranchResponse branch2 = branch("branch2", "host", "/path2");
		BranchResponse branch3 = branch("branch3", "host", "/path3");

		BranchProvider branchProvider = mockBranchProvider(branch1, branch2, branch3);
		PathPrefixBranchSelector branchSelector = new PathPrefixBranchSelector(config, branchProvider);

		TestRoutingContext rc = mockRoutingContext("/otherpath/bla", "");
		when(rc.request().path()).thenReturn("/otherpath/bla");
		mockHost(rc, "host");

		branchSelector.handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();
		verify(rc, never()).put(Mockito.anyString(), Mockito.any());
		verify(rc).next();
	}

	/**
	 * Test filtering for path prefix and implementation version
	 * @throws Exception
	 */
	@Test
	public void testImplementationVersion() throws Exception {
		BranchResponse branch1 = branch("branch1", "host", "/path", tag(BranchProvider.IMPLEMENTATION_VERSION_TAGFAMILY, "version1"));
		BranchResponse branch2 = branch("branch2", "host", "/path", tag(BranchProvider.IMPLEMENTATION_VERSION_TAGFAMILY, "version2"));
		BranchResponse branch3 = branch("branch3", "host", "/path", tag(BranchProvider.IMPLEMENTATION_VERSION_TAGFAMILY, "version3"));

		BranchProvider branchProvider = mockBranchProvider(branch1, branch2, branch3);
		PathPrefixBranchSelector branchSelector = new PathPrefixBranchSelector(config, branchProvider);

		config.setImplementationVersion("version2");
		TestRoutingContext rc = mockRoutingContext("/path/bla", "");
		when(rc.request().path()).thenReturn("/path/bla");
		mockHost(rc, "host");

		branchSelector.handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();
		verify(rc).put(PortalRoutingContext.BRANCH_KEY, branch2);
		verify(rc).next();
	}
}
