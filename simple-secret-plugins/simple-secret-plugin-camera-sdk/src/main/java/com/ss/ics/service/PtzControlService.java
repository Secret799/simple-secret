package com.ss.ics.service;

import com.ss.ics.domain.DeviceDomain;
import com.ss.ics.domain.PTZControlDomain;
import com.ss.ics.exception.UnsupportedCameraSdkOperationException;

/** 厂商 SDK 云台控制能力。 */
public interface PtzControlService extends CameraSdkService {

    /**
     * 异步提交云台命令。
     *
     * @param device 设备
     * @param control 控制参数
     * @return 是否成功提交异步任务；{@code true} 不代表设备已经执行成功
     */
    default boolean asyncControl(DeviceDomain device, PTZControlDomain control) {
        throw new UnsupportedCameraSdkOperationException("PTZ asynchronous control is not supported");
    }

    /**
     * 同步执行云台命令。
     *
     * @param device 设备
     * @param control 控制参数
     * @return 命令执行结果
     */
    default boolean syncControl(DeviceDomain device, PTZControlDomain control) {
        throw new UnsupportedCameraSdkOperationException("PTZ synchronous control is not supported");
    }
}
