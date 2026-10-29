package com.gentics.mesh.portal.gis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;

import com.gentics.mesh.parameter.ImageManipulationParameters;
import com.gentics.mesh.portal.AbstractPortalTest;
import com.gentics.mesh.portal.TestRoutingContext;
import com.gentics.mesh.portal.client.MeshRestClientProvider;
import com.gentics.mesh.rest.client.MeshRequest;
import com.gentics.mesh.rest.client.MeshResponse;
import com.gentics.mesh.rest.client.MeshWebrootResponse;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.Single;

public class GisHandlerTest extends AbstractPortalTest {

	@Test
	public void testRerouting() throws InterruptedException {
		MeshRestClientProvider meshRestClientProviderMock = mock(MeshRestClientProvider.class);
		when(meshRestClientProviderMock.get(any())).thenReturn(clientMock);
		String width = "200";
		String height = "300";
		String mode = "smart";
		String path = "image.jpg";

		GisHandler handler = new GisHandler(meshRestClientProviderMock, config, metricsServiceMock);
		String url = "/GenticsImageStore/" + width + "/" + height + "/" + mode + "/" + path;

		TestRoutingContext rc = mockRoutingContext(url, "");
		when(rc.request().path()).thenReturn(url);
		when(rc.pathParam("param0")).thenReturn(width);
		when(rc.pathParam("param1")).thenReturn(height);
		when(rc.pathParam("param2")).thenReturn(mode);
		when(rc.pathParam("param3")).thenReturn(path);
		handler.reroute(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();

		verify(rc).put(AbstractGisHandler.GIS_HANDLER, GisHandler.class.getName());
		verify(rc).put(AbstractGisHandler.WIDTH_PARAM, width);
		verify(rc).put(AbstractGisHandler.HEIGHT_PARAM, height);
		verify(rc).put(AbstractGisHandler.MODE_PARAM, mode);
		verify(rc).reroute("/" + path);
	}

	@Test
	public void testHandling() throws InterruptedException {
		testHandlingPath("image.jpg");
	}

	@Test
	public void testHandlingSpecialName() throws InterruptedException {
		testHandlingPath("image-äüöß.jpg");
	}

	@Test
	public void testHandlingEscapedName() throws InterruptedException {
		testHandlingPath("image%C3%96%C3%BC.jpg");
	}

	// TODO: Check if the handler actually did its work as expected. Maybe verify webroot requests on the Mesh client.
	protected void testHandlingPath(String path) throws InterruptedException {
		MeshRestClientProvider meshRestClientProviderMock = mock(MeshRestClientProvider.class);
		when(meshRestClientProviderMock.get(any())).thenReturn(clientMock);
		String width = "200";
		String height = "300";
		String mode = "smart";

		GisHandler handler = new GisHandler(meshRestClientProviderMock, config, metricsServiceMock);

		@SuppressWarnings("unchecked")
		MeshRequest<MeshWebrootResponse> requestMock = mock(MeshRequest.class);
		@SuppressWarnings("unchecked")
		MeshResponse<MeshWebrootResponse> meshResponse = mock(MeshResponse.class);

		when(meshResponse.getStatusCode()).thenReturn(HttpResponseStatus.NOT_FOUND.code());

		MeshWebrootResponse webrootResponse = mock(MeshWebrootResponse.class);

		when(meshResponse.getBody()).thenReturn(webrootResponse);
		when(requestMock.getResponse()).thenReturn(Single.just(meshResponse));
		when(clientMock.webroot(anyString(), anyString(), any(), any())).thenAnswer(invocation -> {
			String projectName = invocation.getArgument(0, String.class);
			assertEquals("The used project name was not correct.", config.getMeshProject(), projectName);

			String webRootPath = invocation.getArgument(1, String.class);
			assertEquals("The image webroot path did not match up with the gis path.", "/" + path, webRootPath);

			ImageManipulationParameters param = invocation.getArgument(2, ImageManipulationParameters.class);
			assertNull("Resizing without cropping", param.getCropMode());
			return requestMock;
		});

		TestRoutingContext rc = mockRoutingContext("/" + path, "");
		when(rc.request().path()).thenReturn("/" + path);
		when(rc.get(AbstractGisHandler.GIS_HANDLER)).thenReturn(GisHandler.class.getName());
		when(rc.get(AbstractGisHandler.WIDTH_PARAM)).thenReturn(width);
		when(rc.get(AbstractGisHandler.HEIGHT_PARAM)).thenReturn(height);
		when(rc.get(AbstractGisHandler.MODE_PARAM)).thenReturn(mode);
		handler.handle(rc);
		assertThat(rc.await()).as("Routing Context was ended").isTrue();

		verify(rc.response()).setStatusCode(404);
	}
}
