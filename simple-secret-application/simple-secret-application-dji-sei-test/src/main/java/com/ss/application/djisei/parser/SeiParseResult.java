package com.ss.application.djisei.parser;

import java.util.List;

/**
 * 一帧 Annex-B 视频数据的 SEI 解析结果。
 *
 * @author junpzx
 * @since 2026-08-13
 */
public final class SeiParseResult {

    /** 已解析的 SEI 消息。 */
    private final List<SeiMessage> messages;

    /** 已识别的 SEI NAL 单元数量。 */
    private final int seiNalUnitCount;

    /** 媒体数据格式问题。 */
    private final List<SeiParseIssue> issues;

    /**
     * 创建解析结果。
     *
     * @param messages 已解析的 SEI 消息
     * @param seiNalUnitCount 已识别的 SEI NAL 单元数量
     * @param issues 媒体数据格式问题
     */
    public SeiParseResult(List<SeiMessage> messages, int seiNalUnitCount, List<SeiParseIssue> issues) {
        this.messages = List.copyOf(messages);
        this.seiNalUnitCount = seiNalUnitCount;
        this.issues = List.copyOf(issues);
    }

    /**
     * 创建只包含一个问题的解析结果。
     *
     * @param code 稳定问题代码
     * @param message 稳定问题说明
     * @return 只包含一个解析问题的结果
     */
    public static SeiParseResult issue(String code, String message) {
        return new SeiParseResult(List.of(), 0, List.of(new SeiParseIssue(code, message)));
    }

    /**
     * 获取已解析的 SEI 消息。
     *
     * @return 不可变 SEI 消息列表
     */
    public List<SeiMessage> messages() {
        return messages;
    }

    /**
     * 获取已识别的 SEI NAL 单元数量。
     *
     * @return SEI NAL 单元数量
     */
    public int seiNalUnitCount() {
        return seiNalUnitCount;
    }

    /**
     * 获取媒体数据格式问题。
     *
     * @return 不可变问题列表
     */
    public List<SeiParseIssue> issues() {
        return issues;
    }
}
