package com.ss.application.djisei.parser;

/**
 * 单帧 SEI 解析资源上限。
 *
 * @param maxPayloadBytes 单条负载最大字节数
 * @param maxSeiNalUnits 单帧 SEI NAL 单元上限
 * @param maxSeiMessages 单帧 SEI 消息上限
 * @param maxIssues 单帧结构化问题上限
 * @author junpzx
 * @since 2026-08-13
 */
public record SeiParseLimits(int maxPayloadBytes, int maxSeiNalUnits, int maxSeiMessages, int maxIssues) {

    /** 默认单帧 SEI NAL 单元上限。 */
    public static final int DEFAULT_MAX_SEI_NAL_UNITS = 256;

    /** 默认单帧 SEI 消息上限。 */
    public static final int DEFAULT_MAX_SEI_MESSAGES = 256;

    /** 默认单帧结构化问题上限。 */
    public static final int DEFAULT_MAX_ISSUES = 32;

    /**
     * 校验所有解析上限。
     *
     * @param maxPayloadBytes 单条负载最大字节数
     * @param maxSeiNalUnits 单帧 SEI NAL 单元上限
     * @param maxSeiMessages 单帧 SEI 消息上限
     * @param maxIssues 单帧结构化问题上限
     */
    public SeiParseLimits {
        requirePositive(maxPayloadBytes, "maxPayloadBytes");
        requirePositive(maxSeiNalUnits, "maxSeiNalUnits");
        requirePositive(maxSeiMessages, "maxSeiMessages");
        requirePositive(maxIssues, "maxIssues");
    }

    /**
     * 使用兼容默认数量上限创建解析配置。
     *
     * @param maxPayloadBytes 单条负载最大字节数
     * @return 带默认数量上限的解析配置
     */
    public static SeiParseLimits defaults(int maxPayloadBytes) {
        return new SeiParseLimits(maxPayloadBytes, DEFAULT_MAX_SEI_NAL_UNITS,
                DEFAULT_MAX_SEI_MESSAGES, DEFAULT_MAX_ISSUES);
    }

    /**
     * 校验正整数。
     *
     * @param value 待校验值
     * @param name 参数名
     */
    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
    }
}
