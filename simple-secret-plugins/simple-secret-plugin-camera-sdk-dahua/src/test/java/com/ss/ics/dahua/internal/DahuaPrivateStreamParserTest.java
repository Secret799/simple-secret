package com.ss.ics.dahua.internal;

import com.ss.ics.dahua.internal.model.DahuaNativeStreamFrame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DahuaPrivateStreamParserTest {

    private final List<DahuaNativeStreamFrame> frames = new ArrayList<>();

    @Test
    void stripsDhavHeaderAndDeliversAnnexBPayload() {
        byte[] payload = {0, 0, 0, 1, 0x67, 0x4d, 0x00, 0x29, 1, 2, 3};

        DahuaPrivateStreamParser.parse(dhavFrame(0xfd, 555_177L, 48, payload), frames::add);

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).data()).isEqualTo(payload);
        assertThat(frames.get(0).pts()).isEqualTo(555_177L);
        assertThat(frames.get(0).dts()).isEqualTo(555_177L);
        assertThat(frames.get(0).frameType()).isEqualTo(0xfd);
    }

    @Test
    void parsesMultipleFramesInSingleBuffer() {
        byte[] iPayload = {0, 0, 0, 1, 0x67, 1};
        byte[] pPayload = {0, 0, 0, 1, 0x21, 2};
        byte[] buffer = concat(
                dhavFrame(0xfd, 1000L, 48, iPayload),
                dhavFrame(0xfc, 1040L, 48, pPayload));

        DahuaPrivateStreamParser.parse(buffer, frames::add);

        assertThat(frames).hasSize(2);
        assertThat(frames.get(0).frameType()).isEqualTo(0xfd);
        assertThat(frames.get(1).pts()).isEqualTo(1040L);
        assertThat(frames.get(1).data()).isEqualTo(pPayload);
    }

    @Test
    void skipsInfoAndAudioFramesWithoutStartCode() {
        byte[] infoPayload = {0x34, (byte) 0xe0, 1, 2};
        byte[] audioPayload = {0x11, 0x22};
        byte[] pPayload = {0, 0, 0, 1, 0x21, 9};

        DahuaPrivateStreamParser.parse(concat(
                dhavFrame(0xf1, 0L, 48, infoPayload),
                dhavFrame(0xf0, 0L, 48, audioPayload),
                dhavFrame(0xfc, 40L, 48, pPayload)), frames::add);

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).data()).isEqualTo(pPayload);
    }

    @Test
    void findsPayloadByScanWhenHeaderIsShorter() {
        byte[] payload = {0, 0, 0, 1, 0x21, 5};

        DahuaPrivateStreamParser.parse(dhavFrame(0xfc, 7L, 20, payload), frames::add);

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).data()).isEqualTo(payload);
    }

    @Test
    void rejectsBufferWithoutDhavMagic() {
        byte[] payload = {0, 0, 0, 1, 0x21, 5};
        byte[] buffer = dhavFrame(0xfc, 7L, 48, payload);
        buffer[0] = 'X';

        DahuaPrivateStreamParser.parse(buffer, frames::add);

        assertThat(frames).isEmpty();
    }

    @Test
    void rejectsInconsistentFrameLength() {
        byte[] payload = {0, 0, 0, 1, 0x21, 5};
        byte[] buffer = dhavFrame(0xfc, 7L, 48, payload);
        writeLittleEndian(buffer, 12, Integer.MAX_VALUE);

        DahuaPrivateStreamParser.parse(buffer, frames::add);

        assertThat(frames).isEmpty();
    }

    private static byte[] dhavFrame(int frameType, long pts, int headerSize, byte[] payload) {
        int total = headerSize + payload.length;
        byte[] frame = new byte[total];
        frame[0] = 'D';
        frame[1] = 'H';
        frame[2] = 'A';
        frame[3] = 'V';
        frame[4] = (byte) frameType;
        writeLittleEndian(frame, 8, pts);
        writeLittleEndian(frame, 12, total);
        System.arraycopy(payload, 0, frame, headerSize, payload.length);
        return frame;
    }

    private static void writeLittleEndian(byte[] target, int offset, long value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
        target[offset + 2] = (byte) (value >>> 16);
        target[offset + 3] = (byte) (value >>> 24);
    }

    private static byte[] concat(byte[]... arrays) {
        int length = 0;
        for (byte[] array : arrays) {
            length += array.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }
}
