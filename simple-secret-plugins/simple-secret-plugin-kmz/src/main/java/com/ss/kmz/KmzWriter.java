package com.ss.kmz;

import com.ss.kmz.domain.KmzMission;
import com.ss.kmz.exception.KmzException;
import com.ss.kmz.internal.NonClosingOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 将航点任务写为包含 {@code doc.kml} 的 KMZ。
 */
public final class KmzWriter {

    private KmzWriter() {
    }

    /** 将任务写入 KMZ 文件。 */
    public static void write(KmzMission mission, Path path) {
        if (path == null) {
            throw new IllegalArgumentException("KMZ path must not be null");
        }
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            writeToStream(mission, outputStream);
        } catch (IOException exception) {
            throw new KmzException("写入 KMZ 文件失败: " + path, exception);
        }
    }

    /** 将任务写入调用方输出流，输出流不会被关闭。 */
    public static void writeToStream(KmzMission mission, OutputStream outputStream) {
        if (outputStream == null) {
            throw new IllegalArgumentException("KMZ output stream must not be null");
        }
        byte[] kml = KmlWriter.writeToString(mission).getBytes(StandardCharsets.UTF_8);
        try (ZipOutputStream zip = new ZipOutputStream(new NonClosingOutputStream(outputStream))) {
            ZipEntry entry = new ZipEntry("doc.kml");
            entry.setTime(0L);
            zip.putNextEntry(entry);
            zip.write(kml);
            zip.closeEntry();
            zip.finish();
        } catch (IOException exception) {
            throw new KmzException("写入 KMZ 失败", exception);
        }
    }

    /** 将任务序列化为 KMZ 字节数组。 */
    public static byte[] writeToBytes(KmzMission mission) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeToStream(mission, output);
        return output.toByteArray();
    }
}
