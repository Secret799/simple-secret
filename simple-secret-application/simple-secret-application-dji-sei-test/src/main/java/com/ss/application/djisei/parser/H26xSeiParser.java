package com.ss.application.djisei.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 有界的 H.264/H.265 Annex-B SEI 解析器。
 *
 * @author junpzx
 * @since 2026-08-13
 */
public final class H26xSeiParser {

    /** H.264 SEI NAL 单元类型。 */
    private static final int H264_SEI_NAL_UNIT_TYPE = 6;

    /** H.265 前缀 SEI NAL 单元类型。 */
    private static final int H265_PREFIX_SEI_NAL_UNIT_TYPE = 39;

    /** H.265 后缀 SEI NAL 单元类型。 */
    private static final int H265_SUFFIX_SEI_NAL_UNIT_TYPE = 40;

    /** RBSP 停止位。 */
    private static final int RBSP_STOP_ONE_BIT = 0x80;

    /** SEI 扩展字段的延续值。 */
    private static final int EXTENDED_VALUE_BYTE = 0xFF;

    /**
     * 解析 Annex-B 帧中的标准 H.264/H.265 SEI 消息。
     *
     * @param frame Annex-B 格式帧数据
     * @param codec 视频编码格式
     * @param maxFrameBytes 允许的最大帧字节数
     * @param maxPayloadBytes 允许的最大单条 SEI 负载字节数
     * @return 解析结果和媒体格式问题
     */
    public SeiParseResult parse(byte[] frame, VideoCodec codec, int maxFrameBytes, int maxPayloadBytes) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(codec, "codec");
        validateLimit(maxFrameBytes, "maxFrameBytes");
        validateLimit(maxPayloadBytes, "maxPayloadBytes");
        if (frame.length > maxFrameBytes) {
            return SeiParseResult.issue("FRAME_TOO_LARGE", "frame bytes exceed configured maximum");
        }
        return parseAnnexB(frame, codec, maxPayloadBytes);
    }

    /**
     * 校验解析资源上限。
     *
     * @param limit 配置的上限
     * @param name 上限参数名
     */
    private void validateLimit(int limit, String name) {
        if (limit <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
    }

    /**
     * 扫描 Annex-B NAL 单元并解析其中的 SEI。
     *
     * @param frame 已验证大小的 Annex-B 帧数据
     * @param codec 视频编码格式
     * @param maxPayloadBytes 允许的最大 SEI 负载字节数
     * @return 解析结果
     */
    private SeiParseResult parseAnnexB(byte[] frame, VideoCodec codec, int maxPayloadBytes) {
        List<SeiMessage> messages = new ArrayList<>();
        List<SeiParseIssue> issues = new ArrayList<>();
        int seiNalUnitCount = 0;
        int startCodeIndex = findStartCode(frame, 0);
        while (startCodeIndex >= 0) {
            int nalStart = startCodeIndex + startCodeLength(frame, startCodeIndex);
            int nextStartCodeIndex = findStartCode(frame, nalStart);
            int nalEnd = nextStartCodeIndex < 0 ? frame.length : nextStartCodeIndex;
            if (isSeiNalUnit(frame, nalStart, nalEnd, codec)) {
                seiNalUnitCount++;
                parseSeiNalUnit(frame, nalStart, nalEnd, codec, maxPayloadBytes, messages, issues);
            }
            startCodeIndex = nextStartCodeIndex;
        }
        return new SeiParseResult(messages, seiNalUnitCount, issues);
    }

    /**
     * 判断 NAL 单元是否为当前编码的标准 SEI 类型。
     *
     * @param frame Annex-B 帧数据
     * @param nalStart NAL 单元起始位置
     * @param nalEnd NAL 单元结束位置（排他）
     * @param codec 视频编码格式
     * @return NAL 单元是否为标准 SEI
     */
    private boolean isSeiNalUnit(byte[] frame, int nalStart, int nalEnd, VideoCodec codec) {
        if (codec == VideoCodec.H264) {
            return nalEnd > nalStart && (frame[nalStart] & 0x1F) == H264_SEI_NAL_UNIT_TYPE;
        }
        if (nalEnd - nalStart < 2) {
            return false;
        }
        int nalUnitType = (frame[nalStart] & 0x7E) >> 1;
        return nalUnitType == H265_PREFIX_SEI_NAL_UNIT_TYPE || nalUnitType == H265_SUFFIX_SEI_NAL_UNIT_TYPE;
    }

    /**
     * 解析一个已经识别的 SEI NAL 单元。
     *
     * @param frame Annex-B 帧数据
     * @param nalStart NAL 单元起始位置
     * @param nalEnd NAL 单元结束位置（排他）
     * @param codec 视频编码格式
     * @param maxPayloadBytes 允许的最大 SEI 负载字节数
     * @param messages 已解析消息的收集器
     * @param issues 解析问题的收集器
     */
    private void parseSeiNalUnit(byte[] frame, int nalStart, int nalEnd, VideoCodec codec, int maxPayloadBytes,
                                 List<SeiMessage> messages, List<SeiParseIssue> issues) {
        int rbspStart = nalStart + (codec == VideoCodec.H264 ? 1 : 2);
        byte[] rbsp = unescapeRbsp(frame, rbspStart, nalEnd);
        parseRbsp(rbsp, maxPayloadBytes, messages, issues);
    }

    /**
     * 解析一个 NAL 单元的 RBSP SEI 消息。
     *
     * @param rbsp 已移除防竞争字节的 RBSP 数据
     * @param maxPayloadBytes 允许的最大 SEI 负载字节数
     * @param messages 已解析消息的收集器
     * @param issues 解析问题的收集器
     */
    private void parseRbsp(byte[] rbsp, int maxPayloadBytes, List<SeiMessage> messages, List<SeiParseIssue> issues) {
        RbspCursor cursor = new RbspCursor(rbsp);
        while (true) {
            if (cursor.atTrailingBits()) {
                return;
            }
            if (cursor.atEnd()) {
                issues.add(new SeiParseIssue("MISSING_TRAILING_BITS", "SEI RBSP is missing trailing bits"));
                return;
            }
            int payloadType = cursor.readExtendedValue();
            if (payloadType < 0) {
                issues.add(cursor.headerIssue());
                return;
            }
            int payloadSize = cursor.readExtendedValue();
            if (payloadSize < 0) {
                issues.add(cursor.headerIssue());
                return;
            }
            if (payloadSize > maxPayloadBytes) {
                issues.add(new SeiParseIssue("PAYLOAD_TOO_LARGE", "payload bytes exceed configured maximum"));
                return;
            }
            if (payloadSize > cursor.remaining()) {
                issues.add(new SeiParseIssue("TRUNCATED_PAYLOAD", "declared payload exceeds remaining RBSP bytes"));
                return;
            }
            messages.add(new SeiMessage(payloadType, cursor.readPayload(payloadSize)));
        }
    }

    /**
     * 在原始 Annex-B 数据中寻找下一个起始码。
     *
     * @param frame Annex-B 帧数据
     * @param from 搜索起始位置
     * @return 起始码的位置；未找到时为 -1
     */
    private int findStartCode(byte[] frame, int from) {
        for (int index = from; index + 2 < frame.length; index++) {
            if (frame[index] == 0 && frame[index + 1] == 0 && frame[index + 2] == 1) {
                return index;
            }
            if (index + 3 < frame.length && frame[index] == 0 && frame[index + 1] == 0
                    && frame[index + 2] == 0 && frame[index + 3] == 1) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 获取指定 Annex-B 起始码的长度。
     *
     * @param frame Annex-B 帧数据
     * @param startCodeIndex 起始码位置
     * @return 三或四字节起始码的长度
     */
    private int startCodeLength(byte[] frame, int startCodeIndex) {
        return startCodeIndex + 3 < frame.length && frame[startCodeIndex + 2] == 0 ? 4 : 3;
    }

    /**
     * 移除一个 NAL RBSP 中的防竞争字节。
     *
     * @param frame 原始 Annex-B 帧数据
     * @param start RBSP 起始位置
     * @param end RBSP 结束位置（排他）
     * @return 已移除防竞争字节的 RBSP
     */
    private byte[] unescapeRbsp(byte[] frame, int start, int end) {
        int removedBytes = countEmulationPreventionBytes(frame, start, end);
        byte[] rbsp = new byte[end - start - removedBytes];
        int source = start;
        int target = 0;
        int zeroCount = 0;
        while (source < end) {
            int value = frame[source++] & 0xFF;
            if (zeroCount >= 2 && value == 3) {
                zeroCount = 0;
                continue;
            }
            rbsp[target++] = (byte) value;
            zeroCount = value == 0 ? zeroCount + 1 : 0;
        }
        return rbsp;
    }

    /**
     * 统计 RBSP 中的防竞争字节数量。
     *
     * @param frame 原始 Annex-B 帧数据
     * @param start RBSP 起始位置
     * @param end RBSP 结束位置（排他）
     * @return 防竞争字节数量
     */
    private int countEmulationPreventionBytes(byte[] frame, int start, int end) {
        int removedBytes = 0;
        int zeroCount = 0;
        for (int index = start; index < end; index++) {
            int value = frame[index] & 0xFF;
            if (zeroCount >= 2 && value == 3) {
                removedBytes++;
                zeroCount = 0;
            } else {
                zeroCount = value == 0 ? zeroCount + 1 : 0;
            }
        }
        return removedBytes;
    }

    /**
     * 单个 NAL RBSP 的有界读取游标。
     *
     * @author junpzx
     * @since 2026-08-13
     */
    private static final class RbspCursor {

        /** 单个 NAL 单元的 RBSP 数据。 */
        private final byte[] rbsp;

        /** 当前读取位置。 */
        private int position;

        /** 最近一次扩展字段读取是否溢出。 */
        private boolean overflowed;

        /**
         * 创建有界 RBSP 游标。
         *
         * @param rbsp 单个 NAL 单元的 RBSP 数据
         */
        private RbspCursor(byte[] rbsp) {
            this.rbsp = rbsp;
        }

        /**
         * 读取 SEI 的变长类型或大小字段。
         *
         * @return 读取成功的非负数；不完整或溢出时为 -1
         */
        private int readExtendedValue() {
            int value = 0;
            overflowed = false;
            while (position < rbsp.length) {
                int next = rbsp[position++] & 0xFF;
                if (value > Integer.MAX_VALUE - next) {
                    overflowed = true;
                    return -1;
                }
                value += next;
                if (next != EXTENDED_VALUE_BYTE) {
                    return value;
                }
            }
            return -1;
        }

        /**
         * 判断当前位置是否为 RBSP trailing bits。
         *
         * @return 当前位置是否为停止位及后续填充零
         */
        private boolean atTrailingBits() {
            if (position >= rbsp.length || (rbsp[position] & 0xFF) != RBSP_STOP_ONE_BIT) {
                return false;
            }
            for (int index = position + 1; index < rbsp.length; index++) {
                if (rbsp[index] != 0) {
                    return false;
                }
            }
            return true;
        }

        /**
         * 判断游标是否已经读到 RBSP 末尾。
         *
         * @return 游标是否已经读到末尾
         */
        private boolean atEnd() {
            return position >= rbsp.length;
        }

        /**
         * 获取剩余的可读字节数。
         *
         * @return 剩余字节数
         */
        private int remaining() {
            return rbsp.length - position;
        }

        /**
         * 读取指定长度的负载副本。
         *
         * @param length 负载长度
         * @return 负载副本
         */
        private byte[] readPayload(int length) {
            byte[] payload = Arrays.copyOfRange(rbsp, position, position + length);
            position += length;
            return payload;
        }

        /**
         * 创建最近一次读取失败对应的问题。
         *
         * @return 固定描述的格式问题
         */
        private SeiParseIssue headerIssue() {
            if (overflowed) {
                return new SeiParseIssue("MALFORMED_HEADER", "SEI extended header value overflows integer range");
            }
            return new SeiParseIssue("TRUNCATED_HEADER", "SEI payload type or size is incomplete");
        }
    }
}
