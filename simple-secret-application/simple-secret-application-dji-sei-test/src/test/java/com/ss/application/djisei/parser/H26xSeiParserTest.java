package com.ss.application.djisei.parser;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H.264/H.265 SEI 解析器测试。
 *
 * @author junpzx
 * @since 2026-08-13
 */
class H26xSeiParserTest {

    /** SEI 解析器。 */
    private final H26xSeiParser parser = new H26xSeiParser();

    @Test
    void shouldParseH264UserDataWithFourByteStartCode() {
        byte[] uuid = bytes(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
        byte[] payload = concat(uuid, "dji-test".getBytes(StandardCharsets.UTF_8));
        byte[] frame = annexB4(h264Sei(5, payload));

        SeiParseResult result = parser.parse(frame, VideoCodec.H264, 1024, 512);

        assertThat(result.seiNalUnitCount()).isEqualTo(1);
        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().get(0).payloadType()).isEqualTo(5);
        assertThat(result.messages().get(0).uuid())
                .contains(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e0f"));
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void shouldParseH265PrefixAndSuffixSeiWithThreeByteStartCodes() {
        byte[] frame = concat(
                annexB3(h265Sei(39, 4, bytes(1, 2, 3))),
                annexB3(h265Sei(40, 5, new byte[16])));

        SeiParseResult result = parser.parse(frame, VideoCodec.H265, 1024, 512);

        assertThat(result.seiNalUnitCount()).isEqualTo(2);
        assertThat(result.messages()).extracting(SeiMessage::payloadType)
                .containsExactly(4, 5);
    }

    @Test
    void shouldRemoveEmulationPreventionBytesBeforeReadingPayload() {
        byte[] escapedRbsp = bytes(5, 4, 0, 0, 3, 1, 2, 0x80);
        byte[] frame = annexB4(concat(bytes(0x06), escapedRbsp));

        SeiParseResult result = parser.parse(frame, VideoCodec.H264, 1024, 512);

        assertThat(result.messages().get(0).payload()).containsExactly(0, 0, 1, 2);
    }

    @Test
    void shouldReportTruncatedAndOversizedPayloadWithoutThrowing() {
        SeiParseResult truncated = parser.parse(
                annexB4(bytes(0x06, 5, 10, 1, 2, 0x80)), VideoCodec.H264, 1024, 512);
        SeiParseResult oversized = parser.parse(
                annexB4(bytes(0x06, 5, 4, 1, 2, 3, 4, 0x80)), VideoCodec.H264, 1024, 3);

        assertThat(truncated.issues()).extracting(SeiParseIssue::code)
                .containsExactly("TRUNCATED_PAYLOAD");
        assertThat(oversized.issues()).extracting(SeiParseIssue::code)
                .containsExactly("PAYLOAD_TOO_LARGE");
    }

    @Test
    void shouldRejectOversizedFrameAndIgnoreNonSeiNalus() {
        SeiParseResult oversized = parser.parse(new byte[9], VideoCodec.H264, 8, 4);
        SeiParseResult regularFrame = parser.parse(
                concat(annexB4(bytes(0x65, 1, 2)), annexB3(bytes(0x41, 3, 4))),
                VideoCodec.H264, 1024, 512);

        assertThat(oversized.issues()).extracting(SeiParseIssue::code)
                .containsExactly("FRAME_TOO_LARGE");
        assertThat(regularFrame.messages()).isEmpty();
        assertThat(regularFrame.seiNalUnitCount()).isZero();
    }

    @Test
    void shouldReportMissingTrailingBitsWhenPayloadEndsAtEndOfFrame() {
        SeiParseResult result = parser.parse(
                annexB4(bytes(0x06, 5, 1, 1)), VideoCodec.H264, 1024, 512);

        assertThat(result.messages()).hasSize(1);
        assertThat(result.issues()).extracting(SeiParseIssue::code)
                .containsExactly("MISSING_TRAILING_BITS");
    }

    @Test
    void shouldReportMissingTrailingBitsWhenPayloadConsumesStopBit() {
        SeiParseResult result = parser.parse(
                annexB4(bytes(0x06, 5, 2, 1, 0x80)), VideoCodec.H264, 1024, 512);

        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().get(0).payload()).containsExactly(1, 0x80);
        assertThat(result.issues()).extracting(SeiParseIssue::code)
                .containsExactly("MISSING_TRAILING_BITS");
    }

    @Test
    void shouldParseExtendedPayloadTypeAndSize() {
        byte[] payload = new byte[256];
        payload[0] = 7;
        byte[] rbsp = concat(bytes(0xFF, 5, 0xFF, 1), payload, bytes(0x80));

        SeiParseResult result = parser.parse(annexB4(concat(bytes(0x06), rbsp)), VideoCodec.H264, 1024, 512);

        assertThat(result.messages()).singleElement().satisfies(message -> {
            assertThat(message.payloadType()).isEqualTo(260);
            assertThat(message.payload()).hasSize(256).startsWith((byte) 7);
        });
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void shouldReportTruncatedExtendedHeader() {
        SeiParseResult result = parser.parse(annexB4(bytes(0x06, 0xFF)), VideoCodec.H264, 1024, 512);

        assertThat(result.messages()).isEmpty();
        assertThat(result.issues()).extracting(SeiParseIssue::code)
                .containsExactly("TRUNCATED_HEADER");
    }

    @Test
    void shouldDefensivelyCopyPayloadAndExposeImmutableCollections() {
        SeiParseResult result = parser.parse(annexB4(h264Sei(4, bytes(1))), VideoCodec.H264, 1024, 512);
        SeiMessage message = result.messages().get(0);
        byte[] payload = message.payload();
        payload[0] = 2;

        assertThat(message.payload()).containsExactly(1);
        assertThatThrownBy(() -> result.messages().add(message))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.issues().add(new SeiParseIssue("CODE", "message")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] h264Sei(int payloadType, byte[] payload) {
        return concat(bytes(0x06), seiRbsp(payloadType, payload));
    }

    private static byte[] h265Sei(int nalUnitType, int payloadType, byte[] payload) {
        return concat(bytes(nalUnitType << 1, 0x01), seiRbsp(payloadType, payload));
    }

    private static byte[] seiRbsp(int payloadType, byte[] payload) {
        return concat(bytes(payloadType, payload.length), payload, bytes(0x80));
    }

    private static byte[] annexB4(byte[] nalUnit) {
        return concat(bytes(0, 0, 0, 1), nalUnit);
    }

    private static byte[] annexB3(byte[] nalUnit) {
        return concat(bytes(0, 0, 1), nalUnit);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }

    private static byte[] concat(byte[]... arrays) {
        int length = 0;
        for (byte[] array : arrays) {
            length += array.length;
        }
        byte[] result = new byte[length];
        int position = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, position, array.length);
            position += array.length;
        }
        return result;
    }
}
