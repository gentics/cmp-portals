package com.gentics.mesh.portal.server;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP router between the portal and Mesh, which sends websocket connections directly to Mesh, and all other
 * connections to the {@link PortalTestContext#meshProxy() Mesh proxy}.
 *
 * <p>
 *     This keeps the websocket to Mesh alive (and Mesh events arriving in the portal), while the proxy simulates an
 *     unavailable Mesh for all other requests. The target is determined per connection by the first request line.
 * </p>
 */
public class MeshConnectionRouter implements Closeable {

	private static final Logger log = LoggerFactory.getLogger(MeshConnectionRouter.class);

	private static final String WEBSOCKET_REQUEST = "GET /api/v1/eventbus/websocket";

	private final ServerSocket serverSocket;
	private final String meshHost;
	private final int meshPort;
	private final int proxyPort;
	private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();

	/**
	 * Start the router on an ephemeral port.
	 *
	 * @param meshHost The Mesh host
	 * @param meshPort The Mesh port
	 * @param proxyPort The port of the Mesh proxy on localhost
	 * @throws IOException When the server socket cannot be opened
	 */
	public MeshConnectionRouter(String meshHost, int meshPort, int proxyPort) throws IOException {
		this.meshHost = meshHost;
		this.meshPort = meshPort;
		this.proxyPort = proxyPort;
		this.serverSocket = new ServerSocket(0);

		Thread acceptor = new Thread(this::acceptConnections, "mesh-connection-router");

		acceptor.setDaemon(true);
		acceptor.start();
	}

	/**
	 * Get the port the router listens on.
	 *
	 * @return The port the router listens on
	 */
	public int getPort() {
		return serverSocket.getLocalPort();
	}

	@Override
	public void close() throws IOException {
		serverSocket.close();

		for (Socket socket : sockets) {
			socket.close();
		}
	}

	private void acceptConnections() {
		while (!serverSocket.isClosed()) {
			try {
				Socket client = serverSocket.accept();

				sockets.add(client);
				startThread(() -> route(client));
			} catch (IOException e) {
				if (!serverSocket.isClosed()) {
					log.error("Could not accept connection", e);
				}
			}
		}
	}

	private void route(Socket client) {
		try {
			byte[] requestLine = readRequestLine(client.getInputStream());
			boolean websocket = new String(requestLine, StandardCharsets.ISO_8859_1).startsWith(WEBSOCKET_REQUEST);
			Socket target = websocket ? new Socket(meshHost, meshPort) : new Socket("localhost", proxyPort);

			sockets.add(target);
			if (websocket) {
				log.info("Routing websocket connection directly to Mesh");
			} else {
				log.debug("Routing connection to Mesh proxy");
			}

			target.getOutputStream().write(requestLine);
			startThread(() -> pipe(client, target));
			pipe(target, client);
		} catch (IOException e) {
			log.debug("Routed connection failed: {}", e.getMessage());
			closeQuietly(client);
		}
	}

	/**
	 * Read the bytes up to and including the first line feed.
	 */
	private byte[] readRequestLine(InputStream in) throws IOException {
		ByteArrayOutputStream line = new ByteArrayOutputStream();
		int b;

		while ((b = in.read()) >= 0) {
			line.write(b);

			if (b == '\n') {
				break;
			}
		}

		return line.toByteArray();
	}

	private void pipe(Socket from, Socket to) {
		byte[] buffer = new byte[8192];

		try {
			InputStream in = from.getInputStream();
			OutputStream out = to.getOutputStream();
			int read;

			while ((read = in.read(buffer)) >= 0) {
				out.write(buffer, 0, read);
				out.flush();
			}
		} catch (IOException e) {
			log.trace("Pipe closed: {}", e.getMessage());
		} finally {
			closeQuietly(from);
			closeQuietly(to);
		}
	}

	private void startThread(Runnable runnable) {
		Thread thread = new Thread(runnable, "mesh-connection-router-pipe");

		thread.setDaemon(true);
		thread.start();
	}

	private void closeQuietly(Socket socket) {
		sockets.remove(socket);

		try {
			socket.close();
		} catch (IOException e) {
			// Nothing to do
		}
	}
}
