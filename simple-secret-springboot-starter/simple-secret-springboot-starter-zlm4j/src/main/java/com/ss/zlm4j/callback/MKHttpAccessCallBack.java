package com.ss.zlm4j.callback;

import java.util.Optional;

import com.aizuda.zlm4j.callback.IMKHttpAccessCallBack;
import com.aizuda.zlm4j.structure.MK_HTTP_ACCESS_PATH_INVOKER;
import com.aizuda.zlm4j.structure.MK_PARSER;
import com.aizuda.zlm4j.structure.MK_SOCK_INFO;
import com.ss.zlm4j.handler.HttpAccessHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 在http文件服务器中,收到http访问文件或目录的广播,通过该事件控制访问http目录的权限
 *
 * @author junpzx
 * @since 2024-06-12 13:49
 */
@Slf4j
@RequiredArgsConstructor
public class MKHttpAccessCallBack implements IMKHttpAccessCallBack {

    private final HttpAccessHandler handler;

    /**
     * 在http文件服务器中,收到http访问文件或目录的广播,通过该事件控制访问http目录的权限
     *
     * @param parser  http请求内容对象
     * @param path    文件绝对路径
     * @param invoker 执行invoker返回本次访问文件的结果
     * @param sender  http客户端相关信息
     */
    @Override
    public void invoke(MK_PARSER parser, String path, int isDir, MK_HTTP_ACCESS_PATH_INVOKER invoker, MK_SOCK_INFO sender) {
        log.info("【SimpleSecretZLMediaKit】HTTP访问文件或者目录事件 回调开始");
        try {
            Optional.ofNullable(handler).ifPresent(t -> t.handle(parser, path, isDir, invoker, sender));
        } catch (Exception e) {
            log.error("【SimpleSecretZLMediaKit】HTTP访问文件或者目录事件 回调处理器发生异常", e);
        }
        log.info("【SimpleSecretZLMediaKit】HTTP访问文件或者目录事件 回调结束");
    }
}