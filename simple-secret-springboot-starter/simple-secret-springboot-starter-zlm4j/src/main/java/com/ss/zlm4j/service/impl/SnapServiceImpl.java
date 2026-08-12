package com.ss.zlm4j.service.impl;

import com.ss.common.toolbox.net.SafeUriFormatter;
import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import com.ss.zlm4j.exception.ZlmOperationException;
import com.ss.zlm4j.security.MediaResourcePolicy;
import com.ss.zlm4j.security.MediaResourceUsage;
import com.ss.zlm4j.service.ISnapService;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.PointerPointer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.bytedeco.ffmpeg.global.avutil.*;

/**
 * 快照服务实现
 *
 * @author JunPzx
 * @since 2025/8/20 17:02
 */
@Slf4j
@Service
public class SnapServiceImpl implements ISnapService {

    private final MediaResourcePolicy mediaResourcePolicy;
    private final ZlmMediaProperties properties;
    private final SnapCapture capture;
    private final NativeResourceReleaser resourceReleaser;

    /**
     * 创建并初始化实例。
     *
     * @param mediaResourcePolicy 媒体资源访问策略
     * @param properties 模块配置
     */
    @Autowired
    public SnapServiceImpl(MediaResourcePolicy mediaResourcePolicy, ZlmMediaProperties properties) {
        this(mediaResourcePolicy, properties, null, DefaultNativeResourceReleaser.INSTANCE);
    }

    SnapServiceImpl(MediaResourcePolicy mediaResourcePolicy) {
        this(mediaResourcePolicy, new ZlmMediaProperties(), null, DefaultNativeResourceReleaser.INSTANCE);
    }

    SnapServiceImpl(MediaResourcePolicy mediaResourcePolicy, ZlmMediaProperties properties, SnapCapture capture) {
        this(mediaResourcePolicy, properties, capture, DefaultNativeResourceReleaser.INSTANCE);
    }

    SnapServiceImpl(MediaResourcePolicy mediaResourcePolicy, ZlmMediaProperties properties,
                    SnapCapture capture, NativeResourceReleaser resourceReleaser) {
        this.mediaResourcePolicy = mediaResourcePolicy;
        this.properties = properties;
        this.capture = capture != null ? capture : this::captureNative;
        this.resourceReleaser = resourceReleaser;
    }

    @Override
    public String snapToBase64(String url) {
        url = mediaResourcePolicy.requireAllowed(url, MediaResourceUsage.SNAPSHOT).toASCIIString();
        Path imagePath = null;
        try {
            Path snapDirectory = Path.of(properties.getRootPath(), "snap").toAbsolutePath().normalize();
            Files.createDirectories(snapDirectory);
            imagePath = Files.createTempFile(snapDirectory, "snap-", ".jpg");
            capture.capture(url, imagePath, properties.getSnapTimeoutMs());
            if (!Files.isRegularFile(imagePath)) {
                throw new ZlmOperationException("截图失败");
            }
            return Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
        } catch (ZlmOperationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ZlmOperationException("截图失败", exception);
        } finally {
            if (imagePath != null) {
                try {
                    Files.deleteIfExists(imagePath);
                } catch (IOException exception) {
                    log.warn("【截图】临时文件删除失败：{}", imagePath, exception);
                }
            }
        }
    }

    private void captureNative(String url, Path output, int timeoutMs) {
        SnapshotResources resources = new SnapshotResources(output.toString());
        try {
            openSnapshotInput(resources, url, timeoutMs);
            readSnapshotKeyFrame(resources);
            configureSnapshotCodecs(resources);
            allocateSnapshotBuffers(resources);
            transcodeSnapshotFrame(resources);
            writeSnapshot(resources);
        } finally {
            try {
                if (resources.inputOptions != null) {
                    avutil.av_dict_free(resources.inputOptions);
                }
            } finally {
                free(url, resources.decoderContext, resources.encoderContext,
                        resources.inputFormat, resources.outputFormat, resources.outputIo,
                        resources.frame, resources.sourcePacket, resources.outputPacket);
            }
        }
    }

