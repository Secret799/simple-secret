package com.ss.zlm4j.handler;

import com.aizuda.zlm4j.structure.MK_MEDIA_INFO;
import com.aizuda.zlm4j.structure.MK_SOCK_INFO;
import com.ss.zlm4j.domain.MediaInfoDomain;
import com.ss.zlm4j.domain.SocketInfoDomain;
import com.ss.zlm4j.helper.ZlmMediaHelper;

/**
 * Zlm 流未找到作用域处理器（可用于未找到流后按需拉流）
 *
 * @author JunPzx
 * @since 2025/8/20 09:17
 */
public interface StreamNoFoundHandler {

    /**
     * 处理流未找到（注册流处理）
     *
     * @param mediaInfoDomain  媒体信息
     * @param socketInfoDomain 连接客户端信息
     * @return 1：直接关闭 0：等待流注册
     */
    default int handle(MediaInfoDomain mediaInfoDomain, SocketInfoDomain socketInfoDomain) {
        return 1;
    }

    /**
     * 处理流未找到（注册流处理）
     *
     * @param urlInfo 媒体信息
     * @param sender  连接客户端信息
     * @return 1：直接关闭 0：等待流注册
     */
    default int handle(MK_MEDIA_INFO urlInfo, MK_SOCK_INFO sender) {
        return handle(ZlmMediaHelper.Assembler.getMediaInfo(urlInfo), ZlmMediaHelper.Assembler.getSocketInfo(sender));
    }

}
