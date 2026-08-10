package com.ss.easymedia.h264.parser;

/**
 * H264 NALU
 *
 * @author junpzx
 * @since 2023/08/09
 */
public class H264NalUnit {
    // 完整的 NALU 数据，包括起始码
    private final byte[] nalData;
    // 第一个片段的时间戳
    private final long firstFragmentTimestamp;
    private final String type;

    public H264NalUnit(byte[] nalData, long firstFragmentTimestamp) {
        this.nalData = nalData != null ? nalData.clone() : new byte[0];
        this.firstFragmentTimestamp = firstFragmentTimestamp;
        this.type = Integer.toHexString(this.nalData.length > 4 ? nalData[4] & 0x1F : 0);
    }

    public byte[] getNalData() {
        return nalData;
    }

    public long getFirstFragmentTimestamp() {
        return firstFragmentTimestamp;
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return "H264NalUnit{" +
                "size=" + nalData.length +
                ", firstFragmentTs=" + firstFragmentTimestamp +
                ",NaluHead=" + nalData[0] + nalData[1] + nalData[2] + nalData[3] +
                ", type=0x" + type +
                '}';
    }
}
