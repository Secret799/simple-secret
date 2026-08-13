package com.ss.application.pushstream.scan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 在受控根目录内扫描允许发布的媒体文件。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public class MediaFileScanner {

    /** 扫描根目录。 */
    private final Path rootDirectory;

    /** 构造时解析的真实根目录，用于检测祖先路径替换。 */
    private final Path realRootDirectory;

    /** 小写形式的允许后缀集合。 */
    private final Set<String> allowedSuffixes;

    /** 是否递归扫描子目录。 */
    private final boolean recursive;

    /** 单次扫描允许返回的最大文件数。 */
    private final int maxScannedFiles;

    /**
     * 创建媒体文件扫描器。
     *
     * @param rootDirectory 扫描根目录
     * @param allowedSuffixes 允许的文件后缀
     * @param recursive 是否递归扫描
     */
    public MediaFileScanner(Path rootDirectory, Set<String> allowedSuffixes, boolean recursive) {
        this(rootDirectory, allowedSuffixes, recursive, 1000);
    }

    /**
     * 创建带文件数上限的媒体文件扫描器。
     *
     * @param rootDirectory 扫描根目录
     * @param allowedSuffixes 允许的文件后缀
     * @param recursive 是否递归扫描
     * @param maxScannedFiles 单次扫描最大文件数
     */
    public MediaFileScanner(Path rootDirectory, Set<String> allowedSuffixes,
                            boolean recursive, int maxScannedFiles) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory")
                .toAbsolutePath().normalize();
        this.realRootDirectory = resolveRealRoot(this.rootDirectory);
        this.allowedSuffixes = Objects.requireNonNull(allowedSuffixes, "allowedSuffixes").stream()
                .map(suffix -> suffix.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.recursive = recursive;
        if (maxScannedFiles < 1) {
            throw new IllegalArgumentException("maxScannedFiles must be positive");
        }
        this.maxScannedFiles = maxScannedFiles;
    }

    /**
     * 扫描当前目录并生成稳定排序的文件快照。
     *
     * @return 媒体文件快照列表
     * @throws IOException 读取目录或文件元数据失败时抛出
     */
    public List<MediaFile> scan() throws IOException {
        validateRootDirectory();
        int maxDepth = recursive ? Integer.MAX_VALUE : 1;
        try (Stream<Path> paths = Files.walk(rootDirectory, maxDepth)) {
            return paths.filter(this::isAllowedRegularFile)
                    .map(this::readMediaFile)
                    .limit((long) maxScannedFiles + 1L)
                    .sorted(Comparator.comparing(MediaFile::fileName, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(MediaFile::fileName))
                    .collect(Collectors.collectingAndThen(Collectors.toList(), this::validateFileCount));
        }
    }

    /**
     * 在交给外部进程前复验文件身份，拒绝扫描后的替换和符号链接。
     *
     * @param mediaFile 扫描生成的文件快照
     * @throws IOException 文件身份发生变化或无法读取时抛出
     */
    public void verifyReadable(MediaFile mediaFile) throws IOException {
        Objects.requireNonNull(mediaFile, "mediaFile");
        validateRootDirectory();
        Path path = mediaFile.path();
        if (!path.startsWith(rootDirectory)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)) {
            throw new IOException("Media file changed after scan");
        }
        Path currentRealPath = path.toRealPath();
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        String currentFileKey = Objects.toString(attributes.fileKey(), null);
        if (!currentRealPath.startsWith(realRootDirectory)
                || realPathChanged(mediaFile, currentRealPath)
                || fileIdentityChanged(mediaFile.fileKey(), currentFileKey)
                || attributes.size() != mediaFile.size()
                || !attributes.lastModifiedTime().toInstant().equals(mediaFile.lastModifiedTime())) {
            throw new IOException("Media file changed after scan");
        }
    }

    private boolean realPathChanged(MediaFile mediaFile, Path currentRealPath) {
        return mediaFile.fileKey() != null && !currentRealPath.equals(mediaFile.realPath());
    }

    private boolean fileIdentityChanged(String expectedFileKey, String currentFileKey) {
        return expectedFileKey != null && !Objects.equals(expectedFileKey, currentFileKey);
    }

    private void validateRootDirectory() throws IOException {
        if (!Files.isDirectory(rootDirectory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(rootDirectory)) {
            throw new IOException("Configured media scan root is no longer a regular directory");
        }
        if (!rootDirectory.toRealPath().equals(realRootDirectory)) {
            throw new IOException("Configured media scan root target changed");
        }
    }

    private List<MediaFile> validateFileCount(List<MediaFile> files) {
        if (files.size() > maxScannedFiles) {
            throw new IllegalStateException("The maximum scanned media file count has been exceeded");
        }
        return List.copyOf(files);
    }

    private boolean isAllowedRegularFile(Path path) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            return false;
        }
        String fileName = path.getFileName().toString();
        int separatorIndex = fileName.lastIndexOf('.');
        if (separatorIndex < 0 || separatorIndex == fileName.length() - 1) {
            return false;
        }
        return allowedSuffixes.contains(fileName.substring(separatorIndex + 1).toLowerCase(Locale.ROOT));
    }

    private MediaFile readMediaFile(Path path) {
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(rootDirectory)) {
            throw new IllegalStateException("Scanned file escaped configured root directory");
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    normalizedPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            Path realPath = normalizedPath.toRealPath();
            if (!realPath.startsWith(realRootDirectory)) {
                throw new MediaFileScanException("Media file escaped configured root directory", null);
            }
            String fileName = normalizedPath.getFileName().toString();
            return new MediaFile(normalizedPath, fileName, createStreamId(normalizedPath),
                    attributes.size(), attributes.lastModifiedTime().toInstant(), realPath,
                    Objects.toString(attributes.fileKey(), null));
        } catch (IOException exception) {
            throw new MediaFileScanException("Unable to read media file metadata", exception);
        }
    }

    private static Path resolveRealRoot(Path rootDirectory) {
        try {
            return rootDirectory.toRealPath();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Media scan root must be readable", exception);
        }
    }

    private String createStreamId(Path path) {
        String relativePath = rootDirectory.relativize(path).toString().replace('\\', '/');
        String fileName = path.getFileName().toString();
        int separatorIndex = fileName.lastIndexOf('.');
        String baseName = separatorIndex > 0 ? fileName.substring(0, separatorIndex) : fileName;
        String safeBaseName = baseName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (safeBaseName.isBlank()) {
            safeBaseName = "stream";
        }
        if (safeBaseName.length() > 48) {
            safeBaseName = safeBaseName.substring(0, 48);
        }
        return safeBaseName + '-' + shortHash(relativePath);
    }

    private String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
