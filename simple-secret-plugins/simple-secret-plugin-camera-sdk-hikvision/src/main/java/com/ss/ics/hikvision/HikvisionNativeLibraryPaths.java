package com.ss.ics.hikvision;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** 已校验但尚未加载的海康原生库路径。 */
record HikvisionNativeLibraryPaths(
        Path networkLibrary,
        Path componentDirectory,
        Path cryptoLibrary,
        Path sslLibrary,
        boolean linux) {

    static HikvisionNativeLibraryPaths resolve(HikvisionSdkOptions options, String osName) {
        if (options == null) {
            throw new IllegalArgumentException("options must not be null");
        }
        if (osName == null) {
            throw new IllegalArgumentException("osName must not be null");
        }
        Path root = options.libraryDirectory();
        String normalizedOs = osName.toLowerCase(Locale.ROOT);
        if (normalizedOs.startsWith("windows")) {
            return new HikvisionNativeLibraryPaths(
                    requiredFile(root, "HCNetSDK.dll"), null, null, null, false);
        }
        if (normalizedOs.contains("linux")) {
            return new HikvisionNativeLibraryPaths(
                    requiredFile(root, "libhcnetsdk.so"),
                    root,
                    requiredFile(root, "libcrypto.so.1.1"),
                    requiredFile(root, "libssl.so.1.1"),
                    true);
        }
        throw new IllegalArgumentException("Hikvision SDK supports Windows and Linux only");
    }

    private static Path requiredFile(Path root, String fileName) {
        Path file = root.resolve(fileName).normalize();
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException(
                    "Required Hikvision native library is missing: " + fileName);
        }
        return file;
    }
}
