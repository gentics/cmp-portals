package com.gentics.mesh.portal.branch;

import static com.gentics.mesh.core.rest.MeshEvent.BRANCH_CREATED;
import static com.gentics.mesh.core.rest.MeshEvent.BRANCH_DELETED;
import static com.gentics.mesh.core.rest.MeshEvent.BRANCH_UPDATED;
import static com.gentics.mesh.core.rest.MeshEvent.STARTUP;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gentics.mesh.core.rest.MeshEvent;
import com.gentics.mesh.core.rest.branch.BranchListResponse;
import com.gentics.mesh.core.rest.branch.BranchResponse;
import com.gentics.mesh.portal.api.branch.BranchProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.rest.client.MeshRestClient;
import com.gentics.mesh.rest.client.MeshRestClientUtil;
import com.gentics.mesh.rest.client.MeshWebsocket;

import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.functions.Predicate;

/**
 * Default implementation of {@link BranchProvider}.
 */
@Singleton
public class BranchProviderImpl implements BranchProvider {
	private static final Logger log = LoggerFactory.getLogger(BranchProviderImpl.class);

	private final String project;
	private final MeshRestClient meshClient;
	private final MeshWebsocket eventbus;

	/** A map from project names to branch lists. */
	private Observable<BranchResponse> branchList = Observable.fromArray();

	@Inject
	public BranchProviderImpl(PortalConfig portalConfig, MeshRestClient meshClient, MeshWebsocket eventbus) {
		this.project = portalConfig.getMeshProject();
		this.meshClient = meshClient;
		this.eventbus = eventbus;

		registerEventHandler();
	}

	/**
	 * Register event handlers for branch related events in Mesh.
	 */
	private void registerEventHandler() {
		updateBranchInfo();

		MeshEvent[] events = { BRANCH_CREATED, BRANCH_DELETED, BRANCH_UPDATED, STARTUP };

		eventbus.registerEvents(events);
		Observable.merge(
			eventbus.connections(),
			eventbus.events().filter(MeshRestClientUtil.isOneOf(events))
		).subscribe(ignore -> updateBranchInfo());
	}

	/**
	 * Update the branch info.
	 * @return 
	 */
	private Observable<BranchResponse> updateBranchInfo() {
		log.trace("Refreshing branch info");

		branchList = meshClient.findBranches(project).toSingle()
			.map(BranchListResponse::getData)
			.onErrorReturnItem(List.of())
			.flatMapObservable(Observable::fromIterable)
			.cache();
		branchList.subscribe();

		return branchList;
	}

	@Override
	public Maybe<BranchResponse> findBranch(Predicate<? super BranchResponse> filter) {
		return branchList.isEmpty()
				.map(empty -> empty ? updateBranchInfo() : branchList)
				.flatMapMaybe(branches -> branches.filter(filter).firstElement());
	}

	@Override
	public Observable<BranchResponse> getBranches() {
		return branchList.isEmpty().flatMapObservable(empty -> empty ? updateBranchInfo() : branchList);
	}
}
