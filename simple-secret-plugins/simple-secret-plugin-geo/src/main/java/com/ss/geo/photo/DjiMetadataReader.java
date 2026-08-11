package com.ss.geo.photo;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * DJI 照片元数据读取器（直接解析 JPEG 二进制：EXIF GPS + DJI XMP）
 * <p>
 * 所有字段均直接从图片二进制数据中读取，不做任何推算或计算；标定焦距仅接受显式 DJI XMP
 * {@code drone-dji:CalibratedFocalLength}，MakerNote 因标签语义不稳定而刻意忽略。
 *
 * @author JunPzx
 * @since 2026/5/2
 */
public final class DjiMetadataReader {

    /** 默认允许读取的最大照片大小：64 MiB。 */
    public static final int DEFAULT_MAX_PHOTO_BYTES = 64 * 1024 * 1024;

    private static final String DJI_XMP_NAMESPACE = "http://www.dji.com/drone-dji/1.0/";

    // JPEG 标记
    private static final byte MARKER = (byte) 0xFF;
    private static final byte SOI_HI = (byte) 0xFF;
    private static final byte SOI_LO = (byte) 0xD8;
    private static final byte APP1 = (byte) 0xE1;
    private static final byte SOF0 = (byte) 0xC0;
    private static final byte SOF2 = (byte) 0xC2;
    private static final byte SOS = (byte) 0xDA;

    private static final String EXIF_ID = "Exif\0\0";
    private static final String XMP_ID = "http://ns.adobe.com/xap/1.0/\0";

    // EXIF GPS 标签 ID
    private static final int GPS_LAT_REF = 0x0001;
    private static final int GPS_LAT = 0x0002;
    private static final int GPS_LON_REF = 0x0003;
    private static final int GPS_LON = 0x0004;
    private static final int GPS_ALT_REF = 0x0005;
    private static final int GPS_ALT = 0x0006;

    // EXIF IFD0 标签 ID
    private static final int IMAGE_DESCRIPTION = 0x010E;
    private static final int MAKE = 0x010F;
    private static final int MODEL = 0x0110;
    private static final int SOFTWARE = 0x0131;
    private static final int MODIFY_DATE = 0x0132;

    // EXIF ExifIFD 标签 ID
    private static final int EXIF_IFD_POINTER = 0x8769;
    private static final int EXPOSURE_TIME = 0x829A;
    private static final int F_NUMBER = 0x829D;
    private static final int ISO_SPEED_RATINGS = 0x8827;
    private static final int DATE_TIME_ORIGINAL = 0x9003;
    private static final int FOCAL_LENGTH = 0x920A;
    private static final int FOCAL_PLANE_X_RES = 0xA20E;
    private static final int FOCAL_PLANE_Y_RES = 0xA20F;
    private static final int FOCAL_PLANE_RES_UNIT = 0xA210;
    private static final int DIGITAL_ZOOM_RATIO = 0xA404;
    private static final int FOCAL_LENGTH_35MM = 0xA405;
    private static final int BODY_SERIAL_NUMBER = 0xA431;
    private static final int LENS_SPECIFICATION = 0xA432;
    private static final int UNIQUE_CAMERA_MODEL = 0xA434;
    private static final int MAKER_NOTE = 0x927C;

    // TIFF 类型
    private static final int TIFF_BYTE = 1;
    private static final int TIFF_ASCII = 2;
    private static final int TIFF_SHORT = 3;
    private static final int TIFF_LONG = 4;
    private static final int TIFF_RATIONAL = 5;

    // TIFF 标签大小（字节）
    private static final int TIFF_ENTRY_SIZE = 12;

    private DjiMetadataReader() {
    }

    /**
     * 从文件读取元数据
     *
     * @param photoPath 照片文件路径
     * @return 元数据（所有字段均直接从图片中读取）
     */
    public static DjiPhotoMetadata read(Path photoPath) {
        Objects.requireNonNull(photoPath, "photoPath");
        try (InputStream inputStream = Files.newInputStream(photoPath)) {
            return read(inputStream, DEFAULT_MAX_PHOTO_BYTES);
        } catch (IOException e) {
            throw new DjiMetadataReadException("读取 DJI 照片元数据失败: " + photoPath, e);
        }
    }

