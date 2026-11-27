package com.gentics.mesh.portal.integration.tracing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal HTTP/1.1 client on a plain socket.
 *
 * <p>
 *     The OpenTelemetry agent does not instrument plain sockets, so requests sent with this client carry no
 *     {@code traceparent} header. Requests sent with an instrumented client (e.g. the Vert.x HTTP client of the test)
 *     would carry the context of a client span of the test JVM, which is not sampled, so the request to the portal
 *     would not be sampled either.
 * </p>
 */
public final class RawHttp {
	private RawHttp() {
	}

	/**
	 * Send a GET request
	 * @param port port on localhost
	 * @param path path (with query string)
	 * @param headers additional request headers as name/value pairs
	 * @return response
	 */
	public static Response get(int port, String path, String... headers) {
		return request(port, "GET", path, null, headers);
	}

	/**
	 * Send a request and read the whole response
	 * @param port port on localhost
	 * @param method request method
	 * @param path path (with query string)
	 * @param body request body, may be null
	 * @param headers additional request headers as name/value pairs
	 * @return response
	 */
	public static Response request(int port, String method, String path, String body, String... headers) {
		try (Socket socket = new Socket("localhost", port)) {
			socket.setSoTimeout(30_000);

			StringBuilder request = new StringBuilder();
			request.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
			request.append("Host: localhost:").append(port).append("\r\n");
			request.append("Connection: close\r\n");
			for (int i = 0; i + 1 < headers.length; i += 2) {
				request.append(headers[i]).append(": ").append(headers[i + 1]).append("\r\n");
			}
			byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
			if (body != null) {
				request.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
			}
			request.append("\r\n");

			OutputStream out = socket.getOutputStream();
			out.write(request.toString().getBytes(StandardCharsets.UTF_8));
			out.write(bodyBytes);
			out.flush();

			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
			String statusLine = in.readLine();
			if (statusLine == null) {
				throw new IOException("No response for " + method + " " + path);
			}
			int status = Integer.parseInt(statusLine.split(" ")[1]);

			Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
			String line;
			while ((line = in.readLine()) != null && !line.isEmpty()) {
				int index = line.indexOf(':');
				if (index > 0) {
					responseHeaders.computeIfAbsent(line.substring(0, index).trim().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
						.add(line.substring(index + 1).trim());
				}
			}

			// read the body until the server closes the connection
			char[] buffer = new char[8192];
			while (in.read(buffer) >= 0) {
			}

			return new Response(status, responseHeaders);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Response status and headers
	 * @param status status code
	 * @param headers headers (names in lower case)
	 */
	public record Response(int status, Map<String, List<String>> headers) {
		/**
		 * Get the first value of the header
		 * @param name header name
		 * @return value or null
		 */
		public String header(String name) {
			List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
			return values == null || values.isEmpty() ? null : values.get(0);
		}

		/**
		 * Get all values of the header
		 * @param name header name
		 * @return values (may be empty)
		 */
		public List<String> headers(String name) {
			return headers.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
		}
	}
}
