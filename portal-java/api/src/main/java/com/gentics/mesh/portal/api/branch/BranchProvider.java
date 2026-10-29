package com.gentics.mesh.portal.api.branch;

import com.gentics.mesh.core.rest.branch.BranchResponse;

import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.functions.Predicate;

/**
 * Branch provider that can be used to get branch info.
 *
 * <p>
 * If an instance of the provider is created, it will listen to the Mesh eventbus for branch related events (create, delete, update)
 * And will refresh the internally stored branch information.
 * </p>
 *
 * <p>
 * In addition to the configured Mesh project, all the branches for searchable projects will be fetched.
 * </p>
 */
public interface BranchProvider {

	String IMPLEMENTATION_VERSION_TAGFAMILY = "gcmsImplementationVersion";
	String CHANNEL_UUID_TAGFAMILY = "gcmsChannelUuid";

	/**
	 * Find first branch in the configured Mesh project matching the filter.
	 *
	 * @param filter The filter to apply
	 * @return A <code>Maybe</code> containing the first matching branch if there is one
	 */
	Maybe<BranchResponse> findBranch(Predicate<? super BranchResponse> filter);

	/**
	 * Get all branches in the configured Mesh project.
	 *
	 * @return An <code>Observable</code> with all the branches for the configured Mesh project
	 */
	Observable<BranchResponse> getBranches();

}
