package com.ss.camera.service;

import com.ss.camera.domain.StreamUrlAssemblyDomain;

/** 根据设备品牌和类型组装 RTSP 地址。 */
public interface UrlAssemblyService {

    /** @return 服务支持的品牌编码 */
    String brand();

    /** @return 服务支持的设备类型编码 */
    String type();

    /**
     * 组装 RTSP 地址。
     *
     * @param domain 地址参数
     * @return 完整 RTSP 地址
     * @throws IllegalArgumentException 参数不合法时抛出
     */
    String assembly(StreamUrlAssemblyDomain domain);
}
