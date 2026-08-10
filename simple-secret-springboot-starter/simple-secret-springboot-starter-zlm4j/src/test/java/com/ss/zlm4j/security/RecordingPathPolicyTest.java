package com.ss.zlm4j.security;

import com.ss.zlm4j.config.properties.MediaResourcePolicyProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordingPathPolicyTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesRelativeRecordingPathInsideConfiguredRoot() {
        MediaResourcePolicyProperties properties = new MediaResourcePolicyProperties();
        properties.setRecordingRoot(tempDir.toString());
        DefaultMediaResourcePolicy policy = new DefaultMediaResourcePolicy(properties);

        assertThat(policy.requireRecordingPath("daily/camera-1"))
                .isEqualTo(tempDir.resolve("daily/camera-1").toAbsolutePath().normalize());
    }

    @Test
    void rejectsTraversalAndAbsolutePathOutsideConfiguredRoot() {
        MediaResourcePolicyProperties properties = new MediaResourcePolicyProperties();
        properties.setRecordingRoot(tempDir.toString());
        DefaultMediaResourcePolicy policy = new DefaultMediaResourcePolicy(properties);

        assertThatThrownBy(() -> policy.requireRecordingPath("../outside"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requireRecordingPath(tempDir.resolveSibling("outside").toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExistingSymlinkThatEscapesConfiguredRoot() throws Exception {
        Path outside = Files.createDirectory(tempDir.resolveSibling(tempDir.getFileName() + "-outside"));
        Path link = tempDir.resolve("escape");
        Files.createSymbolicLink(link, outside);
        MediaResourcePolicyProperties properties = new MediaResourcePolicyProperties();
        properties.setRecordingRoot(tempDir.toString());
        DefaultMediaResourcePolicy policy = new DefaultMediaResourcePolicy(properties);

        assertThatThrownBy(() -> policy.requireRecordingPath("escape/file.mp4"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
