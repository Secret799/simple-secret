package com.ss.ics.dahua;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** 已校验但尚未加载的大华原生库路径。 */
record DahuaNativeLibraryPaths(Path netSdkLibrary) {

    static DahuaNativeLibraryPaths resolve(DahuaSdkOptions options, String osName) {
        if (options == null) {
            throw new IllegalArgumentException("options must not be null");
        }
        if (osName == null) {
            throw new IllegalArgumentException("osName must not be null");
        }
        Path root = options.libraryDirectory();
        String normalizedOs = osName.toLowerCase(Locale.ROOT);
        if (normalizedOs.startsWith("windows")) {
            return new DahuaNativeLibraryPaths(requiredFile(root, "dhnetsdk.dll"));
        }
        if (normalizedOs.contains("linux")) {
            return new DahuaNativeLibraryPaths(requiredFile(root, "libdhnetsdk.so"));
        }
        throw new IllegalArgumentException("Dahua SDK supports Windows and Linux only");
    }

    private static Path requiredFile(Path root, String fileName) {
        Path file = root.resolve(fileName).normalize();
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new IllegalArgumentException(
                    "Required Dahua native library is missing: " + fileName);
        }
        return file;
    }

}
