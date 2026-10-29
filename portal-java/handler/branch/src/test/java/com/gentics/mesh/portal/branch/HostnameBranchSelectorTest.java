package com.gentics.mesh.portal.branch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.gentics.mesh.core.rest.branch.BranchResponse;
import com.gentics.mesh.portal.AbstractPortalTest;
import com.gentics.mesh.portal.TestRoutingContext;
import com.gentics.mesh.portal.api.branch.BranchProvider;
import com.gentics.mesh.portal.context.PortalRoutingContext;

/**
 * Test cases for {@link HostnameBranchSelector}
 */
public class HostnameBranchSelectorTest extends AbstractPortalTest {

	/**
	 * Clean the config
	 */
	@Before
	public void cleanConfig() {
		config.setImplementationVersion(null);
	}

	/**
	 * Test filtering by hostname
	 * @throws Exception
	 */
	@Test
	public void testFilter() throws Exception {
		BranchResponse branch1 = branch("branch1", "host1");
		BranchResponse branch2 = branch("branch2", "host2");
		BranchResponse branch3 = branch("branch3", "host3");

		BranchProvider branchProvider = mockBranchProvider(branch1, branch2, branch3);
		HostnameBranchSelector branchSelector = new HostnameBranchSelector(config, branchProvider);

		TestRoutingContext rc = mockRoutingContext("", "");
		mockHost(rc, "host2");

		branchSelector.handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();
		verify(rc).put(PortalRoutingContext.BRANCH_KEY, branch2);
		verify(rc).next();
	}

	/**
	 * Test that ports (set in branch) are ignored while filtering for hostname
	 * @throws InterruptedException 
	 */
	@Test
	public void testIgnorePort1() throws InterruptedException {
		BranchResponse branch1 = branch("branch1", "host1:8080");
		BranchResponse branch2 = branch("branch2", "host2:8080");
		BranchResponse branch3 = branch("branch3", "host3:8080");

		BranchProvider branchProvider = mockBranchProvider(branch1, branch2, branch3);
		HostnameBranchSelector branchSelector = new HostnameBranchSelector(config, branchProvider);

		TestRoutingContext rc = mockRoutingContext("", "");
		mockHost(rc, "host2");

		branchSelector.handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();
		verify(rc).put(PortalRoutingContext.BRANCH_KEY, branch2);
		verify(rc).next();
	}

	/**
	 * Test that ports (set in request) are ignored while filtering for hostname
	 * @throws InterruptedException 
	 */
	@Test
	public void testIgnorePort2() throws InterruptedException {
		BranchResponse branch1 = branch("branch1", "host1");
		BranchResponse branch2 = branch("branch2", "host2");
		BranchResponse branch3 = branch("branch3", "host3");

		BranchProvider branchProvider = mockBranchProvider(branch1, branch2, branch3);
		HostnameBranchSelector branchSelector = new HostnameBranchSelector(config, branchProvider);

		TestRoutingContext rc = mockRoutingContext("", "");
		mockHost(rc, "host2:8080");

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
		BranchResponse branch1 = branch("branch1", "host1");
		BranchResponse branch2 = branch("branch2", "host2");
		BranchResponse branch3 = branch("branch3", "host3");

		BranchProvider branchProvider = mockBranchProvider(branch1, branch2, branch3);
		HostnameBranchSelector branchSelector = new HostnameBranchSelector(config, branchProvider);

		TestRoutingContext rc = mockRoutingContext("", "");
		mockHost(rc, "otherhost");

		branchSelector.handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();
		verify(rc, never()).put(Mockito.anyString(), Mockito.any());
		verify(rc).next();
	}

	/**
	 * Test filtering for hostname and implementation version
	 * @throws InterruptedException 
	 */
	@Test
	public void testImplementationVersion() throws InterruptedException {
		BranchResponse branch1 = branch("branch1", "host", tag(BranchProvider.IMPLEMENTATION_VERSION_TAGFAMILY, "version1"));
		BranchResponse branch2 = branch("branch2", "host", tag(BranchProvider.IMPLEMENTATION_VERSION_TAGFAMILY, "version2"));
		BranchResponse branch3 = branch("branch3", "host", tag(BranchProvider.IMPLEMENTATION_VERSION_TAGFAMILY, "version3"));

		BranchProvider branchProvider = mockBranchProvider(branch1, branch2, branch3);
		HostnameBranchSelector branchSelector = new HostnameBranchSelector(config, branchProvider);

		config.setImplementationVersion("version2");
		TestRoutingContext rc = mockRoutingContext("", "");
		mockHost(rc, "host");

		branchSelector.handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();
		verify(rc).put(PortalRoutingContext.BRANCH_KEY, branch2);
		verify(rc).next();
	}
}
