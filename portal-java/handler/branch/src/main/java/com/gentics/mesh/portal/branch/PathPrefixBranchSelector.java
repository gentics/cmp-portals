package com.gentics.mesh.portal.branch;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.gentics.mesh.core.rest.branch.BranchResponse;
import com.gentics.mesh.portal.api.branch.BranchProvider;
import com.gentics.mesh.portal.config.PortalConfig;

import io.reactivex.functions.Predicate;
import io.vertx.ext.web.RoutingContext;

/**
 * Branch selector handler, that will choose the branch based on the path prefix (and implementation version if configured)
 */
public class PathPrefixBranchSelector extends AbstractBranchSelectorHandler {
	@Inject
	public PathPrefixBranchSelector(PortalConfig config, BranchProvider branchProvider) {
		super(config, branchProvider);
	}

	@Override
	protected Predicate<? super BranchResponse> filter(RoutingContext rc) {
		String path = rc.request().path();
		return branch -> {
			return StringUtils.startsWithAny(path, branch.getPathPrefix(), "/" + branch.getPathPrefix()) && matchesImplementationVersion(branch);
		};
	}
}