    private void openSnapshotInput(SnapshotResources resources, String url, int timeoutMs) {
        resources.inputFormat = new AVFormatContext(null);
        resources.inputOptions = new AVDictionary(null);
        long timeoutMicros = Math.multiplyExact((long) timeoutMs, 1000L);
        avutil.av_dict_set(resources.inputOptions, "rw_timeout", Long.toString(timeoutMicros), 0);
        avutil.av_dict_set(resources.inputOptions, "timeout", Long.toString(timeoutMicros), 0);
        if (url.startsWith("rtsp")) {
            avutil.av_dict_set(resources.inputOptions, "rtsp_transport", "tcp", 0);
        }
        int result = avformat.avformat_open_input(
                resources.inputFormat, url, null, resources.inputOptions);
        if (result < 0) {
            avutil.av_log(resources.inputFormat, AV_LOG_ERROR, "avformat_open_input error \n");
            throw new ZlmOperationException("截图失败");
        }
        result = avformat.avformat_find_stream_info(
                resources.inputFormat, (PointerPointer) null);
        if (result < 0) {
            avutil.av_log(resources.inputFormat, AV_LOG_ERROR, "avformat_find_stream_info error \n");
            throw new ZlmOperationException("截图失败");
        }
        resources.videoIndex = avformat.av_find_best_stream(
                resources.inputFormat, AVMEDIA_TYPE_VIDEO, -1, -1, (PointerPointer) null, 0);
        if (resources.videoIndex < 0) {
            avutil.av_log(resources.inputFormat, AV_LOG_ERROR, "av_find_best_stream error \n");
            throw new ZlmOperationException("截图失败");
        }
        resources.videoStream = resources.inputFormat.streams(resources.videoIndex);
    }

    private void readSnapshotKeyFrame(SnapshotResources resources) {
        resources.sourcePacket = avcodec.av_packet_alloc();
        if (resources.sourcePacket == null) {
            throw new ZlmOperationException("截图失败");
        }
        int result;
        while ((result = avformat.av_read_frame(
                resources.inputFormat, resources.sourcePacket)) == 0) {
            if (resources.sourcePacket.stream_index() == resources.videoIndex
                    && isKeyFrame(resources.sourcePacket.flags())) {
                return;
            }
            avcodec.av_packet_unref(resources.sourcePacket);
        }
        avutil.av_log(resources.inputFormat, AV_LOG_ERROR, "av_read_frame error \n");
        throw new ZlmOperationException("截图失败");
    }

    private void configureSnapshotCodecs(SnapshotResources resources) {
        AVCodec decoder = avcodec.avcodec_find_decoder(resources.videoStream.codecpar().codec_id());
        AVCodec encoder = avcodec.avcodec_find_encoder(avcodec.AV_CODEC_ID_MJPEG);
        if (decoder == null || encoder == null) {
            throw new ZlmOperationException("截图失败");
        }
        resources.decoderContext = avcodec.avcodec_alloc_context3(decoder);
        resources.encoderContext = avcodec.avcodec_alloc_context3(encoder);
        if (resources.decoderContext == null || resources.encoderContext == null) {
            throw new ZlmOperationException("截图失败");
        }
        int result = avcodec.avcodec_parameters_to_context(
                resources.decoderContext, resources.videoStream.codecpar());
        if (result < 0) {
            avutil.av_log(resources.decoderContext, AV_LOG_ERROR,
                    "avcodec_parameters_to_context error \n");
            throw new ZlmOperationException("截图失败");
        }
        result = avcodec.avcodec_open2(resources.decoderContext, decoder, (PointerPointer) null);
        if (result < 0) {
            avutil.av_log(resources.decoderContext, AV_LOG_ERROR, "avcodec_open2 error \n");
            throw new ZlmOperationException("截图失败");
        }
        configureSnapshotOutput(resources, encoder);
    }

