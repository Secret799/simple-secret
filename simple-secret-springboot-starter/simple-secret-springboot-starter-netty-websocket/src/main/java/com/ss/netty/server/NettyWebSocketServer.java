package com.ss.netty.server;

import com.ss.netty.config.NettyWebSocketProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.springframework.context.SmartLifecycle;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** 由 Spring 生命周期托管的独立 Netty WebSocket 服务。 */
public final class NettyWebSocketServer implements SmartLifecycle {

    private static final System.Logger LOGGER =
            System.getLogger(NettyWebSocketServer.class.getName());

    private final NettyWebSocketProperties properties;
    private final NettyWebSocketChannelInitializer initializer;
    private final NettyWebSocketServerBinder binder;
    private final NettyWebSocketEventLoopFactory eventLoopFactory;
    private volatile EventLoopGroup bossGroup;
    private volatile EventLoopGroup workerGroup;
    private volatile Channel serverChannel;

    /**
     * 创建服务。服务由 Spring 容器或调用方显式启动。
     *
     * @param properties 模块配置
     * @param initializer 通道初始化器
     */
    public NettyWebSocketServer(NettyWebSocketProperties properties,
                                NettyWebSocketChannelInitializer initializer) {
        this(properties, initializer, (bootstrap, address) ->
                bootstrap.bind(address).syncUninterruptibly().channel(),
                NioEventLoopGroup::new);
    }

    NettyWebSocketServer(NettyWebSocketProperties properties,
                         NettyWebSocketChannelInitializer initializer,
                         NettyWebSocketServerBinder binder) {
        this(properties, initializer, binder, NioEventLoopGroup::new);
    }

    NettyWebSocketServer(NettyWebSocketProperties properties,
                         NettyWebSocketChannelInitializer initializer,
                         NettyWebSocketServerBinder binder,
                         NettyWebSocketEventLoopFactory eventLoopFactory) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.initializer = Objects.requireNonNull(initializer, "initializer must not be null");
        this.binder = Objects.requireNonNull(binder, "binder must not be null");
        this.eventLoopFactory = Objects.requireNonNull(
                eventLoopFactory, "eventLoopFactory must not be null");
    }

    /** 同步绑定监听地址；绑定失败会清理已创建的 event loop 并阻止应用启动。 */
    @Override
    public synchronized void start() {
        if (isRunning()) {
            return;
        }
        if (serverChannel != null || bossGroup != null || workerGroup != null) {
            stop();
        }
        EventLoopGroup newBoss = null;
        EventLoopGroup newWorker = null;
        try {
            newBoss = eventLoopFactory.create(
                    properties.getBossThreads(),
                    new NamedDaemonThreadFactory("simple-secret-netty-ws-boss-"));
            newWorker = eventLoopFactory.create(
                    properties.getWorkerThreads(),
                    new NamedDaemonThreadFactory("simple-secret-netty-ws-worker-"));
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(newBoss, newWorker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(initializer)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);
            Channel bound = Objects.requireNonNull(binder.bind(bootstrap, new InetSocketAddress(
                    properties.getHost().trim(), properties.getPort())),
                    "binder returned null channel");
            bossGroup = newBoss;
            workerGroup = newWorker;
            serverChannel = bound;
            LOGGER.log(System.Logger.Level.INFO,
                    "Simple Secret Netty WebSocket listening on {0}", bound.localAddress());
        } catch (RuntimeException failure) {
            try {
                shutdownGroups(newWorker, newBoss, properties.getShutdownTimeout());
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw new IllegalStateException("Failed to start Netty WebSocket server", failure);
        }
    }

    /** 关闭监听 channel，并在配置时限内优雅关闭 event loop。 */
    @Override
    public synchronized void stop() {
        Channel channel = serverChannel;
        EventLoopGroup worker = workerGroup;
        EventLoopGroup boss = bossGroup;
        serverChannel = null;
        workerGroup = null;
        bossGroup = null;
        long timeoutMillis = properties.getShutdownTimeout().toMillis();
        if (channel != null) {
            channel.close().awaitUninterruptibly(timeoutMillis);
        }
        shutdownGroups(worker, boss, properties.getShutdownTimeout());
    }

    /** 停止后执行 Spring 生命周期回调。 */
    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    /** 返回监听 channel 是否活动。 */
    @Override
    public boolean isRunning() {
        Channel channel = serverChannel;
        return channel != null && channel.isActive();
    }

    /**
     * 返回实际绑定地址；随机端口只有启动后可见。
     *
     * @return 返回的 {@code Optional<InetSocketAddress>} 结果
     */
    public Optional<InetSocketAddress> localAddress() {
        Channel channel = serverChannel;
        return channel != null && channel.localAddress() instanceof InetSocketAddress address
                ? Optional.of(address) : Optional.empty();
    }

    /** 允许 Spring 在容器刷新时自动启动。 */
    @Override
    public boolean isAutoStartup() {
        return properties.isAutoStartup();
    }

    /** 让网络监听在普通业务生命周期之后启动、之前停止。 */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    private static void shutdownGroup(EventLoopGroup group, Duration timeout) {
        if (group == null) {
            return;
        }
        long timeoutMillis = timeout.toMillis();
        group.shutdownGracefully(0, timeoutMillis, TimeUnit.MILLISECONDS)
                .awaitUninterruptibly(timeoutMillis);
    }

    private static void shutdownGroups(EventLoopGroup worker, EventLoopGroup boss,
                                       Duration timeout) {
        try {
            shutdownGroup(worker, timeout);
        } finally {
            shutdownGroup(boss, timeout);
        }
    }
}
