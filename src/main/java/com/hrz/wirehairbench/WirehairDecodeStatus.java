package com.hrz.wirehairbench;

/** Result of feeding one encoded packet into a decoder. */
public enum WirehairDecodeStatus {
    /** The decoder needs additional distinct encoded packets before recovery can succeed. */
    NEED_MORE,
    /** The decoder reports that it has enough information to attempt recovery. */
    READY
}
