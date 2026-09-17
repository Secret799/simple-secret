package com.ss.gb28181;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

/** Validated immutable listener and capacity settings. Both UDP and TCP use the configured port. */
public record GbServerOptions(
        String serverId,
        String realm,
        String bindAddress,
        String advertisedAddress,
        int port,
        Duration registrationTtl,
        Duration heartbeatInterval,
        int heartbeatMisses,
        Duration queryTimeout,
        int maxDevices,
        int maxPendingQueries,
        int maxCatalogItems,
        int maxMessageBytes,
        Duration inviteTimeout,
        Duration stopTimeout,
        int maxPlaySessions,
        int maxRecordItems,
        int maxPendingAlarms,
        int maxAlarmSubscriptions,
        int maxCatalogSubscriptions,
        int maxPendingCatalogNotifications,
        int maxMobilePositionSubscriptions,
        int maxPendingMobilePositionNotifications,
        int maxMobilePositionItems) {
    public GbServerOptions {
        if (serverId == null || !serverId.matches("[0-9]{20}"))
            throw new IllegalArgumentException("serverId must contain 20 digits");
        if (realm == null || !realm.matches("[a-zA-Z0-9.-]{1,128}"))
            throw new IllegalArgumentException("realm must be a SIP domain without whitespace");
        address(bindAddress, false);
        address(advertisedAddress, true);
        range(port, 1, 65535, "port");
        duration(registrationTtl, Duration.ofSeconds(1), Duration.ofDays(30), "registrationTtl");
        if (registrationTtl.getNano() != 0) throw new IllegalArgumentException("registrationTtl must use whole seconds");
        duration(heartbeatInterval, Duration.ofMillis(100), Duration.ofHours(1), "heartbeatInterval");
        range(heartbeatMisses, 1, 100, "heartbeatMisses");
        duration(queryTimeout, Duration.ofMillis(50), Duration.ofMinutes(10), "queryTimeout");
        range(maxDevices, 1, 10000, "maxDevices");
        range(maxPendingQueries, 1, 10000, "maxPendingQueries");
        range(maxCatalogItems, 1, 100000, "maxCatalogItems");
        range(maxMessageBytes, 4096, 1048576, "maxMessageBytes");
        duration(inviteTimeout, Duration.ofMillis(100), Duration.ofMinutes(2), "inviteTimeout");
        duration(stopTimeout, Duration.ofMillis(100), Duration.ofSeconds(30), "stopTimeout");
        range(maxPlaySessions, 1, 9999, "maxPlaySessions");
        range(maxRecordItems, 1, 100000, "maxRecordItems");
        range(maxPendingAlarms, 1, 10000, "maxPendingAlarms");
        range(maxAlarmSubscriptions, 1, 10000, "maxAlarmSubscriptions");
        range(maxCatalogSubscriptions, 1, 10000, "maxCatalogSubscriptions");
        range(maxPendingCatalogNotifications, 1, 10000, "maxPendingCatalogNotifications");
        range(maxMobilePositionSubscriptions, 1, 10000, "maxMobilePositionSubscriptions");
        range(maxPendingMobilePositionNotifications, 1, 10000, "maxPendingMobilePositionNotifications");
        range(maxMobilePositionItems, 1, 10000, "maxMobilePositionItems");
    }
    /** Source/binary-compatible constructor preceding MobilePosition support. */
    public GbServerOptions(String serverId, String realm, String bindAddress, String advertisedAddress, int port,
                           Duration registrationTtl, Duration heartbeatInterval, int heartbeatMisses,
                           Duration queryTimeout, int maxDevices, int maxPendingQueries, int maxCatalogItems,
                           int maxMessageBytes, Duration inviteTimeout, Duration stopTimeout, int maxPlaySessions,
                           int maxRecordItems, int maxPendingAlarms, int maxAlarmSubscriptions,
                           int maxCatalogSubscriptions, int maxPendingCatalogNotifications) {
        this(serverId, realm, bindAddress, advertisedAddress, port, registrationTtl, heartbeatInterval, heartbeatMisses,
                queryTimeout, maxDevices, maxPendingQueries, maxCatalogItems, maxMessageBytes,
                inviteTimeout, stopTimeout, maxPlaySessions, maxRecordItems, maxPendingAlarms, maxAlarmSubscriptions,
                maxCatalogSubscriptions, maxPendingCatalogNotifications, 256, 256, 1000);
    }
    /** Source/binary-compatible constructor for applications using registration/catalog settings. */
    public GbServerOptions(String serverId, String realm, String bindAddress, String advertisedAddress, int port,
                           Duration registrationTtl, Duration heartbeatInterval, int heartbeatMisses,
                           Duration queryTimeout, int maxDevices, int maxPendingQueries, int maxCatalogItems,
                           int maxMessageBytes) {
        this(serverId, realm, bindAddress, advertisedAddress, port, registrationTtl, heartbeatInterval, heartbeatMisses,
                queryTimeout, maxDevices, maxPendingQueries, maxCatalogItems, maxMessageBytes,
                Duration.ofSeconds(10), Duration.ofSeconds(5), 256);
    }
    /** Source/binary-compatible constructor for applications using live-play settings. */
    public GbServerOptions(String serverId, String realm, String bindAddress, String advertisedAddress, int port,
                           Duration registrationTtl, Duration heartbeatInterval, int heartbeatMisses,
                           Duration queryTimeout, int maxDevices, int maxPendingQueries, int maxCatalogItems,
                           int maxMessageBytes, Duration inviteTimeout, Duration stopTimeout, int maxPlaySessions) {
        this(serverId, realm, bindAddress, advertisedAddress, port, registrationTtl, heartbeatInterval, heartbeatMisses,
                queryTimeout, maxDevices, maxPendingQueries, maxCatalogItems, maxMessageBytes,
                inviteTimeout, stopTimeout, maxPlaySessions, 10000);
    }
    /** Source/binary-compatible constructor for applications using record-query settings. */
    public GbServerOptions(String serverId, String realm, String bindAddress, String advertisedAddress, int port,
                           Duration registrationTtl, Duration heartbeatInterval, int heartbeatMisses,
                           Duration queryTimeout, int maxDevices, int maxPendingQueries, int maxCatalogItems,
                           int maxMessageBytes, Duration inviteTimeout, Duration stopTimeout, int maxPlaySessions,
                           int maxRecordItems) {
        this(serverId, realm, bindAddress, advertisedAddress, port, registrationTtl, heartbeatInterval, heartbeatMisses,
                queryTimeout, maxDevices, maxPendingQueries, maxCatalogItems, maxMessageBytes,
                inviteTimeout, stopTimeout, maxPlaySessions, maxRecordItems, 256);
    }
    /** Source/binary-compatible constructor for applications using alarm callback settings. */
    public GbServerOptions(String serverId, String realm, String bindAddress, String advertisedAddress, int port,
                           Duration registrationTtl, Duration heartbeatInterval, int heartbeatMisses,
                           Duration queryTimeout, int maxDevices, int maxPendingQueries, int maxCatalogItems,
                           int maxMessageBytes, Duration inviteTimeout, Duration stopTimeout, int maxPlaySessions,
                           int maxRecordItems, int maxPendingAlarms) {
        this(serverId, realm, bindAddress, advertisedAddress, port, registrationTtl, heartbeatInterval, heartbeatMisses,
                queryTimeout, maxDevices, maxPendingQueries, maxCatalogItems, maxMessageBytes,
                inviteTimeout, stopTimeout, maxPlaySessions, maxRecordItems, maxPendingAlarms, 256);
    }
    /** Source/binary-compatible constructor for applications using alarm subscription settings. */
    public GbServerOptions(String serverId, String realm, String bindAddress, String advertisedAddress, int port,
                           Duration registrationTtl, Duration heartbeatInterval, int heartbeatMisses,
                           Duration queryTimeout, int maxDevices, int maxPendingQueries, int maxCatalogItems,
                           int maxMessageBytes, Duration inviteTimeout, Duration stopTimeout, int maxPlaySessions,
                           int maxRecordItems, int maxPendingAlarms, int maxAlarmSubscriptions) {
        this(serverId, realm, bindAddress, advertisedAddress, port, registrationTtl, heartbeatInterval, heartbeatMisses,
                queryTimeout, maxDevices, maxPendingQueries, maxCatalogItems, maxMessageBytes,
                inviteTimeout, stopTimeout, maxPlaySessions, maxRecordItems, maxPendingAlarms, maxAlarmSubscriptions,
                256, 256);
    }
    private static void address(String value, boolean advertised) {
        if (value == null || value.length() > 45 || !value.matches("[0-9a-fA-F:.]+")
                || (!value.contains(":") && !value.matches("[0-9]+(?:\\.[0-9]+){3}")))
            throw new IllegalArgumentException("SIP address must be a numeric IP address");
        try {
            InetAddress ip = InetAddress.getByName(value);
            if (ip.isMulticastAddress() || (advertised && ip.isAnyLocalAddress()))
                throw new IllegalArgumentException("SIP address is not a unicast endpoint");
        } catch (UnknownHostException e) { throw new IllegalArgumentException("Invalid SIP IP address"); }
    }
    private static void range(int value, int min, int max, String name) {
        if (value < min || value > max) throw new IllegalArgumentException(name + " outside supported range");
    }
    private static void duration(Duration value, Duration min, Duration max, String name) {
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0)
            throw new IllegalArgumentException(name + " outside supported range");
    }
    public static Builder builder() { return new Builder(); }
    public static final class Builder {
        private String serverId = null;
        private String realm = null;
        private String bindAddress = "127.0.0.1";
        private String advertisedAddress = "127.0.0.1";
        private int port = 5060;
        private Duration registrationTtl = Duration.ofDays(1);
        private Duration heartbeatInterval = Duration.ofSeconds(60);
        private int heartbeatMisses = 3;
        private Duration queryTimeout = Duration.ofSeconds(10);
        private int maxDevices = 1000;
        private int maxPendingQueries = 256;
        private int maxCatalogItems = 10000;
        private int maxMessageBytes = 65536;
        private Duration inviteTimeout = Duration.ofSeconds(10);
        private Duration stopTimeout = Duration.ofSeconds(5);
        private int maxPlaySessions = 256;
        private int maxRecordItems = 10000;
        private int maxPendingAlarms = 256;
        private int maxAlarmSubscriptions = 256;
        private int maxCatalogSubscriptions = 256;
        private int maxPendingCatalogNotifications = 256;
        private int maxMobilePositionSubscriptions = 256;
        private int maxPendingMobilePositionNotifications = 256;
        private int maxMobilePositionItems = 1000;
        public Builder serverId(String value) { this.serverId = value; return this; }
        public Builder realm(String value) { this.realm = value; return this; }
        public Builder bindAddress(String value) { this.bindAddress = value; return this; }
        public Builder advertisedAddress(String value) { this.advertisedAddress = value; return this; }
        public Builder port(int value) { this.port = value; return this; }
        public Builder registrationTtl(Duration value) { this.registrationTtl = value; return this; }
        public Builder heartbeatInterval(Duration value) { this.heartbeatInterval = value; return this; }
        public Builder heartbeatMisses(int value) { this.heartbeatMisses = value; return this; }
        public Builder queryTimeout(Duration value) { this.queryTimeout = value; return this; }
        public Builder maxDevices(int value) { this.maxDevices = value; return this; }
        public Builder maxPendingQueries(int value) { this.maxPendingQueries = value; return this; }
        public Builder maxCatalogItems(int value) { this.maxCatalogItems = value; return this; }
        public Builder maxMessageBytes(int value) { this.maxMessageBytes = value; return this; }
        public Builder inviteTimeout(Duration value) { this.inviteTimeout = value; return this; }
        public Builder stopTimeout(Duration value) { this.stopTimeout = value; return this; }
        public Builder maxPlaySessions(int value) { this.maxPlaySessions = value; return this; }
        public Builder maxRecordItems(int value) { this.maxRecordItems = value; return this; }
        public Builder maxPendingAlarms(int value) { this.maxPendingAlarms = value; return this; }
        public Builder maxAlarmSubscriptions(int value) { this.maxAlarmSubscriptions = value; return this; }
        public Builder maxCatalogSubscriptions(int value) { this.maxCatalogSubscriptions = value; return this; }
        public Builder maxPendingCatalogNotifications(int value) { this.maxPendingCatalogNotifications = value; return this; }
        public Builder maxMobilePositionSubscriptions(int value) { this.maxMobilePositionSubscriptions = value; return this; }
        public Builder maxPendingMobilePositionNotifications(int value) { this.maxPendingMobilePositionNotifications = value; return this; }
        public Builder maxMobilePositionItems(int value) { this.maxMobilePositionItems = value; return this; }
        public GbServerOptions build() { return new GbServerOptions(serverId, realm, bindAddress, advertisedAddress, port, registrationTtl, heartbeatInterval, heartbeatMisses, queryTimeout, maxDevices, maxPendingQueries, maxCatalogItems, maxMessageBytes, inviteTimeout, stopTimeout, maxPlaySessions, maxRecordItems, maxPendingAlarms, maxAlarmSubscriptions, maxCatalogSubscriptions, maxPendingCatalogNotifications, maxMobilePositionSubscriptions, maxPendingMobilePositionNotifications, maxMobilePositionItems); }
    }
}
