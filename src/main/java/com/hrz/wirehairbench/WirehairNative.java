package com.hrz.wirehairbench;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Low-level JNI bridge for the Wirehair fountain-code native library.
 *
 * <p>Most applications should use {@link Wirehair}, {@link WirehairEncoder}, and
 * {@link WirehairDecoder}. This class exposes the native API for advanced use.
 * Native libraries are extracted from this JAR and loaded automatically.</p>
 *
 * <p>Bundled targets: Windows x86-64 ({@code wirehairjni.dll}) and Linux x86-64
 * ({@code libwirehairjni.so}). To override bundled loading, set the JVM property
 * {@code -Dwirehairjni.path=/absolute/path/to/native/library}.</p>
 */
public final class WirehairNative {
    private static volatile boolean loaded;

    private WirehairNative() {
    }

    /**
     * Loads the bundled native library once per class loader.
     *
     * @throws UnsatisfiedLinkError if the OS/architecture is unsupported, the
     *         library resource is missing, or the native library cannot be loaded
     */
    public static synchronized void load() {
        if (loaded) {
            return;
        }

        String override = System.getProperty("wirehairjni.path");
        if (override != null && !override.isBlank()) {
            System.load(Path.of(override).toAbsolutePath().toString());
            loaded = true;
            return;
        }

        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        boolean x64 = arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x64");
        final String resource;
        final String suffix;
        if (!x64) {
            throw new UnsatisfiedLinkError("Unsupported Wirehair JNI architecture: " + arch
                    + ". Currently bundled binaries support x86-64 only.");
        } else if (os.startsWith("windows")) {
            resource = "/native/windows-x86_64/wirehairjni.dll";
            suffix = ".dll";
        } else if (os.startsWith("linux")) {
            resource = "/native/linux-x86_64/libwirehairjni.so";
            suffix = ".so";
        } else {
            throw new UnsatisfiedLinkError("Unsupported Wirehair JNI OS: " + os
                    + ". Currently bundled binaries support Windows and Linux.");
        }

        try (InputStream in = WirehairNative.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new UnsatisfiedLinkError("Native library resource is missing from the JAR: " + resource);
            }
            Path extracted = Files.createTempFile("wirehairjni-", suffix);
            extracted.toFile().deleteOnExit();
            Files.copy(in, extracted, StandardCopyOption.REPLACE_EXISTING);
            System.load(extracted.toAbsolutePath().toString());
            loaded = true;
        } catch (IOException e) {
            UnsatisfiedLinkError error = new UnsatisfiedLinkError("Unable to extract native library " + resource
                    + ": " + e.getMessage());
            error.initCause(e);
            throw error;
        }
    }

    /** Initializes Wirehair's native runtime. Call {@link Wirehair#load()} for checked initialization. */
    public static native int init();

    /** Creates an encoder for a message; returns an opaque native handle or zero on failure. */
    public static native long createEncoder(byte[] message, int blockBytes);

    /** Encodes the specified source/repair block ID into {@code output}; returns bytes written or a negative error code. */
    public static native int encodeInto(long encoder, int blockId, byte[] output);

    /** Creates a decoder for a message of the given byte length; returns an opaque native handle or zero on failure. */
    public static native long createDecoder(long messageBytes, int blockBytes);

    /** Adds an encoded packet; returns 1 when enough data is available, 0 when more is needed, or a negative error code. */
    public static native int decode(long decoder, int blockId, byte[] packet, int length);

    /** Recovers the original message into {@code output}; returns zero on success or a negative error code. */
    public static native int recoverInto(long decoder, byte[] output);

    /** Frees an encoder or decoder handle. Each successful create call must be paired with one call to this method. */
    public static native void free(long handle);

    /** Converts a Wirehair result code to a readable string. */
    public static native String resultString(int result);

    /**
     * Returns process memory statistics.
     * @return a two-element array: {@code [current RSS/working set bytes, peak RSS/working set bytes}
     */
    public static native long[] memoryStats();
}
