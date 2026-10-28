package com.hrz.wirehair;

/**
 * Wirehair 解码状态。
 */
public enum WirehairDecodeStatus {

    /** 收到的编码块还不足以恢复完整消息。 */
    NEED_MORE,

    /** 已经收集到足够的编码块，可以尝试恢复完整消息。 */
    READY
}