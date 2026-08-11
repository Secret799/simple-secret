package com.ss.camera.service.hikvision;

import com.ss.camera.domain.StreamUrlAssemblyDomain;
import com.ss.camera.enums.CameraBrandEnums;
import com.ss.camera.enums.CameraTypeEnums;
import com.ss.camera.service.BaseUrlAssemblyServiceImpl;

/** 组装海康威视独立摄像机的 RTSP 地址。 */
public final class HikCameraUrlAssemblyService extends BaseUrlAssemblyServiceImpl {

    @Override
    public String brand() {
        return CameraBrandEnums.HIKVISION.getCode();
    }

    @Override
    public String type() {
        return CameraTypeEnums.CAMERA.getCode();
    }

    @Override
    public String assembly(StreamUrlAssemblyDomain domain) {
        return "rtsp://" + authority(domain) + "/h264/ch" + channelNo(domain)
                + "/" + streamType(domain) + "/av_stream";
    }
}