    /**
     * 从 InputStream 读取元数据
     *
     * @param inputStream 输入流
     * @return 元数据（所有字段均直接从图片中读取）
     */
    public static DjiPhotoMetadata read(InputStream inputStream) {
        return read(inputStream, DEFAULT_MAX_PHOTO_BYTES);
    }

    /**
     * 从输入流读取元数据，并限制最大读取字节数。
     *
     * @param inputStream 输入流，方法不会关闭该流
     * @param maxBytes    最大读取字节数
     * @return DJI 照片元数据
     */
    public static DjiPhotoMetadata read(InputStream inputStream, int maxBytes) {
        Objects.requireNonNull(inputStream, "inputStream");
        if (maxBytes < 2 || maxBytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxBytes 必须位于 [2, Integer.MAX_VALUE) 范围内");
        }
        try {
            byte[] bytes = inputStream.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) {
                throw tooLarge(maxBytes);
            }
            return read(bytes);
        } catch (IOException e) {
            throw new DjiMetadataReadException("读取 DJI 照片输入流失败", e);
        }
    }

    /**
     * 从字节数组读取元数据
     *
     * @param jpegBytes JPEG 字节数组
     * @return 元数据（所有字段均直接从图片中读取，不做推算）
     */
    public static DjiPhotoMetadata read(byte[] jpegBytes) {
        if (jpegBytes == null || jpegBytes.length < 2
                || jpegBytes[0] != SOI_HI || jpegBytes[1] != SOI_LO) {
            throw new DjiMetadataReadException("输入内容不是有效的 JPEG 文件");
        }
        if (jpegBytes.length > DEFAULT_MAX_PHOTO_BYTES) {
            throw tooLarge(DEFAULT_MAX_PHOTO_BYTES);
        }
        DjiPhotoMetadata metadata = new DjiPhotoMetadata();
        DjiXmpCoordinates xmpCoordinates = new DjiXmpCoordinates();
        int pos = 2; // 跳过 SOI (0xFFD8)

        while (pos < jpegBytes.length) {
            if (jpegBytes[pos] != MARKER) {
                pos++;
                continue;
            }

            while (pos < jpegBytes.length && jpegBytes[pos] == MARKER) {
                pos++;
            }
            if (pos >= jpegBytes.length) {
                break;
            }

            byte markerType = jpegBytes[pos++];
            if (markerType == 0) {
                continue;
            }
            if (markerType == SOS) {
                break;
            }

            // SOI、EOI、TEM 和重启标记不包含长度字段。
            if (markerType == (byte) 0xD8 || markerType == (byte) 0xD9
                    || markerType == 0x01
                    || (markerType >= (byte) 0xD0 && markerType <= (byte) 0xD7)) {
                if (markerType == (byte) 0xD9) {
                    break;
                }
                continue;
            }

            if (pos + 2 > jpegBytes.length) {
                break;
            }
            int segLen = ((jpegBytes[pos] & 0xFF) << 8) | (jpegBytes[pos + 1] & 0xFF);
            if (segLen < 2 || segLen > jpegBytes.length - pos) {
                break;
            }
            int dataStart = pos + 2;
            int segmentEnd = pos + segLen;

            // SOF 含图片尺寸
            if (markerType == SOF0 || markerType == SOF2) {
                readSofSize(jpegBytes, dataStart, segmentEnd, metadata);
            }

            // APP1 段
            if (markerType == APP1) {
                readApp1(jpegBytes, dataStart, segmentEnd, metadata, xmpCoordinates);
            }

            pos = segmentEnd;
        }

        xmpCoordinates.applyTo(metadata);
        return metadata;
    }

    private static void readSofSize(byte[] data, int dataStart, int segmentEnd, DjiPhotoMetadata meta) {
        if (segmentEnd - dataStart >= 5) {
            meta.setImageHeight(((data[dataStart + 1] & 0xFF) << 8) | (data[dataStart + 2] & 0xFF));
            meta.setImageWidth(((data[dataStart + 3] & 0xFF) << 8) | (data[dataStart + 4] & 0xFF));
        }
    }

    private static void readApp1(byte[] data, int dataStart, int segmentEnd, DjiPhotoMetadata meta,
                                 DjiXmpCoordinates xmpCoordinates) {
        int dataLen = segmentEnd - dataStart;
        if (dataLen <= 0) {
            return;
        }

        String id = new String(data, dataStart, Math.min(dataLen, 64), StandardCharsets.US_ASCII);

        if (id.startsWith(EXIF_ID)) {
            try {
                parseExif(data, dataStart + EXIF_ID.length(), dataLen - EXIF_ID.length(), meta);
            } catch (RuntimeException ignored) {
                // 损坏的 EXIF 段不应影响 SOF 或其他 APP1 段的读取。
            }
        } else if (id.startsWith(XMP_ID)) {
            String xmpXml = new String(data, dataStart + XMP_ID.length(),
                    dataLen - XMP_ID.length(), StandardCharsets.UTF_8);
            parseDjiXmp(xmpXml, meta, xmpCoordinates);
        }
    }

    // === EXIF / TIFF ===

    private static void parseExif(byte[] data, int offset, int length, DjiPhotoMetadata meta) {
        if (length < 8) {
            return;
        }

        ByteBuffer buf = ByteBuffer.wrap(data, offset, length).slice();
        boolean littleEndian = data[offset] == 'I' && data[offset + 1] == 'I';
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);

        short tiffMagic = buf.getShort(2);
        if (tiffMagic != 0x002A) {
            return;
        }

        int ifd0Offset = buf.getInt(4);

        // 从 IFD0 中找到 GPS IFD、ExifIFD 指针，同时读取 IFD0 字段
        int gpsIfdOffset = -1;
        int exifIfdOffset = -1;
        int ifd0Count = buf.getShort(ifd0Offset) & 0xFFFF;
        for (int i = 0; i < ifd0Count; i++) {
            int entryPos = ifd0Offset + 2 + i * TIFF_ENTRY_SIZE;
            int tag = buf.getShort(entryPos) & 0xFFFF;
            int type = buf.getShort(entryPos + 2) & 0xFFFF;
            int count = buf.getInt(entryPos + 4);
            switch (tag) {
                case 0x8825 -> gpsIfdOffset = buf.getInt(entryPos + 8);
                case EXIF_IFD_POINTER -> exifIfdOffset = buf.getInt(entryPos + 8);
                case MAKE -> meta.setMake(readAsciiValue(buf, entryPos, type, count));
                case MODEL -> meta.setModel(readAsciiValue(buf, entryPos, type, count));
                case SOFTWARE -> meta.setSoftware(readAsciiValue(buf, entryPos, type, count));
                case MODIFY_DATE -> meta.setModifyDate(readAsciiValue(buf, entryPos, type, count));
                case IMAGE_DESCRIPTION -> meta.setImageDescription(readAsciiValue(buf, entryPos, type, count));
            }
        }

        if (gpsIfdOffset > 0) {
            parseGpsIfd(buf, gpsIfdOffset, meta);
        }

        if (exifIfdOffset > 0) {
            parseExifIfd(buf, exifIfdOffset, meta);
        }
    }

    private static void parseGpsIfd(ByteBuffer buf, int gpsIfdOffset, DjiPhotoMetadata meta) {
        int entryCount = buf.getShort(gpsIfdOffset) & 0xFFFF;
        Double lat = null, lon = null, alt = null;
        String latRef = null, lonRef = null;
        Double altRef = null;

        for (int i = 0; i < entryCount; i++) {
            int entryPos = gpsIfdOffset + 2 + i * TIFF_ENTRY_SIZE;
            int tag = buf.getShort(entryPos) & 0xFFFF;
            int type = buf.getShort(entryPos + 2) & 0xFFFF;
            int count = buf.getInt(entryPos + 4);

            switch (tag) {
                case GPS_LAT_REF -> latRef = readAsciiValue(buf, entryPos, type, count);
                case GPS_LON_REF -> lonRef = readAsciiValue(buf, entryPos, type, count);
                case GPS_LAT -> lat = readRationalTriple(buf, entryPos);
                case GPS_LON -> lon = readRationalTriple(buf, entryPos);
                case GPS_ALT_REF -> altRef = (double) (buf.get(entryPos + 8) & 0xFF);
                case GPS_ALT -> alt = readRationalValue(buf, entryPos);
            }
        }

        if (lat != null && meta.getGpsLat() == null) {
            meta.setGpsLat(("S".equalsIgnoreCase(latRef) ? -1 : 1) * lat);
        }
        if (lon != null && meta.getGpsLon() == null) {
            meta.setGpsLon(("W".equalsIgnoreCase(lonRef) ? -1 : 1) * lon);
        }
        if (alt != null && meta.getGpsAlt() == null) {
            meta.setGpsAlt(altRef != null && altRef == 1 ? -alt : alt);
        }
    }

    private static void parseExifIfd(ByteBuffer buf, int exifIfdOffset, DjiPhotoMetadata meta) {
        int entryCount = buf.getShort(exifIfdOffset) & 0xFFFF;

        for (int i = 0; i < entryCount; i++) {
            int entryPos = exifIfdOffset + 2 + i * TIFF_ENTRY_SIZE;
            int tag = buf.getShort(entryPos) & 0xFFFF;
            int type = buf.getShort(entryPos + 2) & 0xFFFF;
            int count = buf.getInt(entryPos + 4);

            switch (tag) {
                // 拍摄参数
                case EXPOSURE_TIME -> {
                    if (type == TIFF_RATIONAL) {
                        meta.setExposureTime(readRationalValue(buf, entryPos));
                    }
                }
                case F_NUMBER -> {
                    if (type == TIFF_RATIONAL) {
                        meta.setFNumber(readRationalValue(buf, entryPos));
                    }
                }
                case ISO_SPEED_RATINGS -> meta.setIso(buf.getShort(entryPos + 8) & 0xFFFF);
                case DATE_TIME_ORIGINAL -> meta.setDateTimeOriginal(readAsciiValue(buf, entryPos, type, count));

                // 焦距相关（直接读取，不做计算）
                case FOCAL_LENGTH -> {
                    if (type == TIFF_RATIONAL) {
                        meta.setFocalLength(readRationalValue(buf, entryPos));
                    }
                }
                case FOCAL_PLANE_X_RES -> {
                    if (type == TIFF_RATIONAL) {
                        meta.setFocalPlaneXResolution(readRationalValue(buf, entryPos));
                    }
                }
                case FOCAL_PLANE_Y_RES -> {
                    if (type == TIFF_RATIONAL) {
                        meta.setFocalPlaneYResolution(readRationalValue(buf, entryPos));
                    }
                }
                case FOCAL_PLANE_RES_UNIT -> meta.setFocalPlaneResolutionUnit(buf.getShort(entryPos + 8) & 0xFFFF);
                case DIGITAL_ZOOM_RATIO -> {
                    if (type == TIFF_RATIONAL) {
                        setPositiveFiniteDouble(readRationalValue(buf, entryPos), meta::setDigitalZoomRatio);
                    }
                }
                case FOCAL_LENGTH_35MM -> meta.setFocalLength35mm((double) (buf.getShort(entryPos + 8) & 0xFFFF));

                // 设备标识
                case BODY_SERIAL_NUMBER -> meta.setBodySerialNumber(readAsciiValue(buf, entryPos, type, count));
                case LENS_SPECIFICATION -> meta.setLensSpecification(readLensSpecification(buf, entryPos));
                case UNIQUE_CAMERA_MODEL -> meta.setUniqueCameraModel(readAsciiValue(buf, entryPos, type, count));

                // MakerNote 是厂商私有结构；未知标签不猜测为标定焦距。
                case MAKER_NOTE -> { }
            }
        }
    }

    /**
     * 读取镜头规格，格式化为 "minFL-maxFL mm f/minF-maxF"
     *
     * @param buf      TIFF 字节缓冲
     * @param entryPos IFD 条目位置
     * @return 镜头规格字符串
     */
    private static String readLensSpecification(ByteBuffer buf, int entryPos) {
        int offset = buf.getInt(entryPos + 8);
        double minFL = readRational(buf, offset);
        double maxFL = readRational(buf, offset + 8);
        double minF = readRational(buf, offset + 16);
        double maxF = readRational(buf, offset + 24);
        if (minFL == 0 && maxFL == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (minFL == maxFL) {
            sb.append(String.format("%.1f", minFL));
        } else {
            sb.append(String.format("%.1f-%.1f", minFL, maxFL));
        }
        sb.append("mm f/");
        if (minF == maxF) {
            sb.append(String.format("%.1f", minF));
        } else {
            sb.append(String.format("%.1f-%.1f", minF, maxF));
        }
        return sb.toString();
    }

    private static Double readRationalTriple(ByteBuffer buf, int entryPos) {
        // type=RATIONAL(5), count=3 → degrees, minutes, seconds
        int offset = buf.getInt(entryPos + 8);
        double deg = readRational(buf, offset);
        double min = readRational(buf, offset + 8);
        double sec = readRational(buf, offset + 16);
        return deg + min / 60.0 + sec / 3600.0;
    }

    private static Double readRationalValue(ByteBuffer buf, int entryPos) {
        int offset = buf.getInt(entryPos + 8);
        return readRational(buf, offset);
    }

    private static double readRational(ByteBuffer buf, int offset) {
        long num = buf.getInt(offset) & 0xFFFFFFFFL;
        long den = buf.getInt(offset + 4) & 0xFFFFFFFFL;
        return den != 0 ? (double) num / den : 0;
    }

    /**
     * 读取 ASCII 字符串值
     *
     * @param buf      TIFF 字节缓冲
     * @param entryPos IFD 条目位置
     * @param type     TIFF 类型
     * @param count    字符数（含末尾 \0）
     * @return 字符串值
     */
    private static String readAsciiValue(ByteBuffer buf, int entryPos, int type, int count) {
        if (count <= 0 || count > buf.limit()) {
            return null;
        }
        if (count <= 4) {
            byte[] bytes = new byte[count];
            buf.position(entryPos + 8);
            buf.get(bytes);
            // 去掉末尾的 \0
            int end = count;
            while (end > 0 && bytes[end - 1] == 0) {
                end--;
            }
            return new String(bytes, 0, end, StandardCharsets.US_ASCII).trim();
        }
        // count > 4，数据存储在偏移量指向的位置
        int offset = buf.getInt(entryPos + 8);
        if (offset < 0 || offset > buf.limit() - count) {
            return null;
        }
        byte[] bytes = new byte[count];
        buf.position(offset);
        buf.get(bytes);
        int end = count;
        while (end > 0 && bytes[end - 1] == 0) {
            end--;
        }
        return new String(bytes, 0, end, StandardCharsets.US_ASCII).trim();
    }

    // === XMP ===

    private static void parseDjiXmp(String xmpXml, DjiPhotoMetadata metadata,
                                    DjiXmpCoordinates aggregateCoordinates) {
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            // 禁用外部实体解析，防止 XXE
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            javax.xml.stream.XMLEventReader reader =
                    factory.createXMLEventReader(new StringReader(xmpXml));
            DjiXmpCoordinates segmentCoordinates = new DjiXmpCoordinates();

            while (reader.hasNext()) {
                XMLEvent event = reader.nextEvent();
                if (event.isStartElement()) {
                    StartElement element = event.asStartElement();
                    if (!"Description".equals(element.getName().getLocalPart())
                            || !"http://www.w3.org/1999/02/22-rdf-syntax-ns#".equals(element.getName().getNamespaceURI())) {
                        continue;
                    }
                    parseDjiAttributes(element, metadata, segmentCoordinates);
                    // DJI XMP 通常只有一个 rdf:Description 包含所有 drone-dji 属性
                }
            }
            aggregateCoordinates.merge(segmentCoordinates);
        } catch (XMLStreamException ignored) {
            // 非法或包含 DTD/外部实体的 XMP 被安全忽略。
        }
    }

    private static void parseDjiAttributes(StartElement element, DjiPhotoMetadata metadata,
                                           DjiXmpCoordinates coordinates) {
        var attrs = element.getAttributes();
        while (attrs.hasNext()) {
            var attr = attrs.next();
            String ns = attr.getName().getNamespaceURI();
            if (!DJI_XMP_NAMESPACE.equals(ns)) {
                continue;
            }
            String localName = attr.getName().getLocalPart();
            String value = attr.getValue();

            switch (localName) {
                // DJI XMP 可能包含 RTK 修正坐标，优先级高于 EXIF GPS。
                case "GpsLatitude" -> coordinates.gpsLat = parseDouble(value);
                case "GpsLongitude" -> coordinates.gpsLon = parseDouble(value);
                case "AbsoluteAltitude" -> coordinates.gpsAlt = parseDouble(value);
                case "RtkLatitude" -> coordinates.rtkLat = parseDouble(value);
                case "RtkLongitude" -> coordinates.rtkLon = parseDouble(value);
                case "RtkAltitude" -> coordinates.rtkAlt = parseDouble(value);

                // 姿态
                case "RelativeAltitude" -> setFiniteDouble(value, metadata::setRelativeAltitude);
                case "FlightYawDegree" -> setFiniteDouble(value, metadata::setFlightYaw);
                case "FlightPitchDegree" -> setFiniteDouble(value, metadata::setFlightPitch);
                case "FlightRollDegree" -> setFiniteDouble(value, metadata::setFlightRoll);
                case "GimbalYawDegree" -> setFiniteDouble(value, metadata::setGimbalYaw);
                case "GimbalPitchDegree" -> setFiniteDouble(value, metadata::setGimbalPitch);
                case "GimbalRollDegree" -> setFiniteDouble(value, metadata::setGimbalRoll);

                // 标定焦距（直接从 XMP 读取，不做计算）
                case "CalibratedFocalLength" -> setFiniteDouble(value, metadata::setCalibratedFocalLength);

                // 光学/规格变焦与数字裁切倍率语义不同，分别保留。
                case "ZoomFactor", "OpticalZoomFactor" -> setFiniteDouble(value, metadata::setZoomFactor);
                case "DigitalZoomRatio" -> setPositiveFiniteDouble(value, metadata::setDigitalZoomRatio);

                // 版本与来源
                case "Version" -> metadata.setDjiVersion(value);
                case "ImageSource" -> metadata.setImageSource(value);
                case "DroneTypeCode" -> metadata.setDroneTypeCode(value);

                // GPS 状态
                case "GpsStatus" -> metadata.setGpsStatus(value);
                case "AltitudeType" -> metadata.setAltitudeType(value);

                // RTK 精度
                case "RtkFlag" -> metadata.setRtkFlag(parseInt(value));
                case "RtkStdLon" -> setFiniteDouble(value, metadata::setRtkStdLon);
                case "RtkStdLat" -> setFiniteDouble(value, metadata::setRtkStdLat);
                case "RtkStdHgt" -> setFiniteDouble(value, metadata::setRtkStdHgt);
                case "RtkDiffAge" -> setFiniteDouble(value, metadata::setRtkDiffAge);

                // 飞行速度
                case "FlightXSpeed" -> setFiniteDouble(value, metadata::setFlightXSpeed);
                case "FlightYSpeed" -> setFiniteDouble(value, metadata::setFlightYSpeed);
                case "FlightZSpeed" -> setFiniteDouble(value, metadata::setFlightZSpeed);

                // 反向标志
                case "CamReverse" -> metadata.setCamReverse(parseInt(value));
                case "GimbalReverse" -> metadata.setGimbalReverse(parseInt(value));

                // 传感器
                case "SensorTemperature" -> setFiniteDouble(value, metadata::setSensorTemperature);

                // 设备标识
                case "ProductName" -> metadata.setProductName(value);
                case "DroneModel" -> metadata.setDroneModel(value);
                case "DroneSerialNumber" -> metadata.setDroneSerialNumber(value);
                case "CameraSerialNumber" -> metadata.setCameraSerialNumber(value);

                // 拍摄参数
                case "ShutterType" -> metadata.setShutterType(value);
                case "SensorFPS" -> setFiniteDouble(value, metadata::setSensorFPS);
                case "WhiteBalanceCCT" -> metadata.setWhiteBalanceCCT(parseInt(value));

                // 测量模式
                case "SurveyingMode" -> metadata.setSurveyingMode(parseInt(value));

                // 时间
                case "UTCAtExposure" -> metadata.setUtcAtExposure(value);

                // 自定义数据
                case "SelfData" -> metadata.setSelfData(value);

                default -> {
                    // 未识别的 DJI 扩展属性保持向前兼容并忽略。
                }
            }
        }
    }

    private static final class DjiXmpCoordinates {
        private Double gpsLat;
        private Double gpsLon;
        private Double gpsAlt;
        private Double rtkLat;
        private Double rtkLon;
        private Double rtkAlt;

        private void merge(DjiXmpCoordinates source) {
            gpsLat = source.gpsLat != null ? source.gpsLat : gpsLat;
            gpsLon = source.gpsLon != null ? source.gpsLon : gpsLon;
            gpsAlt = source.gpsAlt != null ? source.gpsAlt : gpsAlt;
            rtkLat = source.rtkLat != null ? source.rtkLat : rtkLat;
            rtkLon = source.rtkLon != null ? source.rtkLon : rtkLon;
            rtkAlt = source.rtkAlt != null ? source.rtkAlt : rtkAlt;
        }

        private void applyTo(DjiPhotoMetadata metadata) {
            if (validLatitude(gpsLat) && validLongitude(gpsLon)) {
                metadata.setGpsLat(gpsLat);
                metadata.setGpsLon(gpsLon);
            }
            if (gpsAlt != null) {
                metadata.setGpsAlt(gpsAlt);
            }
            if (validLatitude(rtkLat) && validLongitude(rtkLon) && rtkAlt != null) {
                metadata.setGpsLat(rtkLat);
                metadata.setGpsLon(rtkLon);
                metadata.setGpsAlt(rtkAlt);
            }
        }

        private static boolean validLatitude(Double value) {
            return value != null && value >= -90.0 && value <= 90.0;
        }

        private static boolean validLongitude(Double value) {
            return value != null && value >= -180.0 && value <= 180.0;
        }
    }

    private static DjiMetadataReadException tooLarge(int maxBytes) {
        return new DjiMetadataReadException("DJI 照片大小超过允许上限 " + maxBytes + " 字节");
    }

    private static void setFiniteDouble(String text, Consumer<Double> setter) {
        Double value = parseDouble(text);
        if (value != null) {
            setter.accept(value);
        }
    }

    private static void setPositiveFiniteDouble(String text, Consumer<Double> setter) {
        setPositiveFiniteDouble(parseDouble(text), setter);
    }

    private static void setPositiveFiniteDouble(Double value, Consumer<Double> setter) {
        if (value != null && value > 0.0 && Double.isFinite(value)) {
            setter.accept(value);
        }
    }

    private static Double parseDouble(String text) {
        try {
            double value = Double.parseDouble(text);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
