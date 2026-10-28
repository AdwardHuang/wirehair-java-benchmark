package com.hrz.wirehair;

/**
 * Wirehair 解码器。
 *
 * 接收端必须使用发送端生成编码块时的原始 blockId，
 * 不能根据二维码的接收顺序重新编号。
 */
public final class WirehairDecoder implements AutoCloseable {

    /** 待恢复消息的准确字节长度。 */
    private final long messageBytes;

    /** 每个编码块的字节数。 */
    private final int blockBytes;

    /** 源块数量。 */
    private final int sourceBlockCount;

    /** 原生解码器句柄，0 表示已经关闭。 */
    private long handle;

    /** 是否已收到足够的编码块。 */
    private boolean ready;

    WirehairDecoder(
            long messageBytes,
            int blockBytes,
            int sourceBlockCount) {

        this.messageBytes = messageBytes;
        this.blockBytes = blockBytes;
        this.sourceBlockCount = sourceBlockCount;

        this.handle = WirehairNative.createDecoder(
                messageBytes,
                blockBytes);

        if (this.handle == 0L) {
            throw new WirehairException(
                    "创建 Wirehair 解码器失败");
        }
    }

    public long messageBytes() {
        return messageBytes;
    }

    public int blockBytes() {
        return blockBytes;
    }

    public int sourceBlockCount() {
        return sourceBlockCount;
    }

    /**
     * 向解码器提交一个编码块。
     *
     * @param blockId 发送端原始编码块 ID
     * @param packet 编码块数据
     * @param length packet 中有效数据的长度
     * @return NEED_MORE 表示继续接收，READY 表示可以恢复
     */
    public synchronized WirehairDecodeStatus accept(
            int blockId,
            byte[] packet,
            int length) {

        ensureOpen();

        if (ready) {
            return WirehairDecodeStatus.READY;
        }

        if (blockId < 0) {
            throw new IllegalArgumentException(
                    "blockId 不能小于 0");
        }

        if (packet == null
                || length <= 0
                || length > packet.length) {

            throw new IllegalArgumentException(
                    "packet 或 length 参数不合法");
        }

        int result = WirehairNative.decode(
                handle,
                blockId,
                packet,
                length);

        if (result == 1) {
            ready = true;
            return WirehairDecodeStatus.READY;
        }

        if (result == 0) {
            return WirehairDecodeStatus.NEED_MORE;
        }

        throw new WirehairException(
                "Wirehair 解码失败："
                        + WirehairNative.resultString(-result)
                        + "，原生返回码：" + result);
    }

    /**
     * 恢复完整消息并返回新数组。
     * 只有 accept() 返回 READY 后才能调用。
     */
    public synchronized byte[] recover() {
        byte[] output = new byte[Math.toIntExact(messageBytes)];

        recoverInto(output);

        return output;
    }

    /**
     * 将完整消息恢复到调用方提供的数组。
     * output 长度必须与 messageBytes 完全相同。
     */
    public synchronized void recoverInto(byte[] output) {
        ensureOpen();

        if (!ready) {
            throw new IllegalStateException(
                    "解码尚未就绪，不能恢复消息");
        }

        if (output == null || output.length != messageBytes) {
            throw new IllegalArgumentException(
                    "output 长度必须等于 messageBytes="
                            + messageBytes);
        }

        int result = WirehairNative.recoverInto(
                handle,
                output);

        if (result != 0) {
            throw new WirehairException(
                    "Wirehair 恢复失败："
                            + WirehairNative.resultString(-result)
                            + "，原生返回码：" + result);
        }
    }

    private void ensureOpen() {
        if (handle == 0L) {
            throw new IllegalStateException(
                    "Wirehair 解码器已经关闭");
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