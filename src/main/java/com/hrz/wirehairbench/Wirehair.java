package com.hrz.wirehairbench;

/** Entry point and shared helpers for the Wirehair JNI binding. */
public final class Wirehair {
    private Wirehair() {
    }

    /**
     * Loads the platform-specific native library bundled in this JAR and initializes Wirehair.
     * Safe to call repeatedly.
     *
     * @throws WirehairException if native initialization fails
     * @throws UnsatisfiedLinkError if the bundled native library cannot be loaded
     */
    public static synchronized void load() {
        WirehairNative.load();
        int result = WirehairNative.init();
        if (result != 0) {
            throw new WirehairException("Wirehair initialization failed: " + WirehairNative.resultString(result));
        }
    }

    /**
     * Creates an encoder for the supplied message.
     *
     * @param message original message bytes; copied into native memory
     * @param blockBytes maximum encoded block size in bytes; must be positive
     * @return an encoder that must be closed when no longer needed
     */
    public static WirehairEncoder encoder(byte[] message, int blockBytes) {
        return new WirehairEncoder(message, blockBytes);
    }

    /**
     * Creates a decoder for a message with the specified original length.
     *
     * @param messageBytes original message length in bytes
     * @param blockBytes encoded block size, matching the encoder
     * @return a decoder that must be closed when no longer needed
     */
    public static WirehairDecoder decoder(long messageBytes, int blockBytes) {
        return new WirehairDecoder(messageBytes, blockBytes);
    }

    /** Returns current and peak process RSS/working-set memory, in bytes. */
    public static long[] memoryStats() {
        load();
        return WirehairNative.memoryStats();
    }
}
