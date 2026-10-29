package com.gentics.mesh.portal.branch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.Test;
import org.mockito.Mockito;

import com.gentics.mesh.core.rest.branch.BranchResponse;
import com.gentics.mesh.portal.AbstractPortalTest;
import com.gentics.mesh.portal.TestRoutingContext;
import com.gentics.mesh.portal.api.branch.BranchProvider;
import com.gentics.mesh.portal.context.PortalRoutingContext;

/**
 * Test cases for {@link QueryParameterBranchSelector}
 */
public class QueryParameterBranchSelectorTest extends AbstractPortalTest {

	/**
	 * Test filtering without query parameter
	 * @throws InterruptedException 
	 */
	@Test
	public void testFilterMaster() throws InterruptedException {
		String channel1 = "channel1";
		String channel2 = "channel2";
		BranchResponse branch1 = branch("branch1", "host1", tag(BranchProvider.CHANNEL_UUID_TAGFAMILY, channel1));
		BranchResponse branch2 = branch("branch2", "host2");
		BranchResponse branch3 = branch("branch3", "host3", tag(BranchProvider.CHANNEL_UUID_TAGFAMILY, channel2));

		BranchProvider branchProvider = mockBranchProvider(branch1, branch2, branch3);
		QueryParameterBranchSelector branchSelector = new QueryParameterBranchSelector(config, branchProvider);

		TestRoutingContext rc = mockRoutingContext("", "");
		mockHost(rc, "otherhost");

		branchSelector.handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();
		verify(rc).put(PortalRoutingContext.BRANCH_KEY, branch2);
		verify(rc).next();
	}

	/**
	 * Test filtering with query parameter
	 * @throws InterruptedException 
	 */
	@Test
	public void testFilterChannel() throws InterruptedException {
		String channel1 = "channel1";
		String channel2 = "channel2";
		BranchResponse branch1 = branch("branch1", "host1");
		BranchResponse branch2 = branch("branch2", "host2", tag(BranchProvider.CHANNEL_UUID_TAGFAMILY, channel1));
		BranchResponse branch3 = branch("branch3", "host3", tag(BranchProvider.CHANNEL_UUID_TAGFAMILY, channel2));

		BranchProvider branchProvider = mockBranchProvider(branch1, branch2, branch3);
		QueryParameterBranchSelector branchSelector = new QueryParameterBranchSelector(config, branchProvider);

		TestRoutingContext rc = mockRoutingContext("", "");
		mockHost(rc, "otherhost");
		when(rc.queryParam(QueryParameterBranchSelector.CHANNEL_UUID_PARAM)).thenReturn(Arrays.asList(channel1));

		branchSelector.handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();
		verify(rc).put(PortalRoutingContext.BRANCH_KEY, branch2);
		verify(rc).next();
	}

	/**
	 * Test not finding branch
	 * @throws InterruptedException 
	 */
	@Test
	public void testNotFound() throws InterruptedException {
		String channel1 = "channel1";
		String channel2 = "channel2";
		BranchResponse branch1 = branch("branch1", "host1", tag(BranchProvider.CHANNEL_UUID_TAGFAMILY, channel1));
		BranchResponse branch2 = branch("branch2", "host2");
		BranchResponse branch3 = branch("branch3", "host3", tag(BranchProvider.CHANNEL_UUID_TAGFAMILY, channel2));

		BranchProvider branchProvider = mockBranchProvider(branch1, branch2, branch3);
		QueryParameterBranchSelector branchSelector = new QueryParameterBranchSelector(config, branchProvider);

		TestRoutingContext rc = mockRoutingContext("", "");
		mockHost(rc, "otherhost");
		when(rc.queryParam(QueryParameterBranchSelector.CHANNEL_UUID_PARAM)).thenReturn(Arrays.asList("otherchannel"));

		branchSelector.handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();
		verify(rc, never()).put(Mockito.anyString(), Mockito.any());
		verify(rc).next();
	}
}
