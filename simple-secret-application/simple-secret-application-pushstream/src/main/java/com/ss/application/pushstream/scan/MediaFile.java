package com.ss.application.pushstream.scan;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * 扫描到的媒体文件不可变快照。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public final class MediaFile {

    /** 媒体文件的规范化绝对路径。 */
    private final Path path;

    /** 用于状态展示的文件名。 */
    private final String fileName;

    /** 用于发布到 ZLMediaKit 的稳定流标识。 */
    private final String streamId;

    /** 文件大小，单位为字节。 */
    private final long size;

    /** 文件最后修改时间。 */
    private final Instant lastModifiedTime;

    /** 扫描时解析得到的真实路径。 */
    private final Path realPath;

    /** 文件系统提供的稳定文件标识，文件系统不支持时为 null。 */
    private final String fileKey;

    /**
     * 创建不包含文件系统身份的媒体文件快照。
     *
     * <p>扫描器复验此类快照时仍会校验根目录、文件类型、大小和修改时间，
     * 但只有扫描器自产快照能够额外校验真实路径与文件系统身份。</p>
     *
     * @param path 文件绝对路径
     * @param fileName 文件名
     * @param streamId 稳定流标识
     * @param size 文件大小
     * @param lastModifiedTime 最后修改时间
     */
    public MediaFile(Path path, String fileName, String streamId, long size, Instant lastModifiedTime) {
        this(path, fileName, streamId, size, lastModifiedTime,
                Objects.requireNonNull(path, "path").toAbsolutePath().normalize(), null);
    }

    /**
     * 创建包含文件系统身份的内部媒体快照。
     *
     * @param path 文件绝对路径
     * @param fileName 文件名
     * @param streamId 稳定流标识
     * @param size 文件大小
     * @param lastModifiedTime 最后修改时间
     * @param realPath 扫描时真实路径
     * @param fileKey 文件系统身份
     */
    MediaFile(Path path, String fileName, String streamId, long size,
              Instant lastModifiedTime, Path realPath, String fileKey) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.streamId = Objects.requireNonNull(streamId, "streamId");
        this.size = size;
        this.lastModifiedTime = Objects.requireNonNull(lastModifiedTime, "lastModifiedTime");
        this.realPath = Objects.requireNonNull(realPath, "realPath").toAbsolutePath().normalize();
        this.fileKey = fileKey;
    }

    /** @return 文件绝对路径 */
    public Path path() {
        return path;
    }

    /** @return 文件名 */
    public String fileName() {
        return fileName;
    }

    /** @return 稳定流标识 */
    public String streamId() {
        return streamId;
    }

    /** @return 文件大小 */
    public long size() {
        return size;
    }

    /** @return 最后修改时间 */
    public Instant lastModifiedTime() {
        return lastModifiedTime;
    }

    /** @return 扫描时真实路径 */
    public Path realPath() {
        return realPath;
    }

    /** @return 文件系统身份，文件系统不支持时为 null */
    public String fileKey() {
        return fileKey;
    }

    /**
     * 判断两个快照是否表示相同文件版本。
     *
     * @param other 另一个文件快照
     * @return 路径、真实路径、文件身份、大小和修改时间均相同时返回 true
     */
    public boolean sameVersion(MediaFile other) {
        return other != null && path.equals(other.path)
                && realPath.equals(other.realPath)
                && Objects.equals(fileKey, other.fileKey)
                && size == other.size
                && lastModifiedTime.equals(other.lastModifiedTime);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof MediaFile mediaFile)) {
            return false;
        }
        return size == mediaFile.size && path.equals(mediaFile.path)
                && fileName.equals(mediaFile.fileName) && streamId.equals(mediaFile.streamId)
                && lastModifiedTime.equals(mediaFile.lastModifiedTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, fileName, streamId, size, lastModifiedTime);
    }

    @Override
    public String toString() {
        return "MediaFile{fileName='" + fileName + "', streamId='" + streamId
                + "', size=" + size + ", lastModifiedTime=" + lastModifiedTime + '}';
    }
}
