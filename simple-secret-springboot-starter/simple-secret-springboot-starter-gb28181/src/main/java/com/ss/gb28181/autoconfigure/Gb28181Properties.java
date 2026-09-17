package com.ss.gb28181.autoconfigure;

import com.ss.gb28181.GbServerOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code simple-secret.gb28181} 下的 GB28181 平台自动配置属性。
 */
@ConfigurationProperties("simple-secret.gb28181")
public class Gb28181Properties {
    /**
     * 是否启动 GB28181 UDP 和 TCP 监听。
     */
    private boolean enabled = false;
    /**
     * 平台的 20 位 SIP 国标编码；启用默认服务时必填。
     */
    private String serverId;
    /**
     * Digest 鉴权 realm；启用默认服务时必填。
     */
    private String realm;
    /**
     * UDP 和 TCP 监听的本地数字 IP 地址。
     */
    private String bindAddress = "127.0.0.1";
    /**
     * 写入 SIP Via 等头部的可达数字 IP 地址。
     */
    private String advertisedAddress = "127.0.0.1";
    /**
     * UDP 和 TCP 共用的 SIP 监听端口。
     */
    private int port = 5060;
    /**
     * 单次注册允许的最大有效期。
     */
    private Duration registrationTtl = Duration.ofDays(1);
    /**
     * 设备心跳间隔。
     */
    private Duration heartbeatInterval = Duration.ofSeconds(60);
    /**
     * 连续缺失多少次心跳后将设备判定为离线。
     */
    private int heartbeatMisses = 3;
    /**
     * MESSAGE 查询/控制接收确认、历史回放 INFO 及报警/目录订阅建立和续订的完成期限。
     */
    private Duration queryTimeout = Duration.ofSeconds(10);
    /** 实时点播 INVITE 协商期限。 */
    private Duration inviteTimeout = Duration.ofSeconds(10);
    /** BYE 及报警/目录退订的停止应答期限。 */
    private Duration stopTimeout = Duration.ofSeconds(5);
    /** 点播会话上限，包含用于处理晚到响应的清理窗口。 */
    private int maxPlaySessions = 256;
    /**
     * 内存中同时保存的最大在线设备数。
     */
    private int maxDevices = 1000;
    /**
     * 目录、录像、设备信息/状态查询、控制接收确认与报警独立响应共享的最大挂起 MESSAGE 数。
     */
    private int maxPendingQueries = 256;
    /**
     * 已接纳且尚未完成的报警回调上限，包含正在执行的回调。
     */
    private int maxPendingAlarms = 256;
    /**
     * 报警订阅上限，包含已停止或中止后仍处于清理窗口的订阅。
     */
    private int maxAlarmSubscriptions = 256;
    /**
     * 目录订阅上限，包含已停止或中止后仍处于清理窗口的订阅，范围 1～10000。
     */
    private int maxCatalogSubscriptions = 256;
    /**
     * 已接纳且尚未完成的目录通知回调上限，包含正在执行的回调，范围 1～10000。
     */
    private int maxPendingCatalogNotifications = 256;
    /**
     * 移动位置订阅上限，包含已停止或中止后仍处于清理窗口的订阅，范围 1～10000。
     */
    private int maxMobilePositionSubscriptions = 256;
    /**
     * 已接纳且尚未完成的移动位置通知回调上限，包含正在执行的回调，范围 1～10000。
     */
    private int maxPendingMobilePositionNotifications = 256;
    /**
     * 单条移动位置通知允许声明的最大条目数，范围 1～10000。
     */
    private int maxMobilePositionItems = 1000;
    /**
     * 单次目录查询允许聚合的最大条目数。
     */
    private int maxCatalogItems = 10000;
    /**
     * 单次录像查询允许聚合的最大条目数，范围 1～100000。
     */
    private int maxRecordItems = 10000;
    /**
     * 单条 SIP 消息允许的最大字节数。
     */
    private int maxMessageBytes = 65536;

