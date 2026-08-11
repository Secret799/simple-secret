package com.ss.camera.service.dahua;

import com.ss.camera.domain.StreamUrlAssemblyDomain;
import com.ss.camera.enums.CameraBrandEnums;
import com.ss.camera.enums.CameraTypeEnums;
import com.ss.camera.service.BaseUrlAssemblyServiceImpl;

/** 组装大华 NVR 的实时 RTSP 地址。 */
public final class DahuaNvrUrlAssemblyService extends BaseUrlAssemblyServiceImpl {

    @Override
    public String brand() {
        return CameraBrandEnums.DAHUA.getCode();
    }

    @Override
    public String type() {
        return CameraTypeEnums.NVR.getCode();
    }

    @Override
    public String assembly(StreamUrlAssemblyDomain domain) {
        String subtype = "main".equals(streamType(domain)) ? "0" : "1";
        return "rtsp://" + authority(domain) + "/cam/realmonitor?channel="
                + channelNo(domain) + "&subtype=" + subtype;
    }
}
