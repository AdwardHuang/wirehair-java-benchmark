package com.hrz.wirehairbench;

import java.util.Arrays;
import java.util.Objects;

/**
 * Stateful Wirehair encoder. Source blocks use IDs starting at zero; repair blocks use
 * IDs beyond the source-block range. The receiver must use the exact same message length
 * and {@code blockBytes} value when creating its decoder.
 *
 * <p>Instances own a native handle and should be used with try-with-resources.</p>
 */
public final class WirehairEncoder implements AutoCloseable {
    private final int blockBytes;
    private long handle;

    WirehairEncoder(byte[] message, int blockBytes) {
        Objects.requireNonNull(message, "message");
        if (message.length == 0) {
            throw new IllegalArgumentException("message must not be empty");
        }
        if (blockBytes <= 0) {
            throw new IllegalArgumentException("blockBytes must be positive");
        }
        Wirehair.load();
        this.blockBytes = blockBytes;
        this.handle = WirehairNative.createEncoder(message, blockBytes);
        if (handle == 0) {
            throw new WirehairException("Unable to create Wirehair encoder");
        }
    }

    /** Returns the configured maximum encoded block size in bytes. */
    public int blockBytes() {
        return blockBytes;
    }

    /**
     * Encodes a source or repair block into a caller-provided reusable buffer.
     *
     * @param blockId non-negative Wirehair block/ESI ID
     * @param output destination buffer with at least {@link #blockBytes()} bytes
     * @return number of bytes written to {@code output}
     * @throws IllegalArgumentException if the ID or buffer is invalid
     * @throws WirehairException if native encoding fails
     */
    public synchronized int encodeInto(int blockId, byte[] output) {
        ensureOpen();
        Objects.requireNonNull(output, "output");
        if (blockId < 0) {
            throw new IllegalArgumentException("blockId must be non-negative");
        }
        if (output.length < blockBytes) {
            throw new IllegalArgumentException("output must have at least blockBytes bytes");
        }
        int result = WirehairNative.encodeInto(handle, blockId, output);
        if (result <= 0) {
            throw nativeFailure("encode", result);
        }
        return result;
    }

    /** Encodes a block and returns a newly allocated array containing only the written bytes. */
    public synchronized byte[] encode(int blockId) {
        byte[] buffer = new byte[blockBytes];
        int length = encodeInto(blockId, buffer);
        return length == buffer.length ? buffer : Arrays.copyOf(buffer, length);
    }

    private void ensureOpen() {
        if (handle == 0) {
            throw new IllegalStateException("Encoder is closed");
        }
    }

    private static WirehairException nativeFailure(String operation, int result) {
        String detail = result < 0 ? WirehairNative.resultString(-result) : "unknown native error";
        return new WirehairException("Wirehair " + operation + " failed (code " + result + "): " + detail);
    }

    /** Releases native encoder resources. Safe to call more than once. */
    @Override
    public synchronized void close() {
        if (handle != 0) {
            WirehairNative.free(handle);
            handle = 0;
        }
    }
}
