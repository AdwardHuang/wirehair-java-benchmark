package com.hrz.wirehairbench;

import java.util.Objects;

/**
 * Stateful Wirehair decoder. Feed each received source/repair packet with its original
 * block ID. Packet IDs must not be renumbered when packets are lost.
 *
 * <p>A {@link WirehairDecodeStatus#READY} result means recovery may be attempted; it does
 * not replace validating the recovered message (for example with a cryptographic hash).
 * Instances own a native handle and should be used with try-with-resources.</p>
 */
public final class WirehairDecoder implements AutoCloseable {
    private final long messageBytes;
    private final int blockBytes;
    private long handle;

    WirehairDecoder(long messageBytes, int blockBytes) {
        if (messageBytes <= 0) {
            throw new IllegalArgumentException("messageBytes must be positive");
        }
        if (blockBytes <= 0) {
            throw new IllegalArgumentException("blockBytes must be positive");
        }
        Wirehair.load();
        this.messageBytes = messageBytes;
        this.blockBytes = blockBytes;
        this.handle = WirehairNative.createDecoder(messageBytes, blockBytes);
        if (handle == 0) {
            throw new WirehairException("Unable to create Wirehair decoder");
        }
    }

    /** Returns the original message length in bytes. */
    public long messageBytes() {
        return messageBytes;
    }

    /** Returns the configured encoded block size in bytes. */
    public int blockBytes() {
        return blockBytes;
    }

    /**
     * Supplies one received packet to the decoder.
     *
     * @param blockId original non-negative source/repair block ID
     * @param packet byte array containing the encoded packet
     * @param length number of valid bytes at the start of {@code packet}
     * @return {@code NEED_MORE} while more packets are required, or {@code READY} when
     *         Wirehair reports sufficient information for recovery
     * @throws WirehairException if the native decoder reports an error
     */
    public synchronized WirehairDecodeStatus accept(int blockId, byte[] packet, int length) {
        ensureOpen();
        Objects.requireNonNull(packet, "packet");
        if (blockId < 0) {
            throw new IllegalArgumentException("blockId must be non-negative");
        }
        if (length <= 0 || length > packet.length) {
            throw new IllegalArgumentException("length must be between 1 and packet.length");
        }
        int result = WirehairNative.decode(handle, blockId, packet, length);
        if (result == 1) {
            return WirehairDecodeStatus.READY;
        }
        if (result == 0) {
            return WirehairDecodeStatus.NEED_MORE;
        }
        throw new WirehairException("Wirehair decode failed (code " + result + "): "
                + WirehairNative.resultString(-result));
    }

    /**
     * Recovers the original message into a newly allocated byte array.
     *
     * @throws IllegalStateException if the message length exceeds Java array limits
     * @throws WirehairException if the decoder still needs data or recovery fails
     */
    public synchronized byte[] recover() {
        ensureOpen();
        if (messageBytes > Integer.MAX_VALUE) {
            throw new IllegalStateException("Message is too large for a single Java byte[]: " + messageBytes);
        }
        byte[] output = new byte[(int) messageBytes];
        recoverInto(output);
        return output;
    }

    /** Recovers the original message into a caller-provided buffer of exactly messageBytes length. */
    public synchronized void recoverInto(byte[] output) {
        ensureOpen();
        Objects.requireNonNull(output, "output");
        if (output.length != messageBytes) {
            throw new IllegalArgumentException("output.length must equal messageBytes (" + messageBytes + ")");
        }
        int result = WirehairNative.recoverInto(handle, output);
        if (result != 0) {
            throw new WirehairException("Wirehair recovery failed: " + WirehairNative.resultString(-result));
        }
    }

    private void ensureOpen() {
        if (handle == 0) {
            throw new IllegalStateException("Decoder is closed");
        }
    }

    /** Releases native decoder resources. Safe to call more than once. */
    @Override
    public synchronized void close() {
        if (handle != 0) {
            WirehairNative.free(handle);
            handle = 0;
        }
    }
}
