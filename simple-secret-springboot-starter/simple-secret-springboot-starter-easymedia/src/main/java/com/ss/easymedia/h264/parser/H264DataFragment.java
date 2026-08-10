package com.ss.easymedia.h264.parser;

/**
 * H.264 数据片段
 *
 * @author junpzx
 */
class H264DataFragment {
    private final byte[] data;
    private final long timestamp;

    public H264DataFragment(byte[] data) {
        this.data = data != null ? data.clone() : new byte[0];
        this.timestamp = System.currentTimeMillis();
    }

    public byte[] getData() {
        return data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "H264DataFragment{" +
                "size=" + data.length +
                ", timestamp=" + timestamp +
                '}';
    }
}


