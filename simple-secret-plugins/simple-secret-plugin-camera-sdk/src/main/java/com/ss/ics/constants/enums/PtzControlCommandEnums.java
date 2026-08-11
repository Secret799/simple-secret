package com.ss.ics.constants.enums;

/** 摄像机云台控制命令。 */
public enum PtzControlCommandEnums {
    /** 向上。 */
    UP("0", "上"),
    /** 向下。 */
    DOWN("1", "下"),
    /** 向左。 */
    LEFT("2", "左"),
    /** 向右。 */
    RIGHT("3", "右"),
    /** 左上。 */
    LEFT_UP("5", "左上"),
    /** 右下。 */
    RIGHT_DOWN("6", "右下"),
    /** 左下。 */
    LEFT_DOWN("7", "左下"),
    /** 右上。 */
    RIGHT_UP("8", "右上"),
    /** 放大。 */
    ZOOM_IN("9", "焦距变大"),
    /** 缩小。 */
    ZOOM_OUT("10", "焦距变小"),
    /** 近焦。 */
    FOCUS_NEAR("11", "焦点前调"),
    /** 远焦。 */
    FOCUS_FAR("12", "焦点后调"),
    /** 增大光圈。 */
    IRIS_OPEN("13", "光圈扩大"),
    /** 缩小光圈。 */
    IRIS_CLOSE("14", "光圈缩小");

    private final String code;
    private final String name;

    PtzControlCommandEnums(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /** @return Honeybee 兼容命令编码 */
    public String getCode() {
        return code;
    }

    /** @return 命令显示名称 */
    public String getName() {
        return name;
    }

    /**
     * 按编码查找命令。
     *
     * @param code 命令编码
     * @return 对应命令；不存在时返回 {@code null}
     */
    public static PtzControlCommandEnums getByCode(String code) {
        for (PtzControlCommandEnums command : values()) {
            if (command.code.equals(code)) {
                return command;
            }
        }
        return null;
    }
}