    /** @return 是否启用 GB28181 服务 */
    public boolean isEnabled() { return enabled; }
    /** @param value 是否启用 GB28181 服务 */
    public void setEnabled(boolean value) { enabled = value; }
    /** @return 平台 20 位国标编码 */
    public String getServerId() { return serverId; }
    /** @param value 平台 20 位国标编码 */
    public void setServerId(String value) { serverId = value; }
    /** @return Digest 鉴权 realm */
    public String getRealm() { return realm; }
    /** @param value Digest 鉴权 realm */
    public void setRealm(String value) { realm = value; }
    /** @return 本地监听数字 IP 地址 */
    public String getBindAddress() { return bindAddress; }
    /** @param value 本地监听数字 IP 地址 */
    public void setBindAddress(String value) { bindAddress = value; }
    /** @return SIP 报文公布的数字 IP 地址 */
    public String getAdvertisedAddress() { return advertisedAddress; }
    /** @param value SIP 报文公布的数字 IP 地址 */
    public void setAdvertisedAddress(String value) { advertisedAddress = value; }
    /** @return UDP 和 TCP SIP 监听端口 */
    public int getPort() { return port; }
    /** @param value UDP 和 TCP SIP 监听端口 */
    public void setPort(int value) { port = value; }
    /** @return 最大注册有效期 */
    public Duration getRegistrationTtl() { return registrationTtl; }
    /** @param value 最大注册有效期 */
    public void setRegistrationTtl(Duration value) { registrationTtl = value; }
    /** @return 设备心跳间隔 */
    public Duration getHeartbeatInterval() { return heartbeatInterval; }
    /** @param value 设备心跳间隔 */
    public void setHeartbeatInterval(Duration value) { heartbeatInterval = value; }
    /** @return 离线前允许缺失的心跳次数 */
    public int getHeartbeatMisses() { return heartbeatMisses; }
    /** @param value 离线前允许缺失的心跳次数 */
    public void setHeartbeatMisses(int value) { heartbeatMisses = value; }
    /** @return MESSAGE、历史回放 INFO 及订阅建立和续订的完成期限 */
    public Duration getQueryTimeout() { return queryTimeout; }
    /** @param value MESSAGE/历史回放 INFO 请求完成期限 */
    public void setQueryTimeout(Duration value) { queryTimeout = value; }
    public Duration getInviteTimeout() { return inviteTimeout; }
    public void setInviteTimeout(Duration value) { inviteTimeout = value; }
    public Duration getStopTimeout() { return stopTimeout; }
    public void setStopTimeout(Duration value) { stopTimeout = value; }
    public int getMaxPlaySessions() { return maxPlaySessions; }
    public void setMaxPlaySessions(int value) { maxPlaySessions = value; }
    /** @return 最大在线设备数 */
    public int getMaxDevices() { return maxDevices; }
    /** @param value 最大在线设备数 */
    public void setMaxDevices(int value) { maxDevices = value; }
    /** @return 查询、控制确认和报警响应共享的最大并发 MESSAGE 请求数 */
    public int getMaxPendingQueries() { return maxPendingQueries; }
    /** @param value 最大并发 MESSAGE 请求数 */
    public void setMaxPendingQueries(int value) { maxPendingQueries = value; }
    /** @return 已接纳且尚未完成的报警回调上限 */
    public int getMaxPendingAlarms() { return maxPendingAlarms; }
    /** @param value 已接纳且尚未完成的报警回调上限 */
    public void setMaxPendingAlarms(int value) { maxPendingAlarms = value; }
    /** @return 报警订阅及其清理窗口的总上限 */
    public int getMaxAlarmSubscriptions() { return maxAlarmSubscriptions; }
    /** @param value 报警订阅及其清理窗口的总上限 */
    public void setMaxAlarmSubscriptions(int value) { maxAlarmSubscriptions = value; }
    /** @return 目录订阅及其清理窗口的总上限 */
    public int getMaxCatalogSubscriptions() { return maxCatalogSubscriptions; }
    /** @param value 目录订阅及其清理窗口的总上限，范围 1～10000 */
    public void setMaxCatalogSubscriptions(int value) { maxCatalogSubscriptions = value; }
    /** @return 已接纳且尚未完成的目录通知回调上限 */
    public int getMaxPendingCatalogNotifications() { return maxPendingCatalogNotifications; }
    /** @param value 目录通知排队及执行回调上限，范围 1～10000 */
    public void setMaxPendingCatalogNotifications(int value) { maxPendingCatalogNotifications = value; }
    /** @return 移动位置订阅及其清理窗口的总上限 */
    public int getMaxMobilePositionSubscriptions() { return maxMobilePositionSubscriptions; }
    /** @param value 移动位置订阅及其清理窗口的总上限，范围 1～10000 */
    public void setMaxMobilePositionSubscriptions(int value) { maxMobilePositionSubscriptions = value; }
    /** @return 已接纳且尚未完成的移动位置通知回调上限 */
    public int getMaxPendingMobilePositionNotifications() { return maxPendingMobilePositionNotifications; }
    /** @param value 移动位置通知排队及执行回调上限，范围 1～10000 */
    public void setMaxPendingMobilePositionNotifications(int value) {
        maxPendingMobilePositionNotifications = value;
    }
    /** @return 单条移动位置通知最大条目数 */
    public int getMaxMobilePositionItems() { return maxMobilePositionItems; }
    /** @param value 单条移动位置通知最大条目数，范围 1～10000 */
    public void setMaxMobilePositionItems(int value) { maxMobilePositionItems = value; }
    /** @return 单次目录查询最大条目数 */
    public int getMaxCatalogItems() { return maxCatalogItems; }
    /** @param value 单次目录查询最大条目数 */
    public void setMaxCatalogItems(int value) { maxCatalogItems = value; }
    /** @return 单次录像查询最大条目数 */
    public int getMaxRecordItems() { return maxRecordItems; }
    /** @param value 单次录像查询最大条目数 */
    public void setMaxRecordItems(int value) { maxRecordItems = value; }
    /** @return 单条 SIP 消息最大字节数 */
    public int getMaxMessageBytes() { return maxMessageBytes; }
    /** @param value 单条 SIP 消息最大字节数 */
    public void setMaxMessageBytes(int value) { maxMessageBytes = value; }

