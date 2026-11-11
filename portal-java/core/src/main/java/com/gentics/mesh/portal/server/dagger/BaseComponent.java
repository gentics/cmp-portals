package com.gentics.mesh.portal.server.dagger;

import com.gentics.mesh.portal.api.queries.QueryStore;
import com.gentics.mesh.portal.api.server.BootstrapInitializer;
import com.gentics.mesh.portal.api.server.PortalInfo;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.implementation.PortalImplementationService;
import com.gentics.mesh.rest.client.MeshRestClient;
import io.vertx.core.Vertx;
import okhttp3.OkHttpClient;

public interface BaseComponent {

	Vertx vertx();

	OkHttpClient okClient();

	io.vertx.reactivex.core.Vertx rxVertx();

	MeshRestClient meshRestClient();

	PortalConfig portalConfig();

	PortalInfo portalInfo();

	BootstrapInitializer boot();

	QueryStore queryStore();

	PortalImplementationService portalImplementation();
}
