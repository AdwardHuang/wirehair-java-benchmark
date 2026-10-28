package com.hrz.wirehairbench;

public final class WirehairNative {
    private WirehairNative() {
    }

    public static void load() {
        String path = System.getProperty("wirehairjni.path");
        if (path != null && !path.isBlank()) {
            System.load(path);
        } else {
            System.loadLibrary("wirehairjni");
        }
    }

    public static native int init();
    public static native long createEncoder(byte[] message, int blockBytes);
    public static native int encodeInto(long encoder, int blockId, byte[] output);
    public static native long createDecoder(long messageBytes, int blockBytes);
    public static native int decode(long decoder, int blockId, byte[] packet, int length);
    public static native int recoverInto(long decoder, byte[] output);
    public static native void free(long handle);
    public static native String resultString(int result);

    /** Returns current RSS/working set and peak RSS/working set, in bytes. */
    public static native long[] memoryStats();
}
