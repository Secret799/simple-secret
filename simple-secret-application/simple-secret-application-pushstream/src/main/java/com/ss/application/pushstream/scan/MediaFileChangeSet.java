package com.ss.application.pushstream.scan;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 两次媒体文件扫描之间的变化集合。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public final class MediaFileChangeSet {

    /** 新增文件。 */
    private final List<MediaFile> added;

    /** 内容或元数据发生变化的文件。 */
    private final List<MediaFile> updated;

    /** 已删除文件。 */
    private final List<MediaFile> removed;

    private MediaFileChangeSet(List<MediaFile> added, List<MediaFile> updated, List<MediaFile> removed) {
        this.added = List.copyOf(added);
        this.updated = List.copyOf(updated);
        this.removed = List.copyOf(removed);
    }

    /**
     * 比较前后两次扫描结果。
     *
     * @param previous 上一次扫描结果
     * @param current 当前扫描结果
     * @return 文件变化集合
     */
    public static MediaFileChangeSet between(List<MediaFile> previous, List<MediaFile> current) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        Map<Path, MediaFile> previousByPath = indexByPath(previous);
        Map<Path, MediaFile> currentByPath = indexByPath(current);
        List<MediaFile> added = new ArrayList<>();
        List<MediaFile> updated = new ArrayList<>();
        List<MediaFile> removed = new ArrayList<>();
        for (MediaFile currentFile : current) {
            MediaFile previousFile = previousByPath.get(currentFile.path());
            if (previousFile == null) {
                added.add(currentFile);
            } else if (!previousFile.sameVersion(currentFile)) {
                updated.add(currentFile);
            }
        }
        for (MediaFile previousFile : previous) {
            if (!currentByPath.containsKey(previousFile.path())) {
                removed.add(previousFile);
            }
        }
        return new MediaFileChangeSet(added, updated, removed);
    }

    private static Map<Path, MediaFile> indexByPath(List<MediaFile> files) {
        Map<Path, MediaFile> indexedFiles = new LinkedHashMap<>();
        for (MediaFile file : files) {
            MediaFile duplicate = indexedFiles.put(file.path(), file);
            if (duplicate != null) {
                throw new IllegalArgumentException("Duplicate media file path: " + file.path());
            }
        }
        return indexedFiles;
    }

    /** @return 新增文件列表 */
    public List<MediaFile> added() {
        return added;
    }

    /** @return 更新文件列表 */
    public List<MediaFile> updated() {
        return updated;
    }

    /** @return 删除文件列表 */
    public List<MediaFile> removed() {
        return removed;
    }
}