    private void configureSnapshotOutput(SnapshotResources resources, AVCodec encoder) {
        resources.outputFormat = new AVFormatContext(null);
        int result = avformat.avformat_alloc_output_context2(
                resources.outputFormat, null, "mjpeg", resources.outputPath);
        if (result < 0) {
            throw new ZlmOperationException("截图失败");
        }
        resources.outputStream = avformat.avformat_new_stream(resources.outputFormat, null);
        if (resources.outputStream == null) {
            throw new ZlmOperationException("截图失败");
        }
        resources.encoderContext.width(resources.decoderContext.width());
        resources.encoderContext.height(resources.decoderContext.height());
        resources.encoderContext.time_base(avutil.av_make_q(1, 1));
        resources.encoderContext.pix_fmt(AV_PIX_FMT_YUVJ420P);
        avcodec.avcodec_parameters_from_context(
                resources.outputStream.codecpar(), resources.encoderContext);
        result = avcodec.avcodec_open2(
                resources.encoderContext, encoder, (PointerPointer) null);
        if (result < 0) {
            avutil.av_log(resources.encoderContext, AV_LOG_ERROR, "avcodec_open2 error \n");
            throw new ZlmOperationException("截图失败");
        }
    }

    private void allocateSnapshotBuffers(SnapshotResources resources) {
        resources.frame = avutil.av_frame_alloc();
        if (resources.frame == null) {
            throw new ZlmOperationException("截图失败");
        }
        resources.frame.width(resources.encoderContext.width());
        resources.frame.height(resources.encoderContext.height());
        resources.frame.format(resources.encoderContext.pix_fmt());
        int result = av_frame_get_buffer(resources.frame, 0);
        if (result < 0) {
            avutil.av_log(resources.encoderContext, AV_LOG_ERROR, "av_frame_get_buffer error \n");
            throw new ZlmOperationException("截图失败");
        }
        if (avutil.av_frame_is_writable(resources.frame) != 1) {
            avutil.av_frame_make_writable(resources.frame);
        }
        resources.outputPacket = avcodec.av_packet_alloc();
        if (resources.outputPacket == null) {
            throw new ZlmOperationException("截图失败");
        }
        resources.outputPacket.stream_index(resources.outputStream.index());
    }

    private void transcodeSnapshotFrame(SnapshotResources resources) {
        avcodec.avcodec_send_packet(resources.decoderContext, resources.sourcePacket);
        avcodec.avcodec_send_packet(resources.decoderContext, null);
        int result = avcodec.avcodec_receive_frame(resources.decoderContext, resources.frame);
        if (result < 0 && result != AVERROR_EOF() && result != AVERROR_EAGAIN()) {
            avutil.av_log(resources.decoderContext, AV_LOG_ERROR, "avcodec_receive_frame error \n");
            throw new ZlmOperationException("截图失败");
        }
        avcodec.avcodec_send_frame(resources.encoderContext, resources.frame);
        avcodec.avcodec_send_frame(resources.encoderContext, null);
        result = avcodec.avcodec_receive_packet(resources.encoderContext, resources.outputPacket);
        if (result < 0 && result != AVERROR_EOF() && result != AVERROR_EAGAIN()) {
            avutil.av_log(resources.encoderContext, AV_LOG_ERROR, "avcodec_receive_packet error \n");
            throw new ZlmOperationException("截图失败");
        }
    }

    private void writeSnapshot(SnapshotResources resources) {
        resources.outputIo = new AVIOContext(null);
        int result = avformat.avio_open(
                resources.outputIo, resources.outputPath, avformat.AVIO_FLAG_WRITE);
        if (result < 0) {
            avutil.av_log(resources.outputFormat, AV_LOG_ERROR, "avio_open error \n");
            throw new ZlmOperationException("截图失败");
        }
        resources.outputFormat.pb(resources.outputIo);
        result = avformat.avformat_write_header(
                resources.outputFormat, (PointerPointer) null);
        if (result < 0) {
            avutil.av_log(resources.outputFormat, AV_LOG_ERROR, "avformat_write_header error \n");
            throw new ZlmOperationException("截图失败");
        }
        result = avformat.av_write_frame(resources.outputFormat, resources.outputPacket);
        if (result < 0) {
            avutil.av_log(resources.outputFormat, AV_LOG_ERROR, "av_write_frame error \n");
            throw new ZlmOperationException("截图失败");
        }
        avformat.av_write_trailer(resources.outputFormat);
    }

