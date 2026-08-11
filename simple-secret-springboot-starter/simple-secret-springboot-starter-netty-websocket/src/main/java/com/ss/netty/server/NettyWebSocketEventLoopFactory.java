package com.ss.netty.server;

import io.netty.channel.EventLoopGroup;

import java.util.concurrent.ThreadFactory;

/** 创建服务端 event loop；仅用于隔离资源构造边界。 */
@FunctionalInterface
interface NettyWebSocketEventLoopFactory {

    EventLoopGroup create(int threads, ThreadFactory threadFactory);
}
