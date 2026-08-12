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

    /**
     * 创建并初始化实例。
     *
     * @param nalData NAL 单元字节数据
     * @param firstFragmentTimestamp 首个媒体分片时间戳
     */
    public H264NalUnit(byte[] nalData, long firstFragmentTimestamp) {
        this.nalData = nalData != null ? nalData.clone() : new byte[0];
        this.firstFragmentTimestamp = firstFragmentTimestamp;
        this.type = Integer.toHexString(this.nalData.length > 4 ? nalData[4] & 0x1F : 0);
    }

    /**
     * 返回NAL 单元字节数据。
     *
     * @return NAL 单元字节数据
     */
    public byte[] getNalData() {
        return nalData;
    }

    /**
     * 返回首个媒体分片时间戳。
     *
     * @return 首个媒体分片时间戳
     */
    public long getFirstFragmentTimestamp() {
        return firstFragmentTimestamp;
    }

    /**
     * 返回目标类型。
     *
     * @return 目标类型
     */
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
