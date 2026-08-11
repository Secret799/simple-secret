package com.ss.netty.session;

import com.ss.netty.auth.NettyWebSocketPrincipal;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** 当前应用实例内的线程安全 Netty WebSocket channel 注册表。 */
public final class NettyWebSocketChannelRegistry {

    private final Map<String, ChannelGroup> pathChannels = new ConcurrentHashMap<>();
    private final Map<String, Channel> sessionChannels = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ChannelGroup>> principalChannels =
            new ConcurrentHashMap<>();

    /** 注册连接，并在 channel 关闭时自动精确清理。 */
    public void register(String path, NettyWebSocketPrincipal principal, Channel channel) {
        String validPath = requirePath(path);
        Channel requiredChannel = Objects.requireNonNull(channel, "channel must not be null");
        if (!requiredChannel.isActive()) {
            throw new IllegalArgumentException("channel must be active");
        }
        String sessionId = requiredChannel.id().asLongText();
        pathChannels.compute(validPath, (ignored, group) -> {
            ChannelGroup target = group == null ? channelGroup() : group;
            target.add(requiredChannel);
            return target;
        });
        sessionChannels.put(sessionId, requiredChannel);
        if (principal != null) {
            principalChannels.compute(validPath, (ignoredPath, keyedGroups) -> {
                Map<String, ChannelGroup> target = keyedGroups == null
                        ? new ConcurrentHashMap<>() : keyedGroups;
                target.compute(principal.sessionKey(), (ignoredKey, group) -> {
                    ChannelGroup keyedTarget = group == null ? channelGroup() : group;
                    keyedTarget.add(requiredChannel);
                    return keyedTarget;
                });
                return target;
            });
        }
        requiredChannel.closeFuture().addListener(ignored -> remove(validPath, principal, requiredChannel));
    }

    /**
     * 精确移除一个连接。
     *
     * @return {@code true} 表示全局 session 索引中确实存在该连接
     */
    public boolean remove(String path, NettyWebSocketPrincipal principal, Channel channel) {
        String validPath = requirePath(path);
        Channel requiredChannel = Objects.requireNonNull(channel, "channel must not be null");
        pathChannels.computeIfPresent(validPath, (ignored, group) -> {
            group.remove(requiredChannel);
            return group.isEmpty() ? null : group;
        });
        if (principal != null) {
            principalChannels.computeIfPresent(validPath, (ignoredPath, keyedGroups) -> {
                keyedGroups.computeIfPresent(principal.sessionKey(), (ignoredKey, group) -> {
                    group.remove(requiredChannel);
                    return group.isEmpty() ? null : group;
                });
                return keyedGroups.isEmpty() ? null : keyedGroups;
            });
        }
        return sessionChannels.remove(requiredChannel.id().asLongText(), requiredChannel);
    }

    /** 向指定路径的全部活动连接提交文本写入，并返回连接数。 */
    public int sendToPath(String path, String message) {
        return write(pathChannels.get(requirePath(path)), message);
    }

    /** 向指定 session 提交文本写入。 */
    public boolean sendToSession(String sessionId, String message) {
        Channel channel = sessionChannels.get(requireText(sessionId, "sessionId"));
        if (channel == null || !channel.isActive()) {
            return false;
        }
        channel.writeAndFlush(new TextWebSocketFrame(
                Objects.requireNonNull(message, "message must not be null")));
        return true;
    }

    /** 向指定路径下同一身份的全部活动连接提交文本写入，并返回连接数。 */
    public int sendToPrincipal(String path, String sessionKey, String message) {
        Map<String, ChannelGroup> keyedGroups = principalChannels.get(requirePath(path));
        if (keyedGroups == null) {
            return 0;
        }
        return write(keyedGroups.get(requireText(sessionKey, "sessionKey")), message);
    }

    /** 向同一身份在全部路径下的活动连接提交文本写入，并返回连接总数。 */
    public int sendToPrincipalAllPaths(String sessionKey, String message) {
        String validKey = requireText(sessionKey, "sessionKey");
        int count = 0;
        for (Map<String, ChannelGroup> keyedGroups : principalChannels.values()) {
            count += write(keyedGroups.get(validKey), message);
        }
        return count;
    }

    /** 返回指定路径当前连接数。 */
    public int countByPath(String path) {
        ChannelGroup group = pathChannels.get(requirePath(path));
        return group == null ? 0 : group.size();
    }

    /** 返回指定路径下同一身份的当前连接数。 */
    public int countByPrincipal(String path, String sessionKey) {
        Map<String, ChannelGroup> keyedGroups = principalChannels.get(requirePath(path));
        if (keyedGroups == null) {
            return 0;
        }
        ChannelGroup group = keyedGroups.get(requireText(sessionKey, "sessionKey"));
        return group == null ? 0 : group.size();
    }

    /** 返回全部当前连接数。 */
    public int totalCount() {
        return sessionChannels.size();
    }

    /** 返回路径到连接数的不可变快照。 */
    public Map<String, Integer> snapshot() {
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        pathChannels.forEach((path, group) -> {
            if (!group.isEmpty()) {
                snapshot.put(path, group.size());
            }
        });
        return Map.copyOf(snapshot);
    }

    private static int write(ChannelGroup group, String message) {
        Objects.requireNonNull(message, "message must not be null");
        if (group == null || group.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Channel channel : group) {
            if (channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(message));
                count++;
            }
        }
        return count;
    }

    private static ChannelGroup channelGroup() {
        return new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    }

    private static String requirePath(String value) {
        String path = requireText(value, "path");
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path must start with '/'");
        }
        return path;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
