package com.ss.ics.constants;

/** 摄像机厂商 SDK 品牌编码。 */
public enum CameraBrandEnums {
    /** 海康威视。 */
    HIKVISION("Hikvision", "海康威视"),
    /** 大华。 */
    DAHUA("Dahua", "大华");

    private final String code;
    private final String name;

    CameraBrandEnums(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /** @return 厂商服务使用的稳定产品编码 */
    public String getCode() {
        return code;
    }

    /** @return 厂商显示名称 */
    public String getName() {
        return name;
    }
}
