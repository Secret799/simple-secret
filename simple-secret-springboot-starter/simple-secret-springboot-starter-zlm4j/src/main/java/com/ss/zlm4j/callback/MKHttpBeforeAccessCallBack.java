package com.ss.zlm4j.callback;

import java.util.Optional;

import com.aizuda.zlm4j.callback.IMKHttpBeforeAccessCallBack;
import com.aizuda.zlm4j.structure.MK_PARSER;
import com.aizuda.zlm4j.structure.MK_SOCK_INFO;
import com.ss.zlm4j.handler.HttpBeforeAccessHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 在http文件服务器中,收到http访问文件或目录前的广播,通过该事件可以控制http url到文件路径的映射
 *
 * @author junpzx
 * @since 2024-06-12 13:49
 */
@Slf4j
@RequiredArgsConstructor
public class MKHttpBeforeAccessCallBack implements IMKHttpBeforeAccessCallBack {

    private final HttpBeforeAccessHandler handler;

    /**
     * 在http文件服务器中,收到http访问文件或目录前的广播,通过该事件可以控制http url到文件路径的映射
     * 在该事件中通过自行覆盖path参数，可以做到譬如根据虚拟主机或者app选择不同http根目录的目的
     *
     * @param parser http请求内容对象
     * @param path   文件绝对路径,覆盖之可以重定向到其他文件
     * @param sender http客户端相关信息
     */
    @Override
    public void invoke(MK_PARSER parser, String path, MK_SOCK_INFO sender) {
        log.info("【SimpleSecretZLMediaKit】HTTP访问文件或者目录之前事件 回调开始");
        try {
            Optional.ofNullable(handler).ifPresent(t -> t.handle(parser, path, sender));
        } catch (Exception e) {
            log.error("【SimpleSecretZLMediaKit】HTTP访问文件或者目录之前事件 回调处理器发生异常", e);
        }
        log.info("【SimpleSecretZLMediaKit】HTTP访问文件或者目录之前事件 回调结束");
    }
}