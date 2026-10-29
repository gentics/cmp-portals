package com.gentics.mesh.portal.branch;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.gentics.mesh.core.rest.branch.BranchResponse;
import com.gentics.mesh.portal.api.branch.BranchProvider;
import com.gentics.mesh.portal.config.PortalConfig;

import io.reactivex.functions.Predicate;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

/**
 * Branch selector handler, that will choose the branch based on the requested hostname (and implementation version if configured)
 */
public class HostnameBranchSelector extends AbstractBranchSelectorHandler {
	@Inject
	public HostnameBranchSelector(PortalConfig config, BranchProvider branchProvider) {
		super(config, branchProvider);
	}

	@Override
	protected Predicate<? super BranchResponse> filter(RoutingContext rc) {
		String host = rc.request().getHeader(HttpHeaders.HOST);

		return branch -> {
			return StringUtils.equalsIgnoreCase(stripPort(branch.getHostname()), stripPort(host)) &&
					matchesImplementationVersion(branch);
		};
	}

	/**
	 * If the host also contains a port, remove it
	 * @param host host (probably with port)
	 * @return host without port
	 */
	protected static String stripPort(String host) {
		if (host == null) {
			return null;
		} else if (host.contains(":")) {
			return host.substring(0, host.indexOf(':'));
		} else {
			return host;
		}
	}
}
