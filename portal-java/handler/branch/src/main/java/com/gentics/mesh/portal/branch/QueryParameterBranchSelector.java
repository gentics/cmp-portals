package com.gentics.mesh.portal.branch;

import java.util.List;

import javax.inject.Inject;

import com.gentics.mesh.core.rest.branch.BranchResponse;
import com.gentics.mesh.portal.api.branch.BranchProvider;
import com.gentics.mesh.portal.config.PortalConfig;

import io.reactivex.functions.Predicate;
import io.vertx.ext.web.RoutingContext;

public class QueryParameterBranchSelector extends AbstractBranchSelectorHandler {
	public final static String CHANNEL_UUID_PARAM = "channelUuid";

	@Inject
	public QueryParameterBranchSelector(PortalConfig config, BranchProvider branchProvider) {
		super(config, branchProvider);
	}

	@Override
	protected Predicate<? super BranchResponse> filter(RoutingContext rc) {
		List<String> channelUuidValues = rc.queryParam(CHANNEL_UUID_PARAM);
		String channelUuid = (channelUuidValues != null && !channelUuidValues.isEmpty()) ? channelUuidValues.get(0) : null;

		return branch -> matchesChannelUuid(branch, channelUuid) && matchesImplementationVersion(branch);
	}
}
