package com.hrz.wirehair;

import java.util.Arrays;

/**
 * Wirehair 编码器。
 *
 * 编码块 ID 规则：
 * 1. [0, sourceBlockCount) 是源块；
 * 2. sourceBlockCount 及之后是修复块；
 * 3. 相同 ID 始终代表相同编码块；
 * 4. 重传同一编码块时，不要重新分配 ID。
 *
 * 使用完毕后必须调用 close() 释放原生资源。
 */
public final class WirehairEncoder implements AutoCloseable {

    /** 原始消息长度，单位为字节。 */
    private final int messageBytes;

    /** 编码块大小，单位为字节。 */
    private final int blockBytes;

    /** 源块总数。 */
    private final int sourceBlockCount;

    /** 原生编码器句柄。0 表示已经关闭。 */
    private long handle;

    WirehairEncoder(
            byte[] message,
            int blockBytes,
            int sourceBlockCount) {

        this.messageBytes = message.length;
        this.blockBytes = blockBytes;
        this.sourceBlockCount = sourceBlockCount;

        this.handle = WirehairNative.createEncoder(
                message,
                blockBytes);

        if (this.handle == 0L) {
            throw new WirehairException(
                    "创建 Wirehair 编码器失败");
        }
    }

    public int messageBytes() {
        return messageBytes;
    }

    public int blockBytes() {
        return blockBytes;
    }

    public int sourceBlockCount() {
        return sourceBlockCount;
    }

    /**
     * 将指定 ID 的编码块写入 output。
     *
     * @param blockId 编码块 ID
     * @param output 输出缓冲区，长度至少为 blockBytes
     * @return 实际写入的字节数
     */
    public synchronized int encodeInto(
            int blockId,
            byte[] output) {

        ensureOpen();

        if (blockId < 0) {
            throw new IllegalArgumentException(
                    "blockId 不能小于 0");
        }

        if (output == null || output.length < blockBytes) {
            throw new IllegalArgumentException(
                    "output 长度至少必须为 blockBytes="
                            + blockBytes);
        }

        int length = WirehairNative.encodeInto(
                handle,
                blockId,
                output);

        if (length <= 0) {
            throw new WirehairException(
                    "Wirehair 编码失败："
                            + WirehairNative.resultString(-length));
        }

        return length;
    }

    /**
     * 创建新数组并返回编码块。
     *
     * 高频二维码生成场景建议使用 encodeInto()，
     * 复用同一个 byte[]，减少 Java 数组分配。
     */
    public byte[] encode(int blockId) {
        byte[] output = new byte[blockBytes];

        int length = encodeInto(blockId, output);

        if (length == output.length) {
            return output;
        }

        return Arrays.copyOf(output, length);
    }

    private void ensureOpen() {
        if (handle == 0L) {
            throw new IllegalStateException(
                    "Wirehair 编码器已经关闭");
        }
    }

    @Override
    public synchronized void close() {
        if (handle != 0L) {
            WirehairNative.free(handle);
            handle = 0L;
        }
    }
}