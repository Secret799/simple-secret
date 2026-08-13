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
        return parse(frame, codec, maxFrameBytes, SeiParseLimits.defaults(maxPayloadBytes));
    }

    /**
     * 使用显式资源上限解析 Annex-B 帧中的标准 H.264/H.265 SEI 消息。
     *
     * @param frame Annex-B 格式帧数据
     * @param codec 视频编码格式
     * @param maxFrameBytes 允许的最大帧字节数
     * @param limits 单帧解析资源上限
     * @return 解析结果和媒体格式问题
     */
    public SeiParseResult parse(byte[] frame, VideoCodec codec, int maxFrameBytes, SeiParseLimits limits) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(limits, "limits");
        validateLimit(maxFrameBytes, "maxFrameBytes");
        if (frame.length > maxFrameBytes) {
            return SeiParseResult.issue("FRAME_TOO_LARGE", "frame bytes exceed configured maximum");
        }
        return parseAnnexB(frame, codec, limits);
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
     * @param limits 单帧解析资源上限
     * @return 解析结果
     */
    private SeiParseResult parseAnnexB(byte[] frame, VideoCodec codec, SeiParseLimits limits) {
        ParseContext context = new ParseContext(limits);
        int seiNalUnitCount = 0;
        int startCodeIndex = findStartCode(frame, 0);
        while (startCodeIndex >= 0 && !context.stopped) {
            int nalStart = startCodeIndex + startCodeLength(frame, startCodeIndex);
            int nextStartCodeIndex = findStartCode(frame, nalStart);
            int nalEnd = nextStartCodeIndex < 0 ? frame.length : nextStartCodeIndex;
            if (isSeiNalUnit(frame, nalStart, nalEnd, codec)) {
                if (seiNalUnitCount >= limits.maxSeiNalUnits()) {
                    context.stopWithLimit("SEI_NAL_UNIT_LIMIT_REACHED", "SEI NAL unit count reached configured limit");
                    break;
                }
                seiNalUnitCount++;
                parseSeiNalUnit(frame, nalStart, nalEnd, codec, context);
            }
            startCodeIndex = nextStartCodeIndex;
        }
        return new SeiParseResult(context.messages, seiNalUnitCount, context.issues);
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
     * @param context 单帧解析上下文
     */
    private void parseSeiNalUnit(byte[] frame, int nalStart, int nalEnd, VideoCodec codec, ParseContext context) {
        int rbspStart = nalStart + (codec == VideoCodec.H264 ? 1 : 2);
        if (!hasValidEmulationPreventionBytes(frame, rbspStart, nalEnd)) {
            context.addIssue(new SeiParseIssue("INVALID_EMULATION_PREVENTION_BYTE",
                    "invalid or missing emulation prevention byte after two zero bytes"));
            return;
        }
        byte[] rbsp = unescapeRbsp(frame, rbspStart, nalEnd);
        int issueCount = context.issues.size();
        parseRbsp(rbsp, context);
        replaceAmbiguousTruncationIssue(frame, nalEnd, issueCount, context);
    }

    /**
     * 将 SEI payload 内被 Annex-B 扫描器识别成起始码的未转义序列归类为 EPB 错误。
     *
     * @param frame 原始 Annex-B 帧数据
     * @param nalEnd 当前 NAL 单元结束位置
     * @param issueCount 解析当前 NAL 前的问题数量
     * @param context 单帧解析上下文
     */
    private void replaceAmbiguousTruncationIssue(byte[] frame, int nalEnd, int issueCount, ParseContext context) {
        if (context.issues.size() <= issueCount || nalEnd + 2 >= frame.length) {
            return;
        }
        SeiParseIssue issue = context.issues.get(context.issues.size() - 1);
        boolean threeByteStartCode = frame[nalEnd] == 0 && frame[nalEnd + 1] == 0 && frame[nalEnd + 2] == 1;
        if ("TRUNCATED_PAYLOAD".equals(issue.code()) && threeByteStartCode) {
            context.issues.set(context.issues.size() - 1, new SeiParseIssue(
                    "INVALID_EMULATION_PREVENTION_BYTE", "missing emulation prevention byte before 0x01"));
        }
    }

    /**
     * 在分配 RBSP 前校验所有仿真防止字节。
     *
     * @param frame 原始 Annex-B 帧数据
     * @param start RBSP 起始位置
     * @param end RBSP 结束位置（排他）
     * @return 防竞争字节完整且两个零字节后不存在未转义的 {@code 00..02} 时返回 true
     */
    private boolean hasValidEmulationPreventionBytes(byte[] frame, int start, int end) {
        int validationEnd = trailingPaddingStart(frame, start, end);
        int zeroCount = 0;
        for (int index = start; index < validationEnd; index++) {
            int value = frame[index] & 0xFF;
            if (zeroCount >= 2) {
                if (value <= 2) {
                    return false;
                }
                if (value == 3 && (index + 1 >= validationEnd || (frame[index + 1] & 0xFF) > 3)) {
                    return false;
                }
            }
            if (zeroCount >= 2 && value == 3) {
                zeroCount = 0;
                continue;
            }
            zeroCount = value == 0 ? zeroCount + 1 : 0;
        }
        return true;
    }

    /**
     * 定位 RBSP stop bit 后合法零填充的起始位置。
     *
     * @param frame 原始 Annex-B 帧数据
     * @param start RBSP 起始位置
     * @param end RBSP 结束位置（排他）
     * @return 需要执行防竞争校验的结束位置（排他）
     */
    private int trailingPaddingStart(byte[] frame, int start, int end) {
        int paddingStart = end;
        while (paddingStart > start && frame[paddingStart - 1] == 0) {
            paddingStart--;
        }
        if (paddingStart > start && (frame[paddingStart - 1] & 0xFF) == 0x80) {
            return paddingStart;
        }
        return end;
    }

    /**
     * 解析一个 NAL 单元的 RBSP SEI 消息。
     *
     * @param rbsp 已移除防竞争字节的 RBSP 数据
     * @param context 单帧解析上下文
     */
    private void parseRbsp(byte[] rbsp, ParseContext context) {
        RbspCursor cursor = new RbspCursor(rbsp);
        while (!context.stopped) {
            if (cursor.atTrailingBits()) {
                return;
            }
            if (cursor.atEnd()) {
                context.addIssue(new SeiParseIssue("MISSING_TRAILING_BITS", "SEI RBSP is missing trailing bits"));
                return;
            }
            int payloadType = cursor.readExtendedValue();
            if (payloadType < 0) {
                context.addIssue(cursor.headerIssue());
                return;
            }
            int payloadSize = cursor.readExtendedValue();
            if (payloadSize < 0) {
                context.addIssue(cursor.headerIssue());
                return;
            }
            if (payloadSize > context.limits.maxPayloadBytes()) {
                context.addIssue(new SeiParseIssue("PAYLOAD_TOO_LARGE",
                        "payload bytes exceed configured maximum"));
                return;
            }
            if (payloadSize > cursor.remaining()) {
                context.addIssue(new SeiParseIssue("TRUNCATED_PAYLOAD",
                        "declared payload exceeds remaining RBSP bytes"));
                return;
            }
            if (context.messages.size() >= context.limits.maxSeiMessages()) {
                context.stopWithLimit("SEI_MESSAGE_LIMIT_REACHED", "SEI message count reached configured limit");
                return;
            }
            context.messages.add(new SeiMessage(payloadType, cursor.readPayload(payloadSize)));
        }
    }

    /**
     * 单帧解析的有界可变状态。
     *
     * @author junpzx
     * @since 2026-08-13
     */
    private static final class ParseContext {

        /** 单帧解析资源上限。 */
        private final SeiParseLimits limits;

        /** 已解析的有界消息列表。 */
        private final List<SeiMessage> messages = new ArrayList<>();

        /** 已发现的有界问题列表。 */
        private final List<SeiParseIssue> issues = new ArrayList<>();

        /** 是否已经触发必须终止当前帧的资源上限。 */
        private boolean stopped;

        /** @param limits 单帧解析资源上限 */
        private ParseContext(SeiParseLimits limits) {
            this.limits = limits;
        }

        /**
         * 追加一个结构化问题；问题超限时改为稳定上限问题并停止解析。
         *
         * @param issue 待追加问题
         */
        private void addIssue(SeiParseIssue issue) {
            if (issues.size() < limits.maxIssues() - 1) {
                issues.add(issue);
                return;
            }
            stopWithLimit("SEI_ISSUE_LIMIT_REACHED", "SEI issue count reached configured limit");
        }

        /**
         * 以稳定问题终止当前帧解析，且不突破问题列表上限。
         *
         * @param code 稳定问题代码
         * @param message 稳定问题说明
         */
        private void stopWithLimit(String code, String message) {
            SeiParseIssue limitIssue = new SeiParseIssue(code, message);
            if (issues.size() < limits.maxIssues()) {
                issues.add(limitIssue);
            } else {
                issues.set(issues.size() - 1, limitIssue);
            }
            stopped = true;
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
