package com.ss.ics.hikvision.internal.jna;

import com.ss.ics.hikvision.HikvisionSdkOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class HikvisionNativeLibraryPathsTest {

    @TempDir
    Path tempDirectory;

    @Test
    void resolvesWindowsNetworkLibraryWithoutLoadingIt() throws IOException {
        Path networkLibrary = Files.createFile(tempDirectory.resolve("HCNetSDK.dll"));

        HikvisionNativeLibraryPaths paths = HikvisionNativeLibraryPaths.resolve(
                HikvisionSdkOptions.defaults(tempDirectory), "Windows 11");

        assertThat(paths.networkLibrary()).isEqualTo(networkLibrary);
        assertThat(paths.linux()).isFalse();
    }

    @Test
    void resolvesRequiredLinuxLibrariesWithoutLoadingThem() throws IOException {
        Path networkLibrary = Files.createFile(tempDirectory.resolve("libhcnetsdk.so"));
        Path cryptoLibrary = Files.createFile(tempDirectory.resolve("libcrypto.so.1.1"));
        Path sslLibrary = Files.createFile(tempDirectory.resolve("libssl.so.1.1"));

        HikvisionNativeLibraryPaths paths = HikvisionNativeLibraryPaths.resolve(
                HikvisionSdkOptions.defaults(tempDirectory), "Linux");

        assertThat(paths.networkLibrary()).isEqualTo(networkLibrary);
        assertThat(paths.componentDirectory()).isEqualTo(tempDirectory);
        assertThat(paths.cryptoLibrary()).isEqualTo(cryptoLibrary);
        assertThat(paths.sslLibrary()).isEqualTo(sslLibrary);
        assertThat(paths.linux()).isTrue();
    }

    @Test
    void rejectsUnsupportedPlatformsAndMissingLibrariesWithoutFullPaths() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> HikvisionNativeLibraryPaths.resolve(
                        HikvisionSdkOptions.defaults(tempDirectory), "Mac OS X"))
                .withMessage("Hikvision SDK supports Windows and Linux only");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> HikvisionNativeLibraryPaths.resolve(
                        HikvisionSdkOptions.defaults(tempDirectory.resolve("secret-root")), "Windows"))
                .withMessage("Required Hikvision native library is missing: HCNetSDK.dll");
    }
}
