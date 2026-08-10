package com.ss.zlm4j.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 协议枚举
 *
 * @author JunPzx
 * @since 2025/8/26 16:18
 */
@Getter
@RequiredArgsConstructor
public enum SchemeEnum {
    TS,
    FMP4,
    MP4,
    HLS,
    RTSP,
    RTMP,
    HLS_FMP4;

    /**
     * 根据code获取枚举
     *
     * @param code  code
     * @return 枚举
     */
    public static SchemeEnum getByCode(String code) {
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(code))
                .findAny()
                .orElse(null);
    }

    /**
     * 根据code获取枚举
     *
     * @param codes code
     * @return 枚举
     */
    public static Set<SchemeEnum> listByCodes(String... codes) {
        return Stream.of(codes)
                .map(SchemeEnum::getByCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
