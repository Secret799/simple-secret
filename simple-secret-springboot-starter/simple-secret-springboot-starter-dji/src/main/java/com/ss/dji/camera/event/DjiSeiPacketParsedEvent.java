package com.ss.dji.camera.event;

import com.ss.dji.camera.parser.SeiMessage;
import com.ss.dji.camera.parser.VideoCodec;
import com.ss.easymedia.callback.TrackDelegateCallback.TackDelegateInfo;
import com.ss.zlm4j.domain.MediaSourceDomain;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 一条完整 SEI 消息完成解析后发布的 Spring 应用事件。
 *
 * <p>事件保存完整 payload 并执行防御性复制，不受日志预览长度限制。</p>
 */
public final class DjiSeiPacketParsedEvent extends ApplicationEvent {

    private final Instant parsedAt;
    private final String schema;
    private final String app;
    private final String stream;
    private final String vhost;
    private final VideoCodec codec;
    private final Long pts;
    private final Long dts;
    private final int payloadType;
    private final byte[] payload;
    private final UUID uuid;

    /**
     * 创建完整 SEI 解析事件。
     *
     * @param mediaSource 媒体源
     * @param frame SEI 所在视频帧
     * @param codec 视频编码
     * @param message 已完整解析的 SEI 消息
     * @param parsedAt 解析完成时间
     */
    public DjiSeiPacketParsedEvent(MediaSourceDomain mediaSource, TackDelegateInfo frame,
                                   VideoCodec codec, SeiMessage message, Instant parsedAt) {
        super(Objects.requireNonNull(mediaSource, "mediaSource"));
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(message, "message");
        this.parsedAt = Objects.requireNonNull(parsedAt, "parsedAt");
        this.schema = mediaSource.getSchema();
        this.app = mediaSource.getApp();
        this.stream = mediaSource.getStream();
        this.vhost = mediaSource.getVhost();
        this.codec = Objects.requireNonNull(codec, "codec");
        this.pts = frame.getPts();
        this.dts = frame.getDts();
        this.payloadType = message.payloadType();
        this.payload = message.payload();
        this.uuid = message.uuid().orElse(null);
    }

    /** @return SEI 解析完成时间 */
    public Instant getParsedAt() {
        return parsedAt;
    }

    /** @return 媒体源协议 */
    public String getSchema() {
        return schema;
    }

    /** @return 媒体应用名 */
    public String getApp() {
        return app;
    }

    /** @return 媒体流 ID */
    public String getStream() {
        return stream;
    }

    /** @return ZLMediaKit 虚拟主机 */
    public String getVhost() {
        return vhost;
    }

    /** @return 视频编码 */
    public VideoCodec getCodec() {
        return codec;
    }

    /** @return 显示时间戳，单位毫秒 */
    public Long getPts() {
        return pts;
    }

    /** @return 解码时间戳，单位毫秒 */
    public Long getDts() {
        return dts;
    }

    /** @return SEI payload type */
    public int getPayloadType() {
        return payloadType;
    }

    /** @return 完整 payload 字节数 */
    public int getPayloadBytes() {
        return payload.length;
    }

    /** @return 完整 payload 的防御性副本 */
    public byte[] getPayload() {
        return Arrays.copyOf(payload, payload.length);
    }

    /** @return 用户数据未注册 SEI 的 UUID；其他类型为空 */
    public Optional<UUID> getUuid() {
        return Optional.ofNullable(uuid);
    }
}
