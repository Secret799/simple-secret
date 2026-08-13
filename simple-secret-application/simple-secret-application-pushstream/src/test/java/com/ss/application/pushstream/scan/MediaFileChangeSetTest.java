package com.ss.application.pushstream.scan;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 媒体文件变化集测试。
 *
 * @author junpzx
 * @since 2026-08-12
 */
class MediaFileChangeSetTest {

    @Test
    void shouldDetectAddedUpdatedAndRemovedFiles() {
        MediaFile removed = mediaFile("removed.mp4", 10L, 1L);
        MediaFile unchanged = mediaFile("same.mp4", 10L, 1L);
        MediaFile oldUpdated = mediaFile("updated.mp4", 10L, 1L);
        MediaFile added = mediaFile("added.mp4", 10L, 1L);
        MediaFile newUpdated = mediaFile("updated.mp4", 11L, 2L);

        MediaFileChangeSet changes = MediaFileChangeSet.between(
                List.of(removed, unchanged, oldUpdated),
                List.of(added, unchanged, newUpdated));

        assertThat(changes.added()).containsExactly(added);
        assertThat(changes.updated()).containsExactly(newUpdated);
        assertThat(changes.removed()).containsExactly(removed);
    }

    @Test
    void shouldDetectReplacedFileWithUnchangedSizeAndTimestamp() {
        Path path = Path.of("/media/replaced.mp4");
        Instant modifiedTime = Instant.ofEpochSecond(1L);
        MediaFile previous = new MediaFile(
                path, "replaced.mp4", "stream", 10L, modifiedTime, path, "file-key-1");
        MediaFile current = new MediaFile(
                path, "replaced.mp4", "stream", 10L, modifiedTime, path, "file-key-2");

        MediaFileChangeSet changes = MediaFileChangeSet.between(
                List.of(previous), List.of(current));

        assertThat(changes.updated()).containsExactly(current);
    }

    private MediaFile mediaFile(String name, long size, long modifiedSecond) {
        return new MediaFile(Path.of("/media", name), name,
                "stream", size, Instant.ofEpochSecond(modifiedSecond));
    }
}
