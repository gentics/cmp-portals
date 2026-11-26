package com.gentics.mesh.portal.branch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;

import com.gentics.mesh.core.rest.branch.BranchListResponse;
import com.gentics.mesh.core.rest.branch.BranchResponse;
import com.gentics.mesh.portal.AbstractPortalTest;
import com.gentics.mesh.portal.api.branch.BranchProvider;
import com.gentics.mesh.rest.client.MeshRequest;
import com.gentics.mesh.rest.client.MeshWebsocket;
import io.reactivex.subjects.PublishSubject;
import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

/**
 * Test cases for the {@link BranchProviderImpl}
 */
public class BranchProviderTest extends AbstractPortalTest {

	/**
	 * Test getting branches
	 */
	@Test
	public void testGetBranches() {
		BranchResponse branch1 = new BranchResponse().setName("branch1");
		BranchResponse branch2 = new BranchResponse().setName("branch2");
		BranchResponse branch3 = new BranchResponse().setName("branch3");

		BranchProvider branchProvider = mockBranchProvider(branch1, branch2, branch3);

		branchProvider.getBranches().test().assertComplete().assertResult(branch1, branch2, branch3);
	}

	/**
	 * Test filtering branches
	 */
	@Test
	public void testFilterBranches() {
		BranchResponse branch1 = new BranchResponse().setName("branch1");
		BranchResponse branch2 = new BranchResponse().setName("branch2");
		BranchResponse branch3 = new BranchResponse().setName("branch3");

		BranchProvider branchProvider = mockBranchProvider(branch1, branch2, branch3);

		for (BranchResponse branch : Arrays.asList(branch1, branch2, branch3)) {
			branchProvider.findBranch(check -> StringUtils.equals(check.getName(), branch.getName())).test().assertComplete().assertResult(branch);
		}
	}

	/**
	 * Test filtering, when no branch can be found
	 */
	@Test
	public void testFilterNotFound() {
		BranchResponse branch1 = new BranchResponse().setName("branch1");
		BranchResponse branch2 = new BranchResponse().setName("branch2");
		BranchResponse branch3 = new BranchResponse().setName("branch3");

		BranchProvider branchProvider = mockBranchProvider(branch1, branch2, branch3);

		branchProvider.findBranch(check -> false).test().assertComplete().assertNoValues();
	}

	/**
	 * Test that branches can be loaded again after Mesh was temporarily unavailable while the branch info was
	 * refreshed (SUP-20246).
	 */
	@Test
	public void testGetBranchesAfterMeshUnavailable() {
		BranchResponse branch1 = new BranchResponse().setName("branch1");
		BranchListResponse response = new BranchListResponse();
		response.add(branch1);

		MeshRequest<BranchListResponse> successRequest = mockRequest(response);
		MeshRequest<BranchListResponse> failedRequest = mockRequest(new IOException("I/O Error in GET /api/v1/project/branches : ConnectException"));

		// Mesh is available on start-up, unavailable when the websocket reconnects, and available afterwards.
		when(clientMock.findBranches(any(String.class))).thenReturn(successRequest, failedRequest, successRequest);

		PublishSubject<Object> connections = PublishSubject.create();
		MeshWebsocket eventbus = mockEventbus();
		when(eventbus.connections()).thenReturn(connections);

		BranchProvider branchProvider = new BranchProviderImpl(config, clientMock, eventbus);

		branchProvider.getBranches().test().assertComplete().assertResult(branch1);

		connections.onNext(new Object());

		// Mesh is available again.
		branchProvider.getBranches().test().assertComplete().assertResult(branch1);
	}

	/**
	 * Create a branch provider instance returning the given branches
	 * @param branches branches
	 * @return branch provider instance
	 */
	protected BranchProvider mockBranchProvider(BranchResponse...branches) {
		BranchListResponse response = new BranchListResponse();
		for (BranchResponse branch : branches) {
			response.add(branch);
		}

		MeshRequest<BranchListResponse> requestMock = mockRequest(response);

		when(clientMock.findBranches(any(String.class))).thenReturn(requestMock);

		return new BranchProviderImpl(config, clientMock, mockEventbus());
	}
}
