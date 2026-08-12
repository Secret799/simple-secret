package com.ss.ics.dahua;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证大华驱动内部实现不会重新堆积到公共根包。 */
class DahuaPackageStructureTest {

    @Test
    void placesNativeImplementationInInternalPackages() throws ClassNotFoundException {
        assertThat(Class.forName("com.ss.ics.dahua.internal.DahuaNativeApi")).isNotNull();
        assertThat(Class.forName("com.ss.ics.dahua.internal.jna.JnaDahuaNativeApi")).isNotNull();
        assertThat(Class.forName("com.ss.ics.dahua.internal.jna.DahuaNetSdkLibrary")).isNotNull();
        assertThat(Class.forName("com.ss.ics.dahua.DahuaJnaStructures")).isNotNull();
        assertThat(Class.forName("com.ss.ics.dahua.DahuaNativeLibrary")).isNotNull();
        assertThat(Class.forName("com.ss.ics.dahua.internal.model.DahuaNativeLoginResult")).isNotNull();
        assertThat(Class.forName("com.ss.ics.dahua.internal.jna.DahuaCallbackGate")).isNotNull();

        assertThatMissing("com.ss.ics.dahua.DahuaNativeApi");
        assertThatMissing("com.ss.ics.dahua.DahuaNativeLoginResult");
        assertThatMissing("com.ss.ics.dahua.DahuaNativeLibraryPaths");
        assertThatMissing("com.ss.ics.dahua.JnaDahuaNativeApi");
    }

    private static void assertThatMissing(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
