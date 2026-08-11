package com.ss.camera.enums;

/** 摄像机厂商品牌。 */
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

    /** @return 稳定的品牌编码 */
    public String getCode() {
        return code;
    }

    /** @return 品牌显示名称 */
    public String getName() {
        return name;
    }
}
