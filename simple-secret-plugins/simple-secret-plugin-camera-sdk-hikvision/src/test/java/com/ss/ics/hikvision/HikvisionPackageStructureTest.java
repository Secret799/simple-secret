package com.ss.ics.hikvision;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证海康驱动内部实现不会重新堆积到公共根包。 */
class HikvisionPackageStructureTest {

    @Test
    void placesNativeImplementationInInternalPackages() throws ClassNotFoundException {
        assertThat(Class.forName("com.ss.ics.hikvision.internal.HikvisionNativeApi")).isNotNull();
        assertThat(Class.forName("com.ss.ics.hikvision.internal.jna.JnaHikvisionNativeApi")).isNotNull();
        assertThat(Class.forName("com.ss.ics.hikvision.HikvisionJnaStructures")).isNotNull();
        assertThat(Class.forName("com.ss.ics.hikvision.internal.model.HikvisionNativeLoginResult")).isNotNull();
        assertThat(Class.forName("com.ss.ics.hikvision.internal.query.HikvisionPlaybackCalendarQuery")).isNotNull();

        assertThatMissing("com.ss.ics.hikvision.HikvisionNativeApi");
        assertThatMissing("com.ss.ics.hikvision.HikvisionNativeLoginResult");
        assertThatMissing("com.ss.ics.hikvision.HikvisionPlaybackCalendarQuery");
        assertThatMissing("com.ss.ics.hikvision.JnaHikvisionNativeApi");
    }

    private static void assertThatMissing(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
