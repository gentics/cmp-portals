package com.gentics.mesh.portal.server;

import com.gentics.mesh.portal.api.server.PortalInfo;
import com.gentics.mesh.portal.api.server.PortalState;

/**
 * Dummy {@code PortalInfo} implementation that is always in the
 * {@link PortalState#READY} state.
 */
public class DummyPortalInfo implements PortalInfo {

	@Override
	public PortalState getState() {
		return PortalState.READY;
	}

	@Override
	public void setState(PortalState state) {
	}

	@Override
	public void checkState(Throwable error) {
	}

	@Override
	public void resetErrors() {
	}

	@Override
	public void shutdown() {
	}
}
