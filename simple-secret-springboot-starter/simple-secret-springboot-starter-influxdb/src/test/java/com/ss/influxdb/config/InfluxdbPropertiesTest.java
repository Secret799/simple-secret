package com.ss.influxdb.config;

import org.influxdb.InfluxDB;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InfluxdbPropertiesTest {

    @Test
    void defaultsShouldNeverConnectCreateResourcesOrContainCredentials() {
        InfluxdbProperties properties = new InfluxdbProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getUrl()).isNull();
        assertThat(properties.getUsername()).isNull();
        assertThat(properties.getPassword()).isNull();
        assertThat(properties.getDatabase().getName()).isNull();
        assertThat(properties.getDatabase().isAutoCreate()).isFalse();
        assertThat(properties.getRetentionPolicy().getName()).isNull();
        assertThat(properties.getRetentionPolicy().isAutoCreate()).isFalse();
        assertThat(properties.getBatchWrite().isEnabled()).isFalse();
        assertThat(properties.getLogLevel()).isEqualTo(InfluxDB.LogLevel.NONE);
    }

    @Test
    void enabledConfigurationShouldRequireSafeUrlAndDatabase() {
        InfluxdbProperties properties = enabledProperties();
        properties.setUrl(null);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL");

        properties.setUrl("ftp://localhost:8086");
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP");

        properties.setUrl("http://user:secret@localhost:8086");
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userinfo")
                .hasMessageNotContaining("secret");

        properties.setUrl("http://localhost:8086");
        properties.getDatabase().setName(null);
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("database");
    }

    @Test
    void passwordAndBatchSettingsShouldFailClearlyWhenIncomplete() {
        InfluxdbProperties properties = enabledProperties();
        properties.setPassword("secret");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username")
                .hasMessageNotContaining("secret");

        properties.setUsername("writer");
        properties.getBatchWrite().setEnabled(true);
        properties.getBatchWrite().setActions(0);
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actions");
    }

    @Test
    void retentionPolicyAutoCreateShouldRequireNameAndDuration() {
        InfluxdbProperties properties = enabledProperties();
        properties.getRetentionPolicy().setAutoCreate(true);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retention policy name");

        properties.getRetentionPolicy().setName("archive");
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duration");
    }

    private static InfluxdbProperties enabledProperties() {
        InfluxdbProperties properties = new InfluxdbProperties();
        properties.setEnabled(true);
        properties.setUrl("http://localhost:8086");
        properties.getDatabase().setName("telemetry");
        return properties;
    }
}
