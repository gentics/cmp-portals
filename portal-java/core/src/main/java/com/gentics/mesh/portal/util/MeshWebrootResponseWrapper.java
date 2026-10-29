package com.gentics.mesh.portal.util;

import java.io.Closeable;
import java.util.Optional;

import com.gentics.mesh.rest.client.MeshBinaryResponse;
import com.gentics.mesh.rest.client.MeshWebrootFieldResponse;
import com.gentics.mesh.rest.client.MeshWebrootResponse;

/**
 * Wrapper for {@link MeshWebrootResponse} and {@link MeshWebrootFieldResponse}
 * classes, exposing common methods used by file download handling.
 * @implNote It is important to close the response by calling {@link #close()} when the response is no longer needed. Failing to do so might lead to a connection leak.
 */
public class MeshWebrootResponseWrapper implements Closeable {

	private final MeshWebrootResponse webrootResponse;
	private final MeshWebrootFieldResponse webrootFieldResponse;

	private MeshWebrootResponseWrapper(MeshWebrootResponse webrootResponse) {
		this.webrootResponse = webrootResponse;
		this.webrootFieldResponse = null;
	}

	private MeshWebrootResponseWrapper(MeshWebrootFieldResponse webrootFieldResponse) {
		this.webrootResponse = null;
		this.webrootFieldResponse = webrootFieldResponse;
	}

	/**
	 * Create wrapped instance of {@link MeshWebrootResponse} or
	 * {@link MeshWebrootFieldResponse}.
	 *
	 * <p>
	 *     If the given object is neither of those, this method returns
	 *     {@code null}.
	 * </p>
	 *
	 * @param response The object to wrap
	 * @return A wrapped instance of the given response
	 */
	public static MeshWebrootResponseWrapper wrap(Object response) {
		if (response instanceof MeshWebrootResponse) {
			return new MeshWebrootResponseWrapper((MeshWebrootResponse) response);
		}

		if (response instanceof MeshWebrootFieldResponse) {
			return new MeshWebrootResponseWrapper((MeshWebrootFieldResponse) response);
		}

		return null;
	}

	/**
	 * Whether this response is binary data.
	 *
	 * @return Whether this response is binary data
	 */
	public boolean isBinary() {
		return webrootResponse != null
			? webrootResponse.isBinary()
			: webrootFieldResponse.isBinary();
	}

	/**
	 * Get the binary response.
	 *
	 * @return The binary response or {@code null} if this response is
	 * 		not binary data
	 */
	public MeshBinaryResponse getBinaryResponse() {
		return webrootResponse != null
			? webrootResponse.getBinaryResponse()
			: webrootFieldResponse.getBinaryResponse();
	}

	/**
	 * The node UUID for the loaded content.
	 *
	 * @return The node UUID for the loaded content
	 */
	public String getNodeUuid() {
		return webrootResponse != null
			? webrootResponse.getNodeUuid()
			: webrootFieldResponse.getNodeUuid();

	}

	/**
	 * The response as a JSON string.
	 *
	 * @return The response as a JSON string, or {@code null} if the response
	 * 		is binary data
	 */
	public String getResponseAsJsonString() {
		return webrootResponse != null
			? webrootResponse.getNodeResponse().toJson()
			: webrootFieldResponse.getResponseAsJsonString();
	}

	@Override
	public void close() {
		Optional.ofNullable(webrootResponse).ifPresent(MeshWebrootResponse::close);
		Optional.ofNullable(webrootFieldResponse).ifPresent(MeshWebrootFieldResponse::close);
	}
}
