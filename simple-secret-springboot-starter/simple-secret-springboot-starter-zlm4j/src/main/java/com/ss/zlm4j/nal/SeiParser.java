package com.ss.zlm4j.nal;

import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * H.264/H.265 SEI NAL 单元解析器
 * <p>
 * 从编码帧数据中提取 SEI (Supplemental Enhancement Information) 消息内容
 *
 * @author JunPzx
 * @since 2026/5/6
 */
@Slf4j
public final class SeiParser {

    /** H.264 SEI NAL 类型 */
    private static final int H264_NAL_SEI = 6;
    /** H.265 PREFIX SEI NAL 类型 */
    private static final int H265_NAL_PREFIX_SEI = 39;
    /** H.265 SUFFIX SEI NAL 类型 */
    private static final int H265_NAL_SUFFIX_SEI = 40;

    /** SEI payload 类型：用户自定义未注册 */
    public static final int USER_DATA_UNREGISTERED = 5;

    /** 常见的 NAL 起始码 */
    private static final byte[] START_CODE_4 = {0x00, 0x00, 0x00, 0x01};
    private static final byte[] START_CODE_3 = {0x00, 0x00, 0x01};

    private SeiParser() {
    }

    /**
     * 从 H.264 帧数据中解析 SEI 消息
     *
     * @param data            帧数据（含起始码的 Annex B 格式）
     * @param dataPrefixSize  数据前缀大小（起始码之前的字节数）
     * @return SEI 消息列表
     */
    public static List<SeiMessage> parseH264(byte[] data, long dataPrefixSize) {
        return parse(data, (int) dataPrefixSize, false);
    }

    /**
     * 从 H.265 帧数据中解析 SEI 消息
     *
     * @param data           帧数据（含起始码的 Annex B 格式）
     * @param dataPrefixSize 数据前缀大小
     * @return SEI 消息列表
     */
    public static List<SeiMessage> parseH265(byte[] data, long dataPrefixSize) {
        return parse(data, (int) dataPrefixSize, true);
    }

    /**
     * 通用解析：自动根据 NAL type 判断是否 SEI
     */
    private static List<SeiMessage> parse(byte[] data, int offset, boolean isH265) {
        List<SeiMessage> messages = new ArrayList<>();
        int pos = offset;
        int len = data.length;

        while (pos < len - 4) {
            // 查找起始码
            int startCodeLen = 0;
            if (matchStartCode(data, pos, START_CODE_4)) {
                startCodeLen = 4;
            } else if (matchStartCode(data, pos, START_CODE_3)) {
                startCodeLen = 3;
            }

            if (startCodeLen == 0) {
                pos++;
                continue;
            }

            int nalStart = pos + startCodeLen;
            if (nalStart >= len) {
                break;
            }

            // 查找下一个起始码作为 NAL 单元边界
            int nalEnd = findNextStartCode(data, nalStart);

            // 解析 NAL 单元
            if (isH265) {
                parseH265Nal(data, nalStart, nalEnd, messages);
            } else {
                parseH264Nal(data, nalStart, nalEnd, messages);
            }

            pos = nalEnd >= 0 ? nalEnd : len;
        }

        return messages;
    }

    private static void parseH264Nal(byte[] data, int start, int end, List<SeiMessage> messages) {
        if (end < start + 1) {
            return;
        }
        int nalHeader = data[start] & 0xFF;
        int nalType = nalHeader & 0x1F;
        if (nalType == H264_NAL_SEI) {
            parseSeiPayload(data, start + 1, end, messages);
        }
    }

    private static void parseH265Nal(byte[] data, int start, int end, List<SeiMessage> messages) {
        if (end < start + 2) {
            return;
        }
        int nalHeader = ((data[start] & 0xFF) << 8) | (data[start + 1] & 0xFF);
        int nalType = (nalHeader >> 9) & 0x3F;
        if (nalType == H265_NAL_PREFIX_SEI || nalType == H265_NAL_SUFFIX_SEI) {
            parseSeiPayload(data, start + 2, end, messages);
        }
    }

    /**
     * 解析 SEI payload — 可能包含多条 SEI message
     */
    private static void parseSeiPayload(byte[] data, int start, int end, List<SeiMessage> messages) {
        int pos = start;
        while (pos < end && pos < data.length) {
            // 读取 payloadType (变长)
            int payloadType = 0;
            while (pos < end && (data[pos] & 0xFF) == 0xFF) {
                payloadType += 255;
                pos++;
            }
            if (pos >= end) {
                break;
            }
            payloadType += data[pos] & 0xFF;
            pos++;

            // 读取 payloadSize (变长)
            int payloadSize = 0;
            while (pos < end && (data[pos] & 0xFF) == 0xFF) {
                payloadSize += 255;
                pos++;
            }
            if (pos >= end) {
                break;
            }
            payloadSize += data[pos] & 0xFF;
            pos++;

            if (pos + payloadSize > end) {
                break;
            }

            byte[] payload = new byte[payloadSize];
            System.arraycopy(data, pos, payload, 0, payloadSize);
            messages.add(new SeiMessage(payloadType, payload));
            pos += payloadSize;
        }
    }

    private static boolean matchStartCode(byte[] data, int pos, byte[] prefix) {
        if (pos + prefix.length > data.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[pos + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int findNextStartCode(byte[] data, int from) {
        for (int i = from; i < data.length - 3; i++) {
            if (data[i] == 0x00 && data[i + 1] == 0x00) {
                if (data[i + 2] == 0x01) {
                    return i;
                }
                if (i + 3 < data.length && data[i + 2] == 0x00 && data[i + 3] == 0x01) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * SEI 消息体
     */
    public static class SeiMessage {
        /** SEI payload 类型 */
        private final int payloadType;
        /** SEI payload 原始数据 */
        private final byte[] payload;

        public SeiMessage(int payloadType, byte[] payload) {
            this.payloadType = payloadType;
            this.payload = payload;
        }

        public int getPayloadType() {
            return payloadType;
        }

        public byte[] getPayload() {
            return payload;
        }

        /**
         * 以 UTF-8 字符串形式获取 payload 内容
         */
        public String getPayloadAsString() {
            return new String(payload, StandardCharsets.UTF_8);
        }

        /**
         * 以十六进制形式获取 payload 内容
         */
        public String getPayloadAsHex() {
            StringBuilder sb = new StringBuilder(payload.length * 2);
            for (byte b : payload) {
                sb.append(String.format("%02x", b & 0xFF));
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            if (payloadType == USER_DATA_UNREGISTERED && payload.length > 16) {
                // UUID (16 bytes) + user data
                StringBuilder uuid = new StringBuilder(36);
                for (int i = 0; i < 16; i++) {
                    uuid.append(String.format("%02x", payload[i] & 0xFF));
                    if (i == 3 || i == 5 || i == 7 || i == 9) {
                        uuid.append('-');
                    }
                }
                byte[] userData = new byte[payload.length - 16];
                System.arraycopy(payload, 16, userData, 0, userData.length);

                return String.format("SEI type=%d UUID=%s data=\"%s\"",
                        payloadType, uuid, new String(userData, StandardCharsets.UTF_8).trim());
            }
            return String.format("SEI type=%d size=%d hex=%s",
                    payloadType, payload.length, getPayloadAsHex());
        }
    }
}
