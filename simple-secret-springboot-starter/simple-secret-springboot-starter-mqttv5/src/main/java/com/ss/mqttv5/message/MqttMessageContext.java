package com.ss.mqttv5.message;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ss.json.utils.JsonUtils;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * MQTT 入站消息及其客户端、订阅元数据。
 */
public final class MqttMessageContext {
    private final String clientKey;
    private final String clientId;
    private final String shareGroup;
    private final String subscribeTopic;
    private final String topic;
    private final byte[] payload;
    private final int qos;
    private final boolean retained;
    private final boolean duplicate;
    private final int messageId;
    private final MqttProperties properties;

    /**
     * 创建入站消息上下文。
     *
     * @param clientKey     客户端键
     * @param clientId      Paho 客户端 ID
     * @param shareGroup    共享组名称
     * @param subscribeTopic 处理器订阅过滤器
     * @param topic         实际消息主题
     * @param message       Paho 消息
     */
    public MqttMessageContext(String clientKey, String clientId, String shareGroup,
                              String subscribeTopic, String topic, MqttMessage message) {
        this.clientKey = Objects.requireNonNull(clientKey, "clientKey");
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.shareGroup = shareGroup == null ? "" : shareGroup;
        this.subscribeTopic = Objects.requireNonNull(subscribeTopic, "subscribeTopic");
        this.topic = Objects.requireNonNull(topic, "topic");
        MqttMessage source = Objects.requireNonNull(message, "message");
        this.payload = source.getPayload().clone();
        this.qos = source.getQos();
        this.retained = source.isRetained();
        this.duplicate = source.isDuplicate();
        this.messageId = source.getId();
        this.properties = copyProperties(source.getProperties());
    }

    /** @return 客户端键 */
    public String getClientKey() {
        return clientKey;
    }

    /** @return Paho 客户端 ID */
    public String getClientId() {
        return clientId;
    }

    /** @return 共享组名称 */
    public String getShareGroup() {
        return shareGroup;
    }

    /** @return 处理器订阅过滤器 */
    public String getSubscribeTopic() {
        return subscribeTopic;
    }

    /** @return 实际消息主题 */
    public String getTopic() {
        return topic;
    }

    /**
     * 返回当前快照的只读 Paho 消息副本。
     *
     * @return 与其他处理器隔离的消息副本
     */
    public MqttMessage getMessage() {
        MqttMessage copy = new MqttMessage(payload.clone(), qos, retained, copyProperties(properties));
        copy.setDuplicate(duplicate);
        copy.setId(messageId);
        copy.setMutable(false);
        return copy;
    }

    /**
     * 将 payload 按 UTF-8 解码。
     *
     * @return 消息字符串
     */
    public String getPayloadAsString() {
        return new String(payload, StandardCharsets.UTF_8);
    }

    /**
     * 将 JSON payload 转换为指定类型。
     *
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 转换结果
     */
    public <T> T getPayload(Class<T> type) {
        return JsonUtils.parseObject(getPayloadAsString(), type);
    }

    /**
     * 按泛型类型信息转换 JSON payload。
     *
     * @param type 目标泛型类型
     * @param <T>  目标类型
     * @return 转换结果
     */
    public <T> T getPayload(TypeReference<T> type) {
        return JsonUtils.parseObject(getPayloadAsString(), type);
    }

    private static MqttProperties copyProperties(MqttProperties source) {
        if (source == null) {
            return null;
        }
        MqttProperties copy = new MqttProperties();
        copy.setPayloadFormat(source.getPayloadFormat());
        copy.setMessageExpiryInterval(source.getMessageExpiryInterval());
        copy.setContentType(source.getContentType());
        copy.setResponseTopic(source.getResponseTopic());
        byte[] correlationData = source.getCorrelationData();
        copy.setCorrelationData(correlationData == null ? null : correlationData.clone());
        if (source.getUserProperties() != null) {
            copy.setUserProperties(source.getUserProperties().stream()
                    .map(property -> new UserProperty(property.getKey(), property.getValue()))
                    .toList());
        }
        if (source.getSubscriptionIdentifiers() != null) {
            copy.setSubscriptionIdentifiers(List.copyOf(source.getSubscriptionIdentifiers()));
        }
        if (source.getSubscriptionIdentifier() != null) {
            copy.setSubscriptionIdentifier(source.getSubscriptionIdentifier());
        }
        return copy;
    }
}