    static boolean isKeyFrame(int flags) {
        return (flags & avcodec.AV_PKT_FLAG_KEY) != 0;
    }

    private static final class SnapshotResources {
        private final String outputPath;
        private int videoIndex;
        private AVFormatContext inputFormat;
        private AVFormatContext outputFormat;
        private AVCodecContext decoderContext;
        private AVCodecContext encoderContext;
        private AVPacket sourcePacket;
        private AVPacket outputPacket;
        private AVFrame frame;
        private AVStream videoStream;
        private AVStream outputStream;
        private AVIOContext outputIo;
        private AVDictionary inputOptions;

        private SnapshotResources(String outputPath) {
            this.outputPath = outputPath;
        }
    }


    /**
     * 释放截图流程中已分配的 native 资源。
     *
     * @param url           输入流地址，仅以脱敏形式记录
     * @param deCodecCtx    解码上下文
     * @param enCodecCtx    编码上下文
     * @param iFmtCtx       输入格式上下文
     * @param oFmtCtx       输出格式上下文
     * @param outputContext 输出 IO 上下文
     * @param frame         视频帧
     * @param srcPacket     输入数据包
     * @param packet        输出数据包
     */
    void free(String url, AVCodecContext deCodecCtx, AVCodecContext enCodecCtx,
              AVFormatContext iFmtCtx, AVFormatContext oFmtCtx, AVIOContext outputContext,
              AVFrame frame, AVPacket srcPacket, AVPacket packet) {
        log.info("【截图】释放流地址：{} 资源", SafeUriFormatter.forLog(url));
        if (packet != null) {
            resourceReleaser.freePacket(packet);
        }
        if (srcPacket != null) {
            resourceReleaser.freePacket(srcPacket);
        }
        if (frame != null) {
            resourceReleaser.freeFrame(frame);
        }
        if (enCodecCtx != null) {
            resourceReleaser.freeCodecContext(enCodecCtx);
        }
        if (deCodecCtx != null) {
            resourceReleaser.freeCodecContext(deCodecCtx);
        }
        if (iFmtCtx != null) {
            resourceReleaser.closeInput(iFmtCtx);
        }
        if (outputContext != null && !outputContext.isNull()) {
            resourceReleaser.closeOutput(outputContext);
        }
        if (oFmtCtx != null) {
            resourceReleaser.freeFormatContext(oFmtCtx);
        }
    }

    interface NativeResourceReleaser {
        void freePacket(AVPacket packet);

        void freeFrame(AVFrame frame);

        void freeCodecContext(AVCodecContext codecContext);

        void closeInput(AVFormatContext inputContext);

        void closeOutput(AVIOContext outputContext);

        void freeFormatContext(AVFormatContext formatContext);
    }

    private enum DefaultNativeResourceReleaser implements NativeResourceReleaser {
        INSTANCE;

        @Override
        public void freePacket(AVPacket packet) {
            avcodec.av_packet_free(packet);
        }

        @Override
        public void freeFrame(AVFrame frame) {
            avutil.av_frame_free(frame);
        }

        @Override
        public void freeCodecContext(AVCodecContext codecContext) {
            avcodec.avcodec_free_context(codecContext);
        }

        @Override
        public void closeInput(AVFormatContext inputContext) {
            avformat.avformat_close_input(inputContext);
        }

        @Override
        public void closeOutput(AVIOContext outputContext) {
            avformat.avio_closep(outputContext);
        }

        @Override
        public void freeFormatContext(AVFormatContext formatContext) {
            avformat.avformat_free_context(formatContext);
        }
    }

    @FunctionalInterface
    interface SnapCapture {
        void capture(String url, Path output, int timeoutMs) throws Exception;
    }
}