    GbServerOptions toOptions() {
        return GbServerOptions.builder()
                .serverId(serverId)
                .realm(realm)
                .bindAddress(bindAddress)
                .advertisedAddress(advertisedAddress)
                .port(port)
                .registrationTtl(registrationTtl)
                .heartbeatInterval(heartbeatInterval)
                .heartbeatMisses(heartbeatMisses)
                .queryTimeout(queryTimeout)
                .inviteTimeout(inviteTimeout)
                .stopTimeout(stopTimeout)
                .maxPlaySessions(maxPlaySessions)
                .maxDevices(maxDevices)
                .maxPendingQueries(maxPendingQueries)
                .maxPendingAlarms(maxPendingAlarms)
                .maxAlarmSubscriptions(maxAlarmSubscriptions)
                .maxCatalogSubscriptions(maxCatalogSubscriptions)
                .maxPendingCatalogNotifications(maxPendingCatalogNotifications)
                .maxMobilePositionSubscriptions(maxMobilePositionSubscriptions)
                .maxPendingMobilePositionNotifications(maxPendingMobilePositionNotifications)
                .maxMobilePositionItems(maxMobilePositionItems)
                .maxCatalogItems(maxCatalogItems)
                .maxRecordItems(maxRecordItems)
                .maxMessageBytes(maxMessageBytes)
                .build();
    }
}
