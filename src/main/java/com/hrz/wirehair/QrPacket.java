
package com.hrz.wirehair;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Wirehair 编码数据包。
 *
 * <p>本类属于 Wirehair Java 库，负责封装编码块编号和数据载荷，
 * 并提供二进制序列化及二维码文本转换功能。</p>
 *
 * <p>二进制数据包格式（大端序）：</p>
 *
 * <pre>
 * 偏移  长度  内容
 * 0     4     固定标识 WHQP
 * 4     1     协议版本
 * 5     4     Wirehair 编码块编号
 * 9     4     数据载荷长度
 * 13    N     数据载荷
 * </pre>
 *
 * <p>当前协议版本为 1，固定头部长度为 13 字节。</p>
 *
 * <p>注意：二维码文本转换使用 ISO-8859-1 一一映射字节。
 * 发送端和接收端必须使用兼容的字符集设置。</p>
 */
public final class QrPacket {

    /** 数据包固定标识：WHQP。 */
    private static final byte[] MAGIC = {
            (byte) 'W',
            (byte) 'H',
            (byte) 'Q',
            (byte) 'P'
    };

    /** 当前协议版本。 */
    public static final int VERSION = 1;

    /** 固定头部长度：4 + 1 + 4 + 4。 */
    public static final int HEADER_LENGTH = 13;

    /** 编码块编号在协议中的最大允许值。 */
    private static final int MAX_BLOCK_ID = Integer.MAX_VALUE;

    /** 单个数据包允许的最大载荷长度，防止异常数据造成过量内存分配。 */
    private static final int MAX_PAYLOAD_LENGTH = 16 * 1024 * 1024;

    private final int blockId;
    private final byte[] payload;

    private QrPacket(int blockId, byte[] payload) {
        if (blockId < 0 || blockId > MAX_BLOCK_ID) {
            throw new IllegalArgumentException(
                    "编码块编号无效：" + blockId);
        }

        Objects.requireNonNull(payload, "payload 不能为 null");

        if (payload.length <= 0) {
            throw new IllegalArgumentException("数据载荷不能为空");
        }

        if (payload.length > MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException(
                    "数据载荷超过允许的最大长度：" + payload.length);
        }

        this.blockId = blockId;
        this.payload = payload.clone();
    }

    /**
     * 创建编码数据包。
     *
     * @param blockId Wirehair 编码块编号
     * @param payload 编码块数据
     * @return 新的数据包
     */
    public static QrPacket of(int blockId, byte[] payload) {
        return new QrPacket(blockId, payload);
    }

    /**
     * 获取编码块编号。
     *
     * <p>发送端必须使用生成该载荷时对应的实际 blockId，
     * 接收端必须将同一个编号传递给 WirehairDecoder。</p>
     */
    public int getBlockId() {
        return blockId;
    }

    /**
     * 获取数据载荷的副本。
     */
    public byte[] getPayload() {
        return payload.clone();
    }

    /**
     * 获取数据载荷长度。
     */
    public int getPayloadLength() {
        return payload.length;
    }

    /**
     * 将数据包序列化为二进制数组。
     *
     * @return 固定头部和数据载荷组成的字节数组
     */
    public byte[] toByteArray() {
        ByteBuffer buffer = ByteBuffer.allocate(
                HEADER_LENGTH + payload.length
        ).order(ByteOrder.BIG_ENDIAN);

        buffer.put(MAGIC);
        buffer.put((byte) VERSION);
        buffer.putInt(blockId);
        buffer.putInt(payload.length);
        buffer.put(payload);

        return buffer.array();
    }

    /**
     * 从二进制数组解析数据包。
     *
     * @param data 完整的数据包字节数组
     * @return 解析后的数据包
     * @throws IllegalArgumentException 数据格式不合法时抛出
     */
    public static QrPacket fromByteArray(byte[] data) {
        Objects.requireNonNull(data, "data 不能为 null");

        if (data.length < HEADER_LENGTH) {
            throw new IllegalArgumentException(
                    "数据包长度不足，无法读取完整头部");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data)
                .order(ByteOrder.BIG_ENDIAN);

        for (int i = 0; i < MAGIC.length; i++) {
            if (buffer.get() != MAGIC[i]) {
                throw new IllegalArgumentException(
                        "数据包标识不匹配");
            }
        }

        int version = Byte.toUnsignedInt(buffer.get());

        if (version != VERSION) {
            throw new IllegalArgumentException(
                    "不支持的数据包协议版本：" + version);
        }

        int blockId = buffer.getInt();
        int payloadLength = buffer.getInt();

        if (blockId < 0) {
            throw new IllegalArgumentException(
                    "编码块编号无效：" + blockId);
        }

        if (payloadLength <= 0
                || payloadLength > MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException(
                    "数据载荷长度无效：" + payloadLength);
        }

        if (data.length != HEADER_LENGTH + (long) payloadLength) {
            throw new IllegalArgumentException(
                    "数据包长度与头部声明不一致");
        }

        byte[] payload = new byte[payloadLength];
        buffer.get(payload);

        return new QrPacket(blockId, payload);
    }

    /**
     * 将数据包转换为二维码内容字符串。
     *
     * <p>使用 ISO-8859-1 保持每个字符与一个字节的映射，
     * 不使用 Base64，避免额外的编码膨胀。</p>
     *
     * <p>生成二维码时，应确保二维码编码器使用 ISO-8859-1
     * 或其他能够完整保留这些字节的兼容方式。</p>
     */
    public String toQrContent() {
        return new String(toByteArray(), StandardCharsets.ISO_8859_1);
    }

    /**
     * 从二维码解码得到的字符串恢复数据包。
     *
     * <p>该方法要求输入字符串中的每个字符都在 U+0000 至
     * U+00FF 范围内。如果二维码解码过程改变了原始字节，
     * 解析可能失败。</p>
     *
     * @param content 二维码解码得到的内容
     * @return 解析后的数据包
     */
    public static QrPacket fromQrContent(String content) {
        Objects.requireNonNull(content, "content 不能为 null");

        byte[] data = new byte[content.length()];

        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);

            if (ch > 0xFF) {
                throw new IllegalArgumentException(
                        "二维码内容包含无法通过 ISO-8859-1 "
                                + "还原的字符，位置：" + i);
            }

            data[i] = (byte) ch;
        }

        return fromByteArray(data);
    }

    @Override
    public String toString() {
        return "QrPacket{"
                + "blockId=" + blockId
                + ", payloadLength=" + payload.length
                + ", version=" + VERSION
                + '}';
    }
}
