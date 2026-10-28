package com.hrz.wirehairbench;

import java.security.MessageDigest;
import java.util.Arrays;

public final class Benchmark {
    private static final int DEFAULT_MB = 26;
    private static final int DEFAULT_BLOCK_BYTES = 2500;
    private static final int DEFAULT_REPAIR_LIMIT = 2000;
    private static final int DEFAULT_LOSS_EVERY = 10;

    private Benchmark() {
    }

    public static void main(String[] args) throws Exception {
        int mb = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_MB;
        int blockBytes = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_BLOCK_BYTES;
        int repairLimit = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_REPAIR_LIMIT;
        int lossEvery = args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_LOSS_EVERY;
        if (mb <= 0 || blockBytes <= 0 || repairLimit < 0 || lossEvery < 0) {
            throw new IllegalArgumentException("Invalid benchmark arguments.");
        }
        long messageBytes = mb * 1024L * 1024L;
        if (messageBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("This benchmark currently uses a Java byte[] input.");
        }

        System.out.println("=== Wirehair JNI Benchmark v3 ===");
        System.out.println("messageBytes = " + messageBytes);
        System.out.println("blockBytes   = " + blockBytes);
        System.out.println("repairLimit  = " + repairLimit);
        System.out.println("lossEvery    = " + lossEvery);
        System.out.println();

        WirehairNative.load();
        int init = WirehairNative.init();
        check(init, "wirehair_init");

        byte[] input = new byte[(int) messageBytes];
        fillDeterministic(input);
        int sourceBlocks = (input.length + blockBytes - 1) / blockBytes;
        byte[] packet = new byte[blockBytes];
        byte[] recovered = new byte[input.length];

        printMemory("start");
        printMemory("after input + reusable buffers");

        long encoder = 0;
        long decoder = 0;
        try {
            long t0 = System.nanoTime();
            encoder = WirehairNative.createEncoder(input, blockBytes);
            long encoderMs = elapsedMs(t0);
            if (encoder == 0) {
                throw new IllegalStateException("createEncoder failed");
            }
            System.out.println("encoder = " + encoder);
            System.out.println("TIME createEncoder = " + encoderMs + " ms");
            printMemory("after encoder create");

            t0 = System.nanoTime();
            decoder = WirehairNative.createDecoder(input.length, blockBytes);
            long decoderMs = elapsedMs(t0);
            if (decoder == 0) {
                throw new IllegalStateException("createDecoder failed");
            }
            System.out.println("decoder = " + decoder);
            System.out.println("TIME createDecoder = " + decoderMs + " ms");
            printMemory("after decoder create");

            int received = 0;
            int lost = 0;
            boolean decoderComplete = false;
            System.out.println();
            System.out.println("--- systematic blocks ---");
            for (int blockId = 0; blockId < sourceBlocks; blockId++) {
                int len = WirehairNative.encodeInto(encoder, blockId, packet);
                if (len < 0) {
                    throw new IllegalStateException("encode systematic failed, blockId=" + blockId
                            + ", result=" + WirehairNative.resultString(len));
                }
                if (lossEvery > 0 && ((blockId + 1) % lossEvery == 0)) {
                    lost++;
                    continue;
                }
                int result = WirehairNative.decode(decoder, blockId, packet, len);
                if (result < 0) {
                    throw new IllegalStateException("decode systematic failed, blockId=" + blockId
                            + ", result=" + WirehairNative.resultString(result));
                }
                if (result == 1) {
                    decoderComplete = true;
                }
                received++;
            }
            System.out.println("sourceBlocks = " + sourceBlocks);
            System.out.println("received     = " + received);
            System.out.println("lost         = " + lost);
            printMemory("after systematic");

            System.out.println();
            System.out.println("--- repair blocks ---");
            int repairsSent = 0;
            long repairStart = System.nanoTime();
            for (int i = 0; i < repairLimit; i++) {
                int blockId = sourceBlocks + i;
                int len = WirehairNative.encodeInto(encoder, blockId, packet);
                if (len < 0) {
                    throw new IllegalStateException("encode repair failed, blockId=" + blockId
                            + ", result=" + WirehairNative.resultString(len));
                }
                if (!decoderComplete) {
                    int result = WirehairNative.decode(decoder, blockId, packet, len);
                    if (result < 0) {
                        throw new IllegalStateException("decode repair failed, blockId=" + blockId
                                + ", result=" + WirehairNative.resultString(result));
                    }
                    if (result == 1) {
                        decoderComplete = true;
                    }
                }
                repairsSent++;
                if (i == 0 || (i + 1) % 100 == 0) {
                    System.out.println("repair " + (i + 1) + ", TIME=" + elapsedMs(repairStart) + " ms");
                    printMemory("repair " + (i + 1));
                }
            }
            System.out.println("repairsSent = " + repairsSent);
            System.out.println("TIME repair phase = " + elapsedMs(repairStart) + " ms");
            printMemory("after repair phase");

            System.out.println();
            System.out.println("--- recover ---");
            long recoverStart = System.nanoTime();
            int recoverResult = WirehairNative.recoverInto(decoder, recovered);
            System.out.println("recoverResult = " + recoverResult + " (" + WirehairNative.resultString(recoverResult) + ")");
            System.out.println("TIME recover = " + elapsedMs(recoverStart) + " ms");
            printMemory("after recover");
            if (recoverResult < 0) {
                throw new IllegalStateException("recover failed: " + WirehairNative.resultString(recoverResult));
            }

            boolean same = Arrays.equals(input, recovered);
            String inputSha256 = sha256(input);
            String recoveredSha256 = sha256(recovered);
            System.out.println("input SHA-256     = " + inputSha256);
            System.out.println("recovered SHA-256 = " + recoveredSha256);
            System.out.println("byte-for-byte     = " + same);
            if (!same) {
                throw new IllegalStateException("Recovered data does not match input.");
            }
        } finally {
            if (decoder != 0) {
                WirehairNative.free(decoder);
            }
            if (encoder != 0) {
                WirehairNative.free(encoder);
            }
            System.gc();
            Thread.sleep(200);
            printMemory("after free + GC");
        }
        System.out.println();
        System.out.println("BENCHMARK PASS");
    }

    private static void fillDeterministic(byte[] data) {
        long x = 0x1234_5678_9ABC_DEF0L;
        for (int i = 0; i < data.length; i++) {
            x ^= x << 13;
            x ^= x >>> 7;
            x ^= x << 17;
            data[i] = (byte) x;
        }
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static void printMemory(String phase) {
        long[] m = WirehairNative.memoryStats();
        System.out.printf("MEM %-28s current=%8.1f MB peak=%8.1f MB%n",
                phase, m[0] / 1024.0 / 1024.0, m[1] / 1024.0 / 1024.0);
    }

    private static long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }

    private static void check(int result, String operation) {
        if (result < 0) {
            throw new IllegalStateException(operation + " failed: " + WirehairNative.resultString(result));
        }
    }
}
