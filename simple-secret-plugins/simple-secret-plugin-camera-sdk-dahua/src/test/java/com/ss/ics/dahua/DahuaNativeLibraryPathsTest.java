package com.ss.ics.dahua;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DahuaNativeLibraryPathsTest {

    @TempDir
    Path tempDirectory;

    @Test
    void resolvesWindowsNetSdkWithoutLoadingIt() throws IOException {
        Path netSdk = Files.createFile(tempDirectory.resolve("dhnetsdk.dll"));

        DahuaNativeLibraryPaths paths = DahuaNativeLibraryPaths.resolve(
                DahuaSdkOptions.defaults(tempDirectory), "Windows 11");

        assertThat(paths.netSdkLibrary()).isEqualTo(netSdk);
    }

    @Test
    void resolvesLinuxNetSdk() throws IOException {
        Path netSdk = Files.createFile(tempDirectory.resolve("libdhnetsdk.so"));

        DahuaNativeLibraryPaths paths = DahuaNativeLibraryPaths.resolve(
                DahuaSdkOptions.defaults(tempDirectory), "Linux");

        assertThat(paths.netSdkLibrary()).isEqualTo(netSdk);
    }

    @Test
    void rejectsUnsupportedPlatformsAndMissingNetSdkWithoutFullPaths() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DahuaNativeLibraryPaths.resolve(
                        DahuaSdkOptions.defaults(tempDirectory), "Mac OS X"))
                .withMessage("Dahua SDK supports Windows and Linux only");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DahuaNativeLibraryPaths.resolve(
                        DahuaSdkOptions.defaults(tempDirectory.resolve("secret-root")), "Windows"))
                .withMessage("Required Dahua native library is missing: dhnetsdk.dll");
    }
}
