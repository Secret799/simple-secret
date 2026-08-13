package com.ss.application.pushstream.scan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 媒体文件扫描器测试。
 *
 * @author junpzx
 * @since 2026-08-12
 */
class MediaFileScannerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldFilterSuffixAndReturnStableOrder() throws IOException {
        Files.writeString(temporaryDirectory.resolve("B.MP4"), "b");
        Files.writeString(temporaryDirectory.resolve("a.mp4"), "a");
        Files.writeString(temporaryDirectory.resolve("ignored.txt"), "x");

        MediaFileScanner scanner = new MediaFileScanner(
                temporaryDirectory, Set.of("mp4"), false);

        List<MediaFile> files = scanner.scan();

        assertThat(files).extracting(MediaFile::fileName)
                .containsExactly("a.mp4", "B.MP4");
    }

    @Test
    void shouldHonorRecursiveFlagAndSkipSymbolicLinks() throws IOException {
        Path nestedDirectory = Files.createDirectory(temporaryDirectory.resolve("nested"));
        Path nestedFile = Files.writeString(nestedDirectory.resolve("nested.mp4"), "nested");
        Path linkedFile = temporaryDirectory.resolve("linked.mp4");
        try {
            Files.createSymbolicLink(linkedFile, nestedFile);
        } catch (UnsupportedOperationException exception) {
            return;
        }

        List<MediaFile> flatFiles = new MediaFileScanner(
                temporaryDirectory, Set.of("mp4"), false).scan();
        List<MediaFile> recursiveFiles = new MediaFileScanner(
                temporaryDirectory, Set.of("mp4"), true).scan();

        assertThat(flatFiles).isEmpty();
        assertThat(recursiveFiles).extracting(MediaFile::fileName)
                .containsExactly("nested.mp4");
    }

    @Test
    void shouldRejectScanResultsAboveConfiguredLimit() throws IOException {
        Files.writeString(temporaryDirectory.resolve("first.mp4"), "first");
        Files.writeString(temporaryDirectory.resolve("second.mp4"), "second");
        MediaFileScanner scanner = new MediaFileScanner(
                temporaryDirectory, Set.of("mp4"), false, 1);

        org.assertj.core.api.Assertions.assertThatThrownBy(scanner::scan)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maximum scanned");
    }

    @Test
    void shouldFailWhenConfiguredRootIsReplacedBySymbolicLink() throws IOException {
        Path originalDirectory = Files.createDirectory(temporaryDirectory.resolve("media"));
        Path replacementDirectory = Files.createDirectory(temporaryDirectory.resolve("replacement"));
        MediaFileScanner scanner = new MediaFileScanner(originalDirectory, Set.of("mp4"), false);
        Files.delete(originalDirectory);
        try {
            Files.createSymbolicLink(originalDirectory, replacementDirectory);
        } catch (UnsupportedOperationException exception) {
            return;
        }

        org.assertj.core.api.Assertions.assertThatThrownBy(scanner::scan)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("no longer a regular directory");
    }

    @Test
    void shouldFailWhenConfiguredRootAncestorChangesTarget() throws IOException {
        Path configuredParent = Files.createDirectory(temporaryDirectory.resolve("configured"));
        Path configuredRoot = Files.createDirectory(configuredParent.resolve("media"));
        Path replacementParent = Files.createDirectory(temporaryDirectory.resolve("replacement"));
        Files.createDirectory(replacementParent.resolve("media"));
        MediaFileScanner scanner = new MediaFileScanner(configuredRoot, Set.of("mp4"), false);
        Files.move(configuredParent, temporaryDirectory.resolve("original"));
        try {
            Files.createSymbolicLink(configuredParent, replacementParent);
        } catch (UnsupportedOperationException exception) {
            return;
        }

        assertThatThrownBy(scanner::scan)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("target changed");
    }

    @Test
    void shouldRejectFileReplacedAfterScan() throws IOException {
        Path mediaPath = Files.writeString(temporaryDirectory.resolve("demo.mp4"), "original");
        Path outsideFile = Files.writeString(temporaryDirectory.resolve("outside.bin"), "outside");
        MediaFileScanner scanner = new MediaFileScanner(
                temporaryDirectory, Set.of("mp4"), false);
        MediaFile mediaFile = scanner.scan().get(0);
        Files.delete(mediaPath);
        try {
            Files.createSymbolicLink(mediaPath, outsideFile);
        } catch (UnsupportedOperationException exception) {
            return;
        }

        assertThatThrownBy(() -> scanner.verifyReadable(mediaFile))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("changed after scan");
    }

    @Test
    void shouldVerifyPublicMediaFileSnapshotWithoutFileSystemIdentity() throws IOException {
        Path mediaPath = Files.writeString(temporaryDirectory.resolve("demo.mp4"), "content");
        BasicFileAttributes attributes = Files.readAttributes(
                mediaPath, BasicFileAttributes.class);
        MediaFile mediaFile = new MediaFile(
                mediaPath, "demo.mp4", "demo", attributes.size(),
                attributes.lastModifiedTime().toInstant());
        MediaFileScanner scanner = new MediaFileScanner(
                temporaryDirectory, Set.of("mp4"), false);

        scanner.verifyReadable(mediaFile);
    }

    @Test
    void shouldRejectPublicMediaFileSnapshotOutsideConfiguredRoot() throws IOException {
        Path scanRoot = Files.createDirectory(temporaryDirectory.resolve("media"));
        Path outsidePath = Files.writeString(temporaryDirectory.resolve("outside.mp4"), "content");
        BasicFileAttributes attributes = Files.readAttributes(
                outsidePath, BasicFileAttributes.class);
        MediaFile mediaFile = new MediaFile(
                outsidePath, "outside.mp4", "outside", attributes.size(),
                attributes.lastModifiedTime().toInstant());
        MediaFileScanner scanner = new MediaFileScanner(scanRoot, Set.of("mp4"), false);

        assertThatThrownBy(() -> scanner.verifyReadable(mediaFile))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("changed after scan");
    }
}
