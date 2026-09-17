package com.ss.ics.dahua.internal;

import com.ss.ics.dahua.internal.model.DahuaNativeStreamFrame;

import java.util.Arrays;

/**
 * 大华私有码流解析器：剥离 DHAV 私有帧头，输出 Annex-B 视频帧。
 *
 * <p>部分设备在实时预览时只下发 {@code dataType=0} 的私有码流：缓冲由一个或多个 DHAV 帧
 * 组成，帧头包含 magic("DHAV")、厂商帧类型、毫秒时间戳（偏移 8）和含头的整帧长度（偏移 12），
 * 视频负载从帧头之后的 Annex-B start code 开始，头长度因固件而异。解析器按长度字段分帧，
 * 在固定头之后扫描 start code 定位负载；音频、信息等不含 start code 的帧会被跳过。</p>
 *
 * @author junpzx
 * @since 2026-09-16
 */
public final class DahuaPrivateStreamParser {

    /** magic、帧类型、保留字段到长度字段的最小头部字节数。 */
    private static final int FIXED_HEADER_BYTES = 16;

    /** start code 扫描窗口上限，超过视为无效帧。 */
    private static final int MAX_HEADER_SCAN_BYTES = 192;

    private DahuaPrivateStreamParser() {
    }

    /**
     * 解析私有码流缓冲并逐帧回调。
     *
     * @param buffer 完整私有码流缓冲
     * @param callback 帧消费者
     */
    public static void parse(byte[] buffer, DahuaNativeStreamCallback callback) {
        int offset = 0;
        while (offset + FIXED_HEADER_BYTES <= buffer.length) {
            if (!isDhavFrame(buffer, offset)) {
                return;
            }
            int frameLength = (int) unsignedIntLittleEndian(buffer, offset + 12);
            if (frameLength < FIXED_HEADER_BYTES || frameLength > buffer.length - offset) {
                return;
            }
            long timestamp = unsignedIntLittleEndian(buffer, offset + 8);
            int frameType = buffer[offset + 4] & 0xFF;
            int payloadStart = annexBStart(buffer, offset + FIXED_HEADER_BYTES,
                    Math.min(offset + MAX_HEADER_SCAN_BYTES, offset + frameLength));
            if (payloadStart >= 0) {
                byte[] data = Arrays.copyOfRange(buffer, payloadStart, offset + frameLength);
                callback.onFrame(new DahuaNativeStreamFrame(
                        data, timestamp, timestamp, frameType, 0));
            }
            offset += frameLength;
        }
    }

    private static boolean isDhavFrame(byte[] buffer, int offset) {
        return buffer[offset] == 'D' && buffer[offset + 1] == 'H'
                && buffer[offset + 2] == 'A' && buffer[offset + 3] == 'V';
    }

    private static int annexBStart(byte[] buffer, int from, int end) {
        for (int index = from; index + 3 < end; index++) {
            if (buffer[index] == 0 && buffer[index + 1] == 0 && buffer[index + 2] == 1) {
                return index;
            }
            if (buffer[index] == 0 && buffer[index + 1] == 0
                    && buffer[index + 2] == 0 && buffer[index + 3] == 1) {
                return index;
            }
        }
        return -1;
    }

    private static long unsignedIntLittleEndian(byte[] buffer, int offset) {
        return (buffer[offset] & 0xFFL)
                | (buffer[offset + 1] & 0xFFL) << 8
                | (buffer[offset + 2] & 0xFFL) << 16
                | (buffer[offset + 3] & 0xFFL) << 24;
    }
}
