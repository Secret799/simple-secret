package com.ss.camera.service.hikvision;

import com.ss.camera.domain.StreamUrlAssemblyDomain;
import com.ss.camera.enums.CameraBrandEnums;
import com.ss.camera.enums.CameraTypeEnums;
import com.ss.camera.service.BaseUrlAssemblyServiceImpl;

/** 组装海康威视 NVR 的实时 RTSP 地址。 */
public final class HikNvrUrlAssemblyService extends BaseUrlAssemblyServiceImpl {

    @Override
    public String brand() {
        return CameraBrandEnums.HIKVISION.getCode();
    }

    @Override
    public String type() {
        return CameraTypeEnums.NVR.getCode();
    }

    @Override
    public String assembly(StreamUrlAssemblyDomain domain) {
        String channel = channelNo(domain);
        if (channel.length() > 2 && (channel.endsWith("01") || channel.endsWith("02"))) {
            channel = channel.substring(0, channel.length() - 2);
        }
        String suffix = "main".equals(streamType(domain)) ? "01" : "02";
        return "rtsp://" + authority(domain) + "/Streaming/Channels/"
                + channel + suffix + "?transportmode=multicast";
    }
}
