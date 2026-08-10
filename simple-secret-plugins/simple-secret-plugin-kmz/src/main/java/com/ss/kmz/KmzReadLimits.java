package com.ss.kmz;

/**
 * KMZ 读取资源限制。
 *
 * @param maxCompressedBytes KMZ 压缩输入最大字节数
 * @param maxEntryBytes 单个 ZIP 条目解压后的最大字节数
 * @param maxEntries ZIP 最大条目数
 * @param maxTotalUncompressedBytes ZIP 所有文件条目的累计解压字节上限
 */
public record KmzReadLimits(int maxCompressedBytes, int maxEntryBytes, int maxEntries,
                            int maxTotalUncompressedBytes) {

    /** 默认 KMZ 压缩输入上限：64 MiB。 */
    public static final int DEFAULT_MAX_COMPRESSED_BYTES = 64 * 1024 * 1024;
    /** 默认单条目解压上限：16 MiB。 */
    public static final int DEFAULT_MAX_ENTRY_BYTES = 16 * 1024 * 1024;
    /** 默认 ZIP 条目上限。 */
    public static final int DEFAULT_MAX_ENTRIES = 128;
    /** 默认累计解压上限：64 MiB。 */
    public static final int DEFAULT_MAX_TOTAL_UNCOMPRESSED_BYTES = 64 * 1024 * 1024;

    /**
     * 创建限制，并把累计解压上限设置为压缩输入上限。
     */
    public KmzReadLimits(int maxCompressedBytes, int maxEntryBytes, int maxEntries) {
        this(maxCompressedBytes, maxEntryBytes, maxEntries, maxCompressedBytes);
    }

    /** 校验限制均为正数。 */
    public KmzReadLimits {
        if (maxCompressedBytes <= 0 || maxEntryBytes <= 0 || maxEntries <= 0
                || maxTotalUncompressedBytes <= 0) {
            throw new IllegalArgumentException("KMZ read limits must be positive");
        }
    }

    /** 返回默认限制。 */
    public static KmzReadLimits defaults() {
        return new KmzReadLimits(
                DEFAULT_MAX_COMPRESSED_BYTES,
                DEFAULT_MAX_ENTRY_BYTES,
                DEFAULT_MAX_ENTRIES,
                DEFAULT_MAX_TOTAL_UNCOMPRESSED_BYTES);
    }
}
