package com.ss.kmz;

import com.ss.kmz.domain.KmzMission;
import com.ss.kmz.exception.KmzException;
import com.ss.kmz.internal.XmlSupport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 受限解压并解析 KMZ 中的 KML 或 DJI WPML 任务。
 */
public final class KmzParser {

    private static final String WAYLINES = "wpmz/waylines.wpml";
    private static final String TEMPLATE = "wpmz/template.kml";
    private static final String DOC = "doc.kml";

    private KmzParser() {
    }

    /** 从 KMZ 文件解析任务。 */
    public static KmzMission parse(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("KMZ path must not be null");
        }
        try (InputStream inputStream = Files.newInputStream(path)) {
            return parse(inputStream);
        } catch (IOException exception) {
            throw new KmzException("读取 KMZ 文件失败: " + path, exception);
        }
    }

    /** 从 KMZ 字节数组解析任务。 */
    public static KmzMission parse(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("KMZ bytes must not be null");
        }
        return parse(new ByteArrayInputStream(bytes));
    }

    /** 使用默认限制从输入流解析任务，输入流不会被关闭。 */
    public static KmzMission parse(InputStream inputStream) {
        return parse(inputStream, KmzReadLimits.defaults());
    }

    /** 使用指定限制从输入流解析任务，输入流不会被关闭。 */
    public static KmzMission parse(InputStream inputStream, KmzReadLimits limits) {
        if (limits == null) {
            throw new IllegalArgumentException("KMZ read limits must not be null");
        }
        byte[] compressed = XmlSupport.readLimited(
                inputStream, limits.maxCompressedBytes(), "KMZ");
        List<Candidate> candidates = readCandidates(compressed, limits);
        Candidate selected = select(candidates);
        return KmlReader.parse(new ByteArrayInputStream(selected.content()), limits.maxEntryBytes());
    }

    private static List<Candidate> readCandidates(byte[] compressed, KmzReadLimits limits) {
        List<Candidate> candidates = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(compressed))) {
            ZipEntry entry;
            int entryCount = 0;
            long totalUncompressedBytes = 0;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > limits.maxEntries()) {
                    throw new KmzException("KMZ ZIP entry count exceeds " + limits.maxEntries());
                }
                String name = validateEntryName(entry);
                if (!entry.isDirectory()) {
                    if (entry.getSize() > limits.maxEntryBytes()) {
                        throw new KmzException("ZIP entry " + name + " exceeds maximum size of "
                                + limits.maxEntryBytes() + " bytes");
                    }
                    byte[] content = XmlSupport.readLimited(
                            zip, limits.maxEntryBytes(), "ZIP entry " + name);
                    totalUncompressedBytes += content.length;
                    if (totalUncompressedBytes > limits.maxTotalUncompressedBytes()) {
                        throw new KmzException("KMZ total uncompressed size exceeds "
                                + limits.maxTotalUncompressedBytes() + " bytes");
                    }
                    String lowerName = name.toLowerCase(Locale.ROOT);
                    if (lowerName.endsWith(".kml") || lowerName.endsWith(".wpml")) {
                        candidates.add(new Candidate(lowerName, content));
                    }
                }
                zip.closeEntry();
            }
            return candidates;
        } catch (IOException exception) {
            throw new KmzException("解压 KMZ 失败", exception);
        }
    }

    private static String validateEntryName(ZipEntry entry) {
        String name = entry.getName();
        if (name == null || name.isBlank() || name.indexOf('\\') >= 0
                || name.startsWith("/") || isWindowsAbsolute(name)) {
            throw new KmzException("unsafe ZIP entry path: " + name);
        }
        String[] parts = name.split("/", -1);
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            boolean allowedDirectorySuffix = entry.isDirectory()
                    && index == parts.length - 1 && part.isEmpty();
            if ((!allowedDirectorySuffix && part.isEmpty()) || ".".equals(part) || "..".equals(part)) {
                throw new KmzException("unsafe ZIP entry path: " + name);
            }
        }
        return name;
    }

    private static boolean isWindowsAbsolute(String name) {
        return name.length() >= 2 && Character.isLetter(name.charAt(0)) && name.charAt(1) == ':';
    }

    private static Candidate select(List<Candidate> candidates) {
        Candidate selected = selectExact(candidates, WAYLINES);
        if (selected != null) return selected;
        selected = selectExact(candidates, TEMPLATE);
        if (selected != null) return selected;
        selected = selectExact(candidates, DOC);
        if (selected != null) return selected;
        if (candidates.isEmpty()) {
            throw new KmzException("KMZ does not contain a KML or WPML task file");
        }
        if (candidates.size() != 1) {
            throw new KmzException("ambiguous KML/WPML task files in KMZ");
        }
        return candidates.get(0);
    }

    private static Candidate selectExact(List<Candidate> candidates, String expectedName) {
        Candidate match = null;
        for (Candidate candidate : candidates) {
            if (!expectedName.equals(candidate.name())) {
                continue;
            }
            if (match != null) {
                throw new KmzException("ambiguous duplicate KMZ entry: " + expectedName);
            }
            match = candidate;
        }
        return match;
    }

    private record Candidate(String name, byte[] content) {
    }
}
