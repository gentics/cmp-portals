package com.gentics.mesh.portal.gis;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gentics.mesh.parameter.ImageManipulationParameters;
import com.gentics.mesh.parameter.ParameterProvider;
import com.gentics.mesh.parameter.image.CropMode;
import com.gentics.mesh.parameter.image.ImageRect;
import com.gentics.mesh.portal.AbstractPortalTest;
import com.gentics.mesh.portal.client.MeshRestClientProvider;
import com.gentics.mesh.rest.client.MeshRequest;
import com.gentics.mesh.rest.client.MeshResponse;
import com.gentics.mesh.rest.client.MeshWebrootResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.Single;
import io.vertx.ext.web.RoutingContext;
import org.junit.Test;

public class CroppingGisHandlerTest extends AbstractPortalTest {

	@Test
	public void testRerouting() {
		MeshRestClientProvider meshRestClientProviderMock = mock(MeshRestClientProvider.class);
		when(meshRestClientProviderMock.get(any())).thenReturn(clientMock);
		String width = "200";
		String height = "300";
		String startx = "10";
		String starty = "20";
		String cropWidth = "100";
		String cropHeight = "120";
		String mode = "smart";
		String path = "image.jpg";

		CroppingGisHandler handler = new CroppingGisHandler(meshRestClientProviderMock, config, metricsServiceMock);
		String url = "/GenticsImageStore/" + width + "/" + height + "/cropandresize/" + mode + "/" + startx + "/" + starty + "/" + cropWidth + "/"
			+ cropHeight + "/" + path;

		RoutingContext rc = mockRoutingContext(url, "");
		when(rc.request().path()).thenReturn(url);
		when(rc.pathParam("param0")).thenReturn(width);
		when(rc.pathParam("param1")).thenReturn(height);
		when(rc.pathParam("param2")).thenReturn(mode);
		when(rc.pathParam("param3")).thenReturn(startx);
		when(rc.pathParam("param4")).thenReturn(starty);
		when(rc.pathParam("param5")).thenReturn(cropWidth);
		when(rc.pathParam("param6")).thenReturn(cropHeight);
		when(rc.pathParam("param7")).thenReturn(path);
		handler.reroute(rc);

		verify(rc).put(AbstractGisHandler.GIS_HANDLER, CroppingGisHandler.class.getName());
		verify(rc).put(AbstractGisHandler.WIDTH_PARAM, width);
		verify(rc).put(AbstractGisHandler.HEIGHT_PARAM, height);
		verify(rc).put(AbstractGisHandler.MODE_PARAM, mode);
		verify(rc).put(CroppingGisHandler.STARTX_PARAM, startx);
		verify(rc).put(CroppingGisHandler.STARTY_PARAM, starty);
		verify(rc).put(CroppingGisHandler.CROPWIDTH_PARAM, cropWidth);
		verify(rc).put(CroppingGisHandler.CROPHEIGHT_PARAM, cropHeight);
		verify(rc).reroute("/" + path);
	}

	@Test
	public void testHandling()  {
		MeshRestClientProvider meshRestClientProviderMock = mock(MeshRestClientProvider.class);
		when(meshRestClientProviderMock.get(any())).thenReturn(clientMock);
		String width = "200";
		String height = "300";
		String startx = "10";
		String starty = "20";
		String cropWidth = "100";
		String cropHeight = "120";
		String mode = "smart";
		String path = "image.jpg";

		CroppingGisHandler handler = new CroppingGisHandler(meshRestClientProviderMock, config, metricsServiceMock);

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
			assertEquals("Cropping actions must always include the rect parameter.", CropMode.RECT, param.getCropMode());

			ImageRect rect = param.getRect();
			assertEquals("The crop width did not match up with the GIS url.", Integer.valueOf(cropWidth).intValue(), rect.getWidth());
			assertEquals("The crop height did not match up with the GIS url.", Integer.valueOf(cropHeight).intValue(), rect.getHeight());
			assertEquals("The crop startx did not match up with the GIS url.", Integer.valueOf(startx).intValue(), rect.getStartX());
			assertEquals("The crop starty did not match up with the GIS url.", Integer.valueOf(starty).intValue(), rect.getStartY());
			return requestMock;
		});

		RoutingContext rc = mockRoutingContext("/" + path, "");
		when(rc.request().path()).thenReturn("/" + path);
		when(rc.get(AbstractGisHandler.GIS_HANDLER)).thenReturn(CroppingGisHandler.class.getName());
		when(rc.get(AbstractGisHandler.WIDTH_PARAM)).thenReturn(width);
		when(rc.get(AbstractGisHandler.HEIGHT_PARAM)).thenReturn(height);
		when(rc.get(AbstractGisHandler.MODE_PARAM)).thenReturn(mode);
		when(rc.get(CroppingGisHandler.STARTX_PARAM)).thenReturn(startx);
		when(rc.get(CroppingGisHandler.STARTY_PARAM)).thenReturn(starty);
		when(rc.get(CroppingGisHandler.CROPWIDTH_PARAM)).thenReturn(cropWidth);
		when(rc.get(CroppingGisHandler.CROPHEIGHT_PARAM)).thenReturn(cropHeight);
		handler.handle(rc);

		verify(rc.response()).setStatusCode(404);
	}
}
