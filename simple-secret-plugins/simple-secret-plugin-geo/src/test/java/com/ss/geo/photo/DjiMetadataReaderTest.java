package com.ss.geo.photo;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DjiMetadataReaderTest {

    @Test
    void shouldReadImageSizeAndDjiXmpFromSyntheticJpeg() {
        String xmp = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
                + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
                + "<rdf:Description xmlns:drone-dji=\"http://www.dji.com/drone-dji/1.0/\""
                + " drone-dji:GpsLatitude=\"31.2304\" drone-dji:GpsLongitude=\"121.4737\""
                + " drone-dji:AbsoluteAltitude=\"120.5\" drone-dji:GimbalPitchDegree=\"-90\""
                + " drone-dji:ImageSource=\"WideCamera\"/>"
                + "</rdf:RDF></x:xmpmeta>";

        DjiPhotoMetadata metadata = DjiMetadataReader.read(jpegWithSofAndXmp(4000, 3000, xmp));

        assertThat(metadata.getImageWidth()).isEqualTo(4000);
        assertThat(metadata.getImageHeight()).isEqualTo(3000);
        assertThat(metadata.getGpsLat()).isEqualTo(31.2304);
        assertThat(metadata.getGpsLon()).isEqualTo(121.4737);
        assertThat(metadata.getGpsAlt()).isEqualTo(120.5);
        assertThat(metadata.getGimbalPitch()).isEqualTo(-90.0);
        assertThat(metadata.getImageSource()).isEqualTo("WideCamera");
    }

    @Test
    void invalidJpegShouldFailClearlyWhileTruncatedSegmentStaysSafe() {
        assertThatThrownBy(() -> DjiMetadataReader.read(new byte[]{1, 2, 3}))
                .isInstanceOf(DjiMetadataReadException.class)
                .hasMessageContaining("JPEG");

        byte[] truncated = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0x01};
        assertThatCode(() -> DjiMetadataReader.read(truncated)).doesNotThrowAnyException();
    }

    @Test
    void inputStreamShouldRespectCallerProvidedLimit() {
        byte[] input = new byte[17];

        assertThatThrownBy(() -> DjiMetadataReader.read(new ByteArrayInputStream(input), 16))
                .isInstanceOf(DjiMetadataReadException.class)
                .hasMessageContaining("16");
    }

    @Test
    void xmpDtdAndExternalEntityShouldNotBeResolved() {
        String xmp = "<!DOCTYPE x [<!ENTITY secret SYSTEM \"file:///etc/passwd\">]>"
                + "<rdf:Description xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\""
                + " xmlns:drone-dji=\"http://www.dji.com/drone-dji/1.0/\""
                + " drone-dji:ProductName=\"&secret;\"/>";

        DjiPhotoMetadata metadata = DjiMetadataReader.read(jpegWithSofAndXmp(1, 1, xmp));

        assertThat(metadata.getProductName()).isNull();
    }

    @Test
    void rtkCoordinatesShouldOverrideGpsRegardlessOfXmlOrder() {
        String xmp = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
                + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
                + "<rdf:Description xmlns:drone-dji=\"http://www.dji.com/drone-dji/1.0/\""
                + " drone-dji:RtkLatitude=\"31.1\" drone-dji:RtkLongitude=\"121.1\""
                + " drone-dji:RtkAltitude=\"88.0\"/>"
                + "<rdf:Description xmlns:drone-dji=\"http://www.dji.com/drone-dji/1.0/\""
                + " drone-dji:GpsLatitude=\"32.2\" drone-dji:GpsLongitude=\"122.2\""
                + " drone-dji:AbsoluteAltitude=\"99.0\"/>"
                + "</rdf:RDF></x:xmpmeta>";

        DjiPhotoMetadata metadata = DjiMetadataReader.read(jpegWithSofAndXmp(1, 1, xmp));

        assertThat(metadata.getGpsLat()).isEqualTo(31.1);
        assertThat(metadata.getGpsLon()).isEqualTo(121.1);
        assertThat(metadata.getGpsAlt()).isEqualTo(88.0);
    }

    @Test
    void unknownMakerNoteRationalShouldNotBecomeCalibratedFocalLength() {
        DjiPhotoMetadata metadata = DjiMetadataReader.read(jpegWithUnknownMakerNoteRational());

        assertThat(metadata.getCalibratedFocalLength()).isNull();
    }

    @Test
    void partialRtkTupleShouldNotMixWithGpsCoordinates() {
        String xmp = "<rdf:Description xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\""
                + " xmlns:drone-dji=\"http://www.dji.com/drone-dji/1.0/\""
                + " drone-dji:GpsLatitude=\"32.2\" drone-dji:GpsLongitude=\"122.2\""
                + " drone-dji:AbsoluteAltitude=\"99.0\""
                + " drone-dji:RtkLatitude=\"31.1\"/>";

        DjiPhotoMetadata metadata = DjiMetadataReader.read(jpegWithSofAndXmp(1, 1, xmp));

        assertThat(metadata.getGpsLat()).isEqualTo(32.2);
        assertThat(metadata.getGpsLon()).isEqualTo(122.2);
        assertThat(metadata.getGpsAlt()).isEqualTo(99.0);
    }

    @Test
    void shouldReadGpsAndFocalFieldsFromLittleAndBigEndianExif() {
        for (ByteOrder order : new ByteOrder[]{ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN}) {
            DjiPhotoMetadata metadata = DjiMetadataReader.read(jpegWithExif(order));

            assertThat(metadata.getGpsLat()).isCloseTo(
                    31.2304, org.assertj.core.data.Offset.offset(1.0e-10));
            assertThat(metadata.getGpsLon()).isCloseTo(
                    121.4737, org.assertj.core.data.Offset.offset(1.0e-10));
            assertThat(metadata.getGpsAlt()).isEqualTo(120.5);
            assertThat(metadata.getFocalLength()).isEqualTo(10.0);
            assertThat(metadata.getFocalPlaneXResolution()).isEqualTo(100.0);
            assertThat(metadata.getFocalPlaneResolutionUnit()).isEqualTo(4);
            assertThat(metadata.getFocalLength35mm()).isEqualTo(36.0);
        }
    }

    @Test
    void malformedExifIfdOffsetShouldBeIgnored() {
        byte[] tiff = new byte[16];
        ByteBuffer buffer = ByteBuffer.wrap(tiff).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(0, (byte) 'I');
        buffer.put(1, (byte) 'I');
        buffer.putShort(2, (short) 42);
        buffer.putInt(4, Integer.MAX_VALUE);

        DjiPhotoMetadata metadata = DjiMetadataReader.read(wrapExif(tiff));

        assertThat(metadata.getGpsLat()).isNull();
        assertThat(metadata.getFocalLength()).isNull();
    }

    @Test
    void rtkCoordinatesShouldWinAcrossMultipleXmpSegments() {
        String rtk = "<rdf:Description xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\""
                + " xmlns:drone-dji=\"http://www.dji.com/drone-dji/1.0/\""
                + " drone-dji:RtkLatitude=\"31.1\" drone-dji:RtkLongitude=\"121.1\""
                + " drone-dji:RtkAltitude=\"88.0\"/>";
        String gps = "<rdf:Description xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\""
                + " xmlns:drone-dji=\"http://www.dji.com/drone-dji/1.0/\""
                + " drone-dji:GpsLatitude=\"32.2\" drone-dji:GpsLongitude=\"122.2\""
                + " drone-dji:AbsoluteAltitude=\"99.0\"/>";

        DjiPhotoMetadata metadata = DjiMetadataReader.read(
                jpegWithSofAndXmps(1, 1, List.of(rtk, gps)));

        assertThat(metadata.getGpsLat()).isEqualTo(31.1);
        assertThat(metadata.getGpsLon()).isEqualTo(121.1);
        assertThat(metadata.getGpsAlt()).isEqualTo(88.0);
    }

    private static byte[] jpegWithSofAndXmp(int width, int height, String xmp) {
        return jpegWithSofAndXmps(width, height, List.of(xmp));
    }

    private static byte[] jpegWithSofAndXmps(int width, int height, List<String> xmps) {
        byte[] xmpId = "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.US_ASCII);
        int totalLength = 2 + 9 + 2;
        for (String xmp : xmps) {
            totalLength += 4 + xmpId.length + xmp.getBytes(StandardCharsets.UTF_8).length;
        }
        byte[] jpeg = new byte[totalLength];
        int offset = 0;
        jpeg[offset++] = (byte) 0xFF;
        jpeg[offset++] = (byte) 0xD8;
        jpeg[offset++] = (byte) 0xFF;
        jpeg[offset++] = (byte) 0xC0;
        jpeg[offset++] = 0;
        jpeg[offset++] = 7;
        jpeg[offset++] = 8;
        jpeg[offset++] = (byte) (height >>> 8);
        jpeg[offset++] = (byte) height;
        jpeg[offset++] = (byte) (width >>> 8);
        jpeg[offset++] = (byte) width;
        for (String xmp : xmps) {
            byte[] xml = xmp.getBytes(StandardCharsets.UTF_8);
            int app1Length = 2 + xmpId.length + xml.length;
            jpeg[offset++] = (byte) 0xFF;
            jpeg[offset++] = (byte) 0xE1;
            jpeg[offset++] = (byte) (app1Length >>> 8);
            jpeg[offset++] = (byte) app1Length;
            System.arraycopy(xmpId, 0, jpeg, offset, xmpId.length);
            offset += xmpId.length;
            System.arraycopy(xml, 0, jpeg, offset, xml.length);
            offset += xml.length;
        }
        jpeg[offset++] = (byte) 0xFF;
        jpeg[offset] = (byte) 0xD9;
        return jpeg;
    }

    private static byte[] jpegWithUnknownMakerNoteRational() {
        byte[] tiff = new byte[52];
        ByteBuffer buffer = ByteBuffer.wrap(tiff).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(0, (byte) 'I');
        buffer.put(1, (byte) 'I');
        buffer.putShort(2, (short) 42);
        buffer.putInt(4, 8);
        buffer.putShort(8, (short) 1);
        buffer.putShort(10, (short) 0x927C);
        buffer.putShort(12, (short) 7);
        buffer.putInt(14, 26);
        buffer.putInt(18, 26);
        buffer.putInt(22, 0);
        buffer.put(26, (byte) 'D');
        buffer.put(27, (byte) 'J');
        buffer.put(28, (byte) 'I');
        buffer.put(29, (byte) 0);
        buffer.putShort(30, (short) 1);
        buffer.putShort(32, (short) 1);
        buffer.putShort(34, (short) 5);
        buffer.putInt(36, 1);
        buffer.putInt(40, 44);
        buffer.putInt(44, 1000);
        buffer.putInt(48, 1);

        byte[] exifId = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        int app1Length = 2 + exifId.length + tiff.length;
        byte[] jpeg = new byte[2 + 2 + app1Length + 2];
        int offset = 0;
        jpeg[offset++] = (byte) 0xFF;
        jpeg[offset++] = (byte) 0xD8;
        jpeg[offset++] = (byte) 0xFF;
        jpeg[offset++] = (byte) 0xE1;
        jpeg[offset++] = (byte) (app1Length >>> 8);
        jpeg[offset++] = (byte) app1Length;
        System.arraycopy(exifId, 0, jpeg, offset, exifId.length);
        offset += exifId.length;
        System.arraycopy(tiff, 0, jpeg, offset, tiff.length);
        offset += tiff.length;
        jpeg[offset++] = (byte) 0xFF;
        jpeg[offset] = (byte) 0xD9;
        return jpeg;
    }

    private static byte[] jpegWithExif(ByteOrder order) {
        byte[] tiff = new byte[320];
        ByteBuffer buffer = ByteBuffer.wrap(tiff).order(order);
        buffer.put(0, (byte) (order == ByteOrder.LITTLE_ENDIAN ? 'I' : 'M'));
        buffer.put(1, (byte) (order == ByteOrder.LITTLE_ENDIAN ? 'I' : 'M'));
        buffer.putShort(2, (short) 42);
        buffer.putInt(4, 8);

        buffer.putShort(8, (short) 2);
        putEntry(buffer, 10, 0x8825, 4, 1, 40);
        putEntry(buffer, 22, 0x8769, 4, 1, 140);
        buffer.putInt(34, 0);

        buffer.putShort(40, (short) 6);
        putEntry(buffer, 42, 1, 2, 2, 0);
        buffer.put(50, (byte) 'N');
        putEntry(buffer, 54, 2, 5, 3, 220);
        putEntry(buffer, 66, 3, 2, 2, 0);
        buffer.put(74, (byte) 'E');
        putEntry(buffer, 78, 4, 5, 3, 244);
        putEntry(buffer, 90, 5, 1, 1, 0);
        putEntry(buffer, 102, 6, 5, 1, 268);
        buffer.putInt(114, 0);

        buffer.putShort(140, (short) 4);
        putEntry(buffer, 142, 0x920A, 5, 1, 276);
        putEntry(buffer, 154, 0xA20E, 5, 1, 284);
        putEntry(buffer, 166, 0xA210, 3, 1, 0);
        buffer.putShort(174, (short) 4);
        putEntry(buffer, 178, 0xA405, 3, 1, 0);
        buffer.putShort(186, (short) 36);
        buffer.putInt(190, 0);

        putRational(buffer, 220, 31, 1);
        putRational(buffer, 228, 13, 1);
        putRational(buffer, 236, 4944, 100);
        putRational(buffer, 244, 121, 1);
        putRational(buffer, 252, 28, 1);
        putRational(buffer, 260, 2532, 100);
        putRational(buffer, 268, 1205, 10);
        putRational(buffer, 276, 10, 1);
        putRational(buffer, 284, 100, 1);
        return wrapExif(tiff);
    }

    private static void putEntry(ByteBuffer buffer, int offset, int tag, int type, int count, int value) {
        buffer.putShort(offset, (short) tag);
        buffer.putShort(offset + 2, (short) type);
        buffer.putInt(offset + 4, count);
        buffer.putInt(offset + 8, value);
    }

    private static void putRational(ByteBuffer buffer, int offset, int numerator, int denominator) {
        buffer.putInt(offset, numerator);
        buffer.putInt(offset + 4, denominator);
    }

    private static byte[] wrapExif(byte[] tiff) {
        byte[] exifId = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        int app1Length = 2 + exifId.length + tiff.length;
        byte[] jpeg = new byte[2 + 2 + app1Length + 2];
        int offset = 0;
        jpeg[offset++] = (byte) 0xFF;
        jpeg[offset++] = (byte) 0xD8;
        jpeg[offset++] = (byte) 0xFF;
        jpeg[offset++] = (byte) 0xE1;
        jpeg[offset++] = (byte) (app1Length >>> 8);
        jpeg[offset++] = (byte) app1Length;
        System.arraycopy(exifId, 0, jpeg, offset, exifId.length);
        offset += exifId.length;
        System.arraycopy(tiff, 0, jpeg, offset, tiff.length);
        offset += tiff.length;
        jpeg[offset++] = (byte) 0xFF;
        jpeg[offset] = (byte) 0xD9;
        return jpeg;
    }
}
