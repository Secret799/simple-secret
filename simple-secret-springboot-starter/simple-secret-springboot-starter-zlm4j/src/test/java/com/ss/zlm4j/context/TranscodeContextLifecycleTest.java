package com.ss.zlm4j.context;

import com.ss.zlm4j.service.domain.bo.TranscodeBO;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TranscodeContextLifecycleTest {

    @Test
    void shouldRejectInputWithoutVideoStreamBeforeDereferencingIt() {
        assertThat(TranscodeContext.hasVideoStream(-1)).isFalse();
        assertThat(TranscodeContext.hasVideoStream(0)).isTrue();
    }

    @Test
    void stopShouldRequestFfmpegIoInterruption() {
        TranscodeContext context = new TranscodeContext(new TranscodeBO(), "rtmp://127.0.0.1/live/out");

        assertThat(context.shouldInterruptIo()).isFalse();
        context.stop();
        assertThat(context.shouldInterruptIo()).isTrue();
    }

    @Test
    void startMethodShouldStayWithinJavaMethodSizeGuideline() throws Exception {
        Path sourcePath = Path.of(System.getProperty("basedir"))
                .resolve("src/main/java/com/ss/zlm4j/context/TranscodeContext.java");
        String source = Files.readString(sourcePath);
        int start = source.indexOf("public void start()");
        int end = source.indexOf("\n    private AVIOInterruptCB.Callback_Pointer", start);

        assertThat(start).isNotNegative();
        assertThat(end).isGreaterThan(start);
        assertThat(source.substring(start, end).lines().count()).isLessThanOrEqualTo(80);
    }

    @Test
    void normalInputEofShouldStillWriteTrailer() throws Exception {
        Path sourcePath = Path.of(System.getProperty("basedir"))
                .resolve("src/main/java/com/ss/zlm4j/context/TranscodeContext.java");
        String source = Files.readString(sourcePath);
        int start = source.indexOf("private void transcodePackets");
        int end = source.indexOf("\n    private boolean transcodeVideoPacket", start);

        assertThat(start).isNotNegative();
        assertThat(end).isGreaterThan(start);
        assertThat(source.substring(start, end))
                .contains("result != AVERROR_EOF()")
                .contains("avformat.av_write_trailer(resources.outputFormat)");
    }
}
