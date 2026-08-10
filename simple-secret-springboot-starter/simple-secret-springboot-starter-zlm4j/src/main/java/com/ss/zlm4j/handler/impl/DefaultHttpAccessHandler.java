package com.ss.zlm4j.handler.impl;

import com.aizuda.zlm4j.structure.MK_HTTP_ACCESS_PATH_INVOKER;
import com.aizuda.zlm4j.structure.MK_PARSER;
import com.aizuda.zlm4j.structure.MK_SOCK_INFO;
import com.ss.zlm4j.handler.HttpAccessHandler;

/**
 * 在http文件服务器中,收到http访问文件或目录的广播,通过该事件控制访问http目录的权限
 *
 * @author JunPzx
 * @since 2025/8/21 14:14
 */
public class DefaultHttpAccessHandler extends AbstractCallbackHandler implements HttpAccessHandler {

    /**
     * 在http文件服务器中,收到http访问文件或目录的广播,通过该事件控制访问http目录的权限
     *
     * @param parser  http请求内容对象
     * @param path    文件绝对路径
     * @param isDir   path是否为文件夹
     * @param invoker 执行invoker返回本次访问文件的结果
     * @param sender  http客户端相关信息
     */
    @Override
    public void handle(MK_PARSER parser, String path, int isDir, MK_HTTP_ACCESS_PATH_INVOKER invoker, MK_SOCK_INFO sender) {

    }


}
