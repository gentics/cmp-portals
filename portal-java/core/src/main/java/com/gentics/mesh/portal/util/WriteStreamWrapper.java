package com.gentics.mesh.portal.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.WriteStream;

/**
 * Implementation of a {@link WriteStream} for instances of {@link Buffer}, which allows consuming the
 * data with an {@link InputStream} (via {@link #createInputStream()}).
 */
public class WriteStreamWrapper implements WriteStream<Buffer> {
	private static final Logger log = LoggerFactory.getLogger(WriteStreamWrapper.class);

	/**
	 * Marker for the end
	 */
	private static final Buffer END_MARKER = Buffer.buffer();

	/**
	 * Maximum allowed size of the queue
	 */
	private int writeQueueMaxSize = 8 * 1024;

	/**
	 * Blocking queue for Buffers, which are written to this wrapper
	 */
	private BlockingQueue<Buffer> queue = new LinkedBlockingDeque<Buffer>();

	/**
	 * Buffer, which is currently read in the InputStream
	 */
	private Buffer currentlyRead = null;

	/**
	 * Current position when reading from the buffer
	 */
	private int currentBufStart = 0;

	/**
	 * Total number of bytes, which were read from the InputStream
	 */
	private int readTotal = 0;

	/**
	 * Drain Handler (will be called, when the buffer is drained and accepts more data to be written)
	 */
	private Handler<Void> drainHandler;

	/**
	 * Exception Handler
	 */
	private Handler<Throwable> exceptionHandler;

	/**
	 * Flag to mark, when the WriteStream is ended
	 */
	private boolean ended = false;

	@Override
	public WriteStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
		this.exceptionHandler = handler;
		return this;
	}

	@Override
	public Future<Void> write(Buffer data) {
		queue.add(data);

		return Future.succeededFuture();
	}

	@Override
	public Future<Void> end() {
		this.ended = true;
		queue.add(END_MARKER);
		if (log.isDebugEnabled()) {
			log.debug("Ended");
		}

		return Future.succeededFuture();
	}

	@Override
	public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
		this.writeQueueMaxSize = maxSize;
		return this;
	}

	@Override
	public boolean writeQueueFull() {
		return getTotalLength() >= writeQueueMaxSize;
	}

	@Override
	public WriteStream<Buffer> drainHandler(@Nullable Handler<Void> handler) {
		this.drainHandler = handler;
		checkDrained();
		return this;
	}

	/**
	 * Create an InputStream for reading the data
	 * @return new InputStream instance
	 */
	public InputStream createInputStream() {
		return new InputStream() {
			@Override
			public int read() throws IOException {
				byte[] buffer = new byte[1];
				if (read(buffer, 0, 1) <= 0) {
					return -1;
				}
				return buffer[0];
			}

			@Override
			public int read(@Nonnull byte[] b, int off, int len) throws IOException {
				try {
					if (ended && getTotalLength() == 0) {
						return -1;
					}

					Buffer read = currentlyRead;

					if (read == null) {
						try {
							if (log.isDebugEnabled()) {
								log.debug("Waiting for more data");
							}
							currentlyRead = queue.take();
							currentBufStart = 0;

							if (currentlyRead == END_MARKER) {
								if (log.isDebugEnabled()) {
									log.debug("Found end marker");
								}
								currentlyRead = null;
								return -1;
							}

							read = currentlyRead;
						} catch (InterruptedException e) {
							throw new IOException("Error while getting next buffer from queue", e);
						}
					}

					int byteCount = Math.min(read.length() - currentBufStart, len);
					if (log.isDebugEnabled()) {
						log.debug("Reading bytes {}-{}. Total Read {}, Available {}", currentBufStart, currentBufStart + byteCount, readTotal, getTotalLength());
					}
					read.getBytes(currentBufStart, currentBufStart + byteCount, b, off);
					currentBufStart += byteCount;
					readTotal += byteCount;

					if (currentBufStart >= read.length()) {
						currentlyRead = null;
						currentBufStart = 0;
					}

					checkDrained();

					return byteCount;
				} catch (IOException | RuntimeException e) {
					Handler<Throwable> handler = exceptionHandler;
					if (handler != null) {
						handler.handle(e);
					}
					throw e;
				}
			}
		};
	}

	/**
	 * Check whether the queue accepts more data, and call the
	 * {@link #drainHandler}, if any exists. The {@link #drainHandler} will be unset
	 * afterwards.
	 */
	private void checkDrained() {
		if (getTotalLength() < writeQueueMaxSize) {
			Handler<Void> handler = drainHandler;
			drainHandler = null;
			if (handler != null) {
				handler.handle(null);
			}
		}
	}

	/**
	 * Get the total length of all buffers in the queue and the currently read buffer
	 * @return total length currently stored in the queue
	 */
	private synchronized int getTotalLength() {
		int totalLength = 0;
		Buffer buf = currentlyRead;
		if (buf != null) {
			totalLength = buf.length();
		}
		totalLength += queue.stream().mapToInt(Buffer::length).sum();
		return totalLength;
	}
}
