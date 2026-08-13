package com.ss.application.djisei.parser;

import java.util.Locale;
import java.util.Optional;

/**
 * 视频编码格式。
 *
 * @author junpzx
 * @since 2026-08-13
 */
public enum VideoCodec {
    H264,
    H265;

    /**
     * 根据 ZLM 编码名称解析视频编码格式。
     *
     * @param codecName ZLM 返回的编码名称
     * @return 已知编码格式；未知或空名称时为空
     */
    public static Optional<VideoCodec> fromCodecName(String codecName) {
        if (codecName == null) {
            return Optional.empty();
        }
        return switch (codecName.toLowerCase(Locale.ROOT)) {
            case "h264", "avc" -> Optional.of(H264);
            case "h265", "hevc" -> Optional.of(H265);
            default -> Optional.empty();
        };
    }
}
