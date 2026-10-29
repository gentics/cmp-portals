package com.gentics.mesh.portal.server.dagger;

import javax.inject.Qualifier;

import okhttp3.OkHttpClient;

/**
 * Qualifier to mark the injected {@link OkHttpClient} as not following redirects
 */
@Qualifier
public @interface NoRedirect {
}
