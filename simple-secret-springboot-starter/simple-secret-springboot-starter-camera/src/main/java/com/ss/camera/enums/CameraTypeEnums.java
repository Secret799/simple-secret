package com.ss.camera.enums;

/** 摄像机设备类型。 */
public enum CameraTypeEnums {
    /** 独立摄像机。 */
    CAMERA("CAMERA", "摄像头"),
    /** 网络硬盘录像机。 */
    NVR("NVR", "NVR");

    private final String code;
    private final String name;

    CameraTypeEnums(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /** @return 稳定的设备类型编码 */
    public String getCode() {
        return code;
    }

    /** @return 设备类型显示名称 */
    public String getName() {
        return name;
    }
}
