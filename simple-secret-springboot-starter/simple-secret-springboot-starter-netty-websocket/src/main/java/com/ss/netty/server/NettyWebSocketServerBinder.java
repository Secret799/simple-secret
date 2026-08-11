package com.ss.netty.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;

/** 隔离 Netty bind 操作，便于在禁止监听端口的环境验证生命周期。 */
@FunctionalInterface
interface NettyWebSocketServerBinder {

    Channel bind(ServerBootstrap bootstrap, InetSocketAddress address);
}
