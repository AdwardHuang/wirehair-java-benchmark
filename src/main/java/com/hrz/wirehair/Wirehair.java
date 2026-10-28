package com.hrz.wirehair;

/**
 * Wirehair Java API 入口。
 *
 * 发送端与接收端必须使用相同的消息长度、blockBytes
 * 以及兼容的 Wirehair 编码配置。
 */
public final class Wirehair {

    /**
     * 官方 Wirehair 当前支持的源块数量上限为 64000。
     */
    private static final int MAX_SOURCE_BLOCKS = 64000;

    private Wirehair() {
    }

    /**
     * 加载并初始化原生库。
     * 可以在程序启动时主动调用，也可以由 encoder()/decoder() 自动调用。
     */
    public static void load() {
        WirehairNative.load();
    }

    /**
     * 根据原始消息创建编码器。
     *
     * @param message 要编码的完整消息
     * @param blockBytes 每个编码块的字节数
     */
    public static WirehairEncoder encoder(
            byte[] message,
            int blockBytes) {

        if (message == null || message.length == 0) {
            throw new IllegalArgumentException(
                    "message 不能为空");
        }

        validateBlockBytes(blockBytes);

        int sourceBlockCount = (int) (
                ((long) message.length + blockBytes - 1L)
                        / blockBytes);

        if (sourceBlockCount > MAX_SOURCE_BLOCKS) {
            throw new IllegalArgumentException(
                    "源块数量超过 Wirehair 支持范围："
                            + sourceBlockCount);
        }

        load();

        return new WirehairEncoder(
                message,
                blockBytes,
                sourceBlockCount);
    }

    /**
     * 根据原始消息长度创建解码器。
     *
     * 注意：messageBytes 必须是编码前原始消息的准确字节长度，
     * 例如 QOcr 压缩后待传输的数据长度，而不是文件解压后的长度。
     */
    public static WirehairDecoder decoder(
            long messageBytes,
            int blockBytes) {

        if (messageBytes <= 0) {
            throw new IllegalArgumentException(
                    "messageBytes 必须大于 0");
        }

        validateBlockBytes(blockBytes);

        long sourceBlockCount =
                (messageBytes + blockBytes - 1L) / blockBytes;

        if (sourceBlockCount > MAX_SOURCE_BLOCKS) {
            throw new IllegalArgumentException(
                    "源块数量超过 Wirehair 支持范围："
                            + sourceBlockCount);
        }

        load();

        return new WirehairDecoder(
                messageBytes,
                blockBytes,
                (int) sourceBlockCount);
    }

    /**
     * 返回当前进程内存统计。
     */
    public static long[] memoryStats() {
        load();
        return WirehairNative.memoryStats();
    }

    private static void validateBlockBytes(int blockBytes) {
        if (blockBytes <= 0) {
            throw new IllegalArgumentException(
                    "blockBytes 必须大于 0");
        }
    }
}