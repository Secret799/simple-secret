package com.ss.zlm4j.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoStackWindowContextTest {

    @Test
    void identifiesNativeCallFailureFromCurrentReturnCode() {
        assertTrue(VideoStackWindowContext.nativeCallFailed(-1));
        assertFalse(VideoStackWindowContext.nativeCallFailed(0));
        assertFalse(VideoStackWindowContext.nativeCallFailed(1));
    }
}
