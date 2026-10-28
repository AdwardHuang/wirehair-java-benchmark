package com.hrz.wirehairbench;

import com.hrz.wirehair.Wirehair;
import com.hrz.wirehair.WirehairDecodeStatus;
import com.hrz.wirehair.WirehairDecoder;
import com.hrz.wirehair.WirehairEncoder;

import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Wirehair JNI 自检程序。
 *
 * 用于测试：
 * 1. 原生库是否能够加载；
 * 2. 编码器和解码器是否能正常创建；
 * 3. 模拟丢包后是否能够恢复数据；
 * 4. 恢复结果是否与原始数据完全一致。
 */
public final class Benchmark {

    private Benchmark() {
    }

    public static void main(String[] args) throws Exception {
        int messageBytes =
                args.length > 0
                        ? Integer.parseInt(args[0])
                        : 2 * 1024 * 1024;

        int blockBytes =
                args.length > 1
                        ? Integer.parseInt(args[1])
                        : 2500;

        int dropEvery =
                args.length > 2
                        ? Integer.parseInt(args[2])
                        : 10;

        if (messageBytes <= 0 || blockBytes <= 0) {
            throw new IllegalArgumentException(
                    "消息长度和块大小必须大于 0");
        }

        if (dropEvery < 0) {
            throw new IllegalArgumentException(
                    "丢包间隔不能小于 0");
        }

        // 构造可重复生成的测试数据。
        byte[] message = new byte[messageBytes];

        for (int i = 0; i < message.length; i++) {
            message[i] = (byte) (
                    (i * 31 + (i >>> 4) * 17) & 0xff);
        }

        System.out.println("Wirehair JNI 自检开始");
        System.out.println("消息长度：" + message.length + " bytes");
        System.out.println("块大小：" + blockBytes + " bytes");

        if (dropEvery == 0) {
            System.out.println("模拟丢包：关闭");
        } else {
            System.out.println(
                    "模拟丢包：每 " + dropEvery + " 个编码块丢弃 1 个");
        }

        long started = System.nanoTime();

        byte[] recovered;

        try (WirehairEncoder encoder =
                     Wirehair.encoder(message, blockBytes);
             WirehairDecoder decoder =
                     Wirehair.decoder(message.length, blockBytes)) {

            int sourceBlocks = encoder.sourceBlockCount();

            // 复用缓冲区，避免每个编码块都创建新数组。
            byte[] packet = new byte[blockBytes];

            int accepted = 0;
            int generated = 0;
            boolean ready = false;

            // 为测试设置一个额外修复块预算。
            int maxBlocks =
                    sourceBlocks + Math.max(200, sourceBlocks / 2);

            for (int blockId = 0;
                    blockId < maxBlocks;
                    blockId++) {

                int length =
                        encoder.encodeInto(blockId, packet);

                generated++;

                // 模拟丢包：直接跳过当前块，但不能修改后续块 ID。
                if (dropEvery > 0
                        && (blockId + 1) % dropEvery == 0) {
                    continue;
                }

                WirehairDecodeStatus status =
                        decoder.accept(
                                blockId,
                                packet,
                                length);

                accepted++;

                if (status == WirehairDecodeStatus.READY) {
                    ready = true;
                    break;
                }
            }

            if (!ready) {
                throw new IllegalStateException(
                        "当前测试块不足以恢复消息。"
                                + "生成块数：" + generated
                                + "，提交解码块数：" + accepted);
            }

            recovered = decoder.recover();

            System.out.println("源块数量：" + sourceBlocks);
            System.out.println("生成块数：" + generated);
            System.out.println("提交解码块数：" + accepted);
        }

        // 验证恢复结果。
        boolean same = Arrays.equals(message, recovered);

        System.out.println("原始数据 SHA-256：" + sha256(message));
        System.out.println("恢复数据 SHA-256：" + sha256(recovered));
        System.out.println("逐字节一致：" + same);

        long[] memory = Wirehair.memoryStats();

        if (memory != null && memory.length >= 2) {
            System.out.println(
                    "当前进程驻留内存：" + memory[0] + " bytes");

            System.out.println(
                    "历史峰值驻留内存：" + memory[1] + " bytes");
        }

        System.out.printf(
                "耗时：%.3f 秒%n",
                (System.nanoTime() - started) / 1_000_000_000.0);

        if (!same) {
            throw new IllegalStateException(
                    "恢复结果与原始消息不一致");
        }

        System.out.println("Wirehair JNI 自检通过");
    }

    /** 计算 SHA-256，并转为十六进制字符串。 */
    private static String sha256(byte[] data) throws Exception {
        byte[] digest =
                MessageDigest.getInstance("SHA-256").digest(data);

        StringBuilder result =
                new StringBuilder(digest.length * 2);

        for (byte value : digest) {
            result.append(
                    String.format("%02x", value & 0xff));
        }

        return result.toString();
    }
}