package com.ss.zlm4j.context;

import com.ss.common.toolbox.net.SafeUriFormatter;
import com.ss.zlm4j.service.domain.bo.TranscodeBO;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.AVIOInterruptCB;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.ffmpeg.global.swscale;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;

import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.bytedeco.ffmpeg.global.avformat.AVFMT_NOFILE;
import static org.bytedeco.ffmpeg.global.avutil.*;

/**
 * 转码上下文
 *
 * @author JunPzx
 * @since 2025/8/20 18:04
 */
@Slf4j
public class TranscodeContext {
    /**
     * 调用参数。
     */
    private final TranscodeBO param;

    /**
     * {@code push}地址。
     */
    private final String pushUrl;

    /**
     * 是否已经停止。
     */
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    /**
     * 创建并初始化实例。
     *
     * @param param 调用参数
     * @param pushUrl 转码输出推流地址
     */
    public TranscodeContext(TranscodeBO param, String pushUrl) {
        this.param = param;
        this.pushUrl = pushUrl;
    }

    /**
     * 开始转码
     */
    public void start() {
        TranscodeResources resources = new TranscodeResources();
        AVIOInterruptCB.Callback_Pointer interruptCallback = createInterruptCallback();
        try {
            if (!openInput(resources, interruptCallback)
                    || !createOutput(resources)
                    || !discoverStreams(resources)
                    || !configureVideoCodecs(resources)
                    || !allocateFramesAndPackets(resources)
                    || !openOutput(resources)) {
                return;
            }
            transcodePackets(resources);
        } finally {
            free(resources);
            Reference.reachabilityFence(interruptCallback);
        }
    }

    private AVIOInterruptCB.Callback_Pointer createInterruptCallback() {
        return new AVIOInterruptCB.Callback_Pointer() {
            @Override
            public int call(Pointer opaque) {
                return shouldInterruptIo() ? 1 : 0;
            }
        };
    }

    private boolean openInput(TranscodeResources resources,
                              AVIOInterruptCB.Callback_Pointer interruptCallback) {
        resources.inputFormat = new AVFormatContext(null);
        resources.inputFormat.interrupt_callback().callback(interruptCallback);
        int result;
        if (param.getUrl().startsWith("rtsp")) {
            AVDictionary rtspOptions = new AVDictionary(null);
            try {
                avutil.av_dict_set(rtspOptions, "rtsp_transport", "tcp", 0);
                result = avformat.avformat_open_input(
                        resources.inputFormat, param.getUrl(), null, rtspOptions);
            } finally {
                avutil.av_dict_free(rtspOptions);
            }
        } else {
            result = avformat.avformat_open_input(
                    resources.inputFormat, param.getUrl(), null, null);
        }
        if (result < 0) {
            avutil.av_log(resources.inputFormat, AV_LOG_ERROR, "avformat_open_input error \n");
            return false;
        }
        result = avformat.avformat_find_stream_info(
                resources.inputFormat, (PointerPointer) null);
        if (result < 0) {
            avutil.av_log(resources.inputFormat, AV_LOG_ERROR,
                    "avformat_find_stream_info error \n");
            return false;
        }
        return true;
    }

    private boolean createOutput(TranscodeResources resources) {
        resources.outputFormat = new AVFormatContext(null);
        return avformat.avformat_alloc_output_context2(
                resources.outputFormat, null, "flv", pushUrl) >= 0;
    }

    private boolean discoverStreams(TranscodeResources resources) {
        for (int index = 0; index < resources.inputFormat.nb_streams(); index++) {
            AVStream stream = resources.inputFormat.streams(index);
            if (stream.codecpar().codec_type() == AVMEDIA_TYPE_VIDEO) {
                resources.outputVideoStream = avformat.avformat_new_stream(
                        resources.outputFormat, null);
                resources.videoStream = stream;
                resources.videoIndex = index;
            } else if (stream.codecpar().codec_type() == AVMEDIA_TYPE_AUDIO
                    && Boolean.TRUE.equals(param.getEnableAudio())) {
                resources.outputAudioStream = avformat.avformat_new_stream(
                        resources.outputFormat, null);
                if (resources.outputAudioStream == null) {
                    return false;
                }
                avcodec.avcodec_parameters_copy(
                        resources.outputAudioStream.codecpar(), stream.codecpar());
                resources.audioStream = stream;
                resources.audioIndex = index;
            }
        }
        if (!hasVideoStream(resources.videoIndex)
                || resources.videoStream == null || resources.outputVideoStream == null) {
            log.error("【转码】输入流不包含视频轨：{}", SafeUriFormatter.forLog(param.getUrl()));
            return false;
        }
        return true;
    }

    private boolean configureVideoCodecs(TranscodeResources resources) {
        AVCodec decoder = avcodec.avcodec_find_decoder(
                resources.videoStream.codecpar().codec_id());
        if (decoder == null) {
            log.error("【转码】未找到输入视频解码器：{}",
                    resources.videoStream.codecpar().codec_id());
            return false;
        }
        AVCodec encoder = avcodec.avcodec_find_encoder_by_name("libx264");
        if (encoder == null) {
            log.error("【转码】未找到 libx264 编码器");
            return false;
        }
        resources.decoderContext = avcodec.avcodec_alloc_context3(decoder);
        resources.encoderContext = avcodec.avcodec_alloc_context3(encoder);
        if (resources.decoderContext == null || resources.encoderContext == null) {
            log.error("【转码】分配编解码上下文失败");
            return false;
        }
        int result = avcodec.avcodec_parameters_to_context(
                resources.decoderContext, resources.videoStream.codecpar());
        if (result < 0) {
            avutil.av_log(resources.decoderContext, AV_LOG_ERROR,
                    "avcodec_parameters_to_context error \n");
            return false;
        }
        result = avcodec.avcodec_open2(
                resources.decoderContext, decoder, (PointerPointer) null);
        if (result < 0) {
            avutil.av_log(resources.decoderContext, AV_LOG_ERROR, "avcodec_open2 error \n");
            return false;
        }
        return configureEncoder(resources, encoder);
    }

    private boolean configureEncoder(TranscodeResources resources, AVCodec encoder) {
        int scaleWidth = param.getScaleWidth() == null ? 0 : param.getScaleWidth();
        int scaleHeight = param.getScaleHeight() == null ? 0 : param.getScaleHeight();
        resources.encoderContext.width(
                scaleWidth == 0 ? resources.decoderContext.width() : scaleWidth);
        resources.encoderContext.height(
                scaleHeight == 0 ? resources.decoderContext.height() : scaleHeight);
        resources.scaleRequired = scaleWidth != 0 || scaleHeight != 0;
        resources.encoderContext.gop_size(avutil.av_q2intfloat(
                resources.videoStream.avg_frame_rate()));
        resources.encoderContext.has_b_frames(0);
        resources.encoderContext.max_b_frames(0);
        resources.encoderContext.profile(AVCodecContext.FF_PROFILE_H264_BASELINE);
        resources.encoderContext.framerate(resources.videoStream.avg_frame_rate());
        resources.encoderContext.time_base(avutil.av_make_q(
                resources.videoStream.avg_frame_rate().den(),
                resources.videoStream.avg_frame_rate().num()));
        resources.encoderContext.pix_fmt(AV_PIX_FMT_YUV420P);
        int result = avcodec.avcodec_open2(
                resources.encoderContext, encoder, (PointerPointer) null);
        if (result < 0) {
            avutil.av_log(resources.encoderContext, AV_LOG_ERROR, "avcodec_open2 error \n");
            return false;
        }
        result = avcodec.avcodec_parameters_from_context(
                resources.outputVideoStream.codecpar(), resources.encoderContext);
        if (result < 0) {
            avutil.av_log(resources.encoderContext, AV_LOG_ERROR,
                    "avcodec_parameters_from_context error \n");
            return false;
        }
        resources.outputVideoStream.codecpar().codec_tag(0);
        resources.outputVideoStream.time_base(resources.encoderContext.time_base());
        return configureScaler(resources);
    }

    private boolean configureScaler(TranscodeResources resources) {
        if (!resources.scaleRequired) {
            return true;
        }
        resources.scaleContext = swscale.sws_getContext(
                resources.decoderContext.width(), resources.decoderContext.height(),
                resources.decoderContext.pix_fmt(), resources.encoderContext.width(),
                resources.encoderContext.height(), resources.encoderContext.pix_fmt(),
                swscale.SWS_BICUBIC, null, null, (DoublePointer) null);
        if (resources.scaleContext == null) {
            avutil.av_log(resources.scaleContext, AV_LOG_ERROR, "sws_getContext error \n");
            return false;
        }
        return true;
    }

    private boolean allocateFramesAndPackets(TranscodeResources resources) {
        resources.frame = avutil.av_frame_alloc();
        if (resources.frame == null) {
            return false;
        }
        resources.frame.width(resources.decoderContext.width());
        resources.frame.height(resources.decoderContext.height());
        resources.frame.format(resources.decoderContext.pix_fmt());
        if (av_frame_get_buffer(resources.frame, 0) < 0) {
            avutil.av_log(resources.decoderContext, AV_LOG_ERROR,
                    "av_frame_get_buffer error \n");
            return false;
        }
        if (avutil.av_frame_is_writable(resources.frame) != 1) {
            avutil.av_frame_make_writable(resources.frame);
        }
        if (resources.scaleRequired && !allocateScaledFrame(resources)) {
            return false;
        }
        resources.outputPacket = avcodec.av_packet_alloc();
        resources.sourcePacket = avcodec.av_packet_alloc();
        if (resources.outputPacket == null || resources.sourcePacket == null) {
            return false;
        }
        resources.outputPacket.stream_index(resources.outputVideoStream.index());
        return true;
    }

    private boolean allocateScaledFrame(TranscodeResources resources) {
        resources.scaledFrame = avutil.av_frame_alloc();
        if (resources.scaledFrame == null) {
            return false;
        }
        resources.scaledFrame.width(resources.encoderContext.width());
        resources.scaledFrame.height(resources.encoderContext.height());
        resources.scaledFrame.format(resources.encoderContext.pix_fmt());
        if (av_frame_get_buffer(resources.scaledFrame, 0) < 0) {
            avutil.av_log(resources.scaleContext, AV_LOG_ERROR,
                    "av_frame_get_buffer error \n");
            return false;
        }
        if (avutil.av_frame_is_writable(resources.scaledFrame) != 1) {
            avutil.av_frame_make_writable(resources.scaledFrame);
        }
        return true;
    }

    private boolean openOutput(TranscodeResources resources) {
        AVIOContext outputIo = new AVIOContext((Pointer) null);
        int result = avformat.avio_open2(outputIo, pushUrl, avformat.AVIO_FLAG_WRITE,
                resources.inputFormat.interrupt_callback(), (AVDictionary) null);
        if (result < 0) {
            avutil.av_log(resources.outputFormat, AV_LOG_ERROR, "avio_open error \n");
            return false;
        }
        resources.outputFormat.pb(outputIo);
        result = avformat.avformat_write_header(
                resources.outputFormat, (PointerPointer) null);
        if (result < 0) {
            avutil.av_log(resources.outputFormat, AV_LOG_ERROR,
                    "avformat_write_header error \n");
            return false;
        }
        return true;
    }

    private void transcodePackets(TranscodeResources resources) {
        int result = 0;
        while (!stopped.get() && (result = avformat.av_read_frame(
                resources.inputFormat, resources.sourcePacket)) == 0) {
            try {
                if (resources.sourcePacket.stream_index() == resources.videoIndex) {
                    if (!transcodeVideoPacket(resources)) {
                        return;
                    }
                } else if (resources.sourcePacket.stream_index() == resources.audioIndex
                        && Boolean.TRUE.equals(param.getEnableAudio())
                        && !writeAudioPacket(resources)) {
                    return;
                }
            } finally {
                avcodec.av_packet_unref(resources.sourcePacket);
            }
        }
        if (result < 0 && result != AVERROR_EOF() && !shouldInterruptIo()) {
            avutil.av_log(resources.inputFormat, AV_LOG_ERROR, "av_read_frame error \n");
            return;
        }
        avformat.av_write_trailer(resources.outputFormat);
    }

    private boolean transcodeVideoPacket(TranscodeResources resources) {
        int result = avcodec.avcodec_send_packet(
                resources.decoderContext, resources.sourcePacket);
        if (result < 0) {
            avutil.av_log(resources.decoderContext, AV_LOG_ERROR,
                    "avcodec_send_packet error \n");
            return false;
        }
        while (true) {
            result = avcodec.avcodec_receive_frame(
                    resources.decoderContext, resources.frame);
            if (result == AVERROR_EAGAIN() || result == AVERROR_EOF()) {
                return true;
            }
            if (result < 0) {
                avutil.av_log(resources.decoderContext, AV_LOG_ERROR,
                        "avcodec_receive_frame error \n");
                return false;
            }
            AVFrame outputFrame = prepareOutputFrame(resources);
            if (!writeEncodedFrame(resources, outputFrame)) {
                return false;
            }
        }
    }

    private AVFrame prepareOutputFrame(TranscodeResources resources) {
        if (!resources.scaleRequired) {
            return resources.frame;
        }
        swscale.sws_scale(resources.scaleContext, resources.frame.data(),
                resources.frame.linesize(), 0, resources.frame.height(),
                resources.scaledFrame.data(), resources.scaledFrame.linesize());
        resources.scaledFrame.pts(resources.frame.pts());
        resources.scaledFrame.pkt_dts(resources.frame.pkt_dts());
        resources.scaledFrame.duration(resources.frame.duration());
        return resources.scaledFrame;
    }

    private boolean writeEncodedFrame(TranscodeResources resources, AVFrame frameToWrite) {
        int result = avcodec.avcodec_send_frame(resources.encoderContext, frameToWrite);
        if (result < 0) {
            avutil.av_log(resources.encoderContext, AV_LOG_ERROR,
                    "avcodec_send_frame error \n");
            return false;
        }
        try {
            while (true) {
                result = avcodec.avcodec_receive_packet(
                        resources.encoderContext, resources.outputPacket);
                if (result == AVERROR_EAGAIN() || result == AVERROR_EOF()) {
                    return true;
                }
                if (result < 0) {
                    avutil.av_log(resources.encoderContext, AV_LOG_ERROR,
                            "avcodec_receive_packet error \n");
                    return false;
                }
                avcodec.av_packet_rescale_ts(resources.outputPacket,
                        resources.videoStream.time_base(),
                        resources.outputVideoStream.time_base());
                resources.outputPacket.dts(resources.outputPacket.pts());
                if (avformat.av_interleaved_write_frame(
                        resources.outputFormat, resources.outputPacket) < 0) {
                    avutil.av_log(resources.outputFormat, AV_LOG_ERROR,
                            "av_interleaved_write_frame error \n");
                    return false;
                }
                avcodec.av_packet_unref(resources.outputPacket);
            }
        } finally {
            avutil.av_frame_unref(resources.frame);
        }
    }

    private boolean writeAudioPacket(TranscodeResources resources) {
        avcodec.av_packet_rescale_ts(resources.sourcePacket,
                resources.audioStream.time_base(), resources.outputAudioStream.time_base());
        resources.sourcePacket.stream_index(resources.outputAudioStream.index());
        int result = avformat.av_interleaved_write_frame(
                resources.outputFormat, resources.sourcePacket);
        if (result < 0) {
            avutil.av_log(resources.outputFormat, AV_LOG_ERROR,
                    "av_interleaved_write_frame error \n");
            return false;
        }
        return true;
    }

    /**
     * 停止
     */
    public void stop() {
        stopped.set(true);
    }

    static boolean hasVideoStream(int videoIndex) {
        return videoIndex >= 0;
    }

    boolean shouldInterruptIo() {
        return stopped.get() || Thread.currentThread().isInterrupted();
    }

    private static final class TranscodeResources {
        private int videoIndex = -1;
        private int audioIndex = -1;
        private AVFormatContext outputFormat;
        private AVFormatContext inputFormat;
        private AVCodecContext decoderContext;
        private AVCodecContext encoderContext;
        private SwsContext scaleContext;
        private AVPacket sourcePacket;
        private AVPacket outputPacket;
        private AVFrame frame;
        private AVFrame scaledFrame;
        private AVStream videoStream;
        private AVStream audioStream;
        private AVStream outputVideoStream;
        private AVStream outputAudioStream;
        private boolean scaleRequired;
    }

    /**
     * 释放资源
     */
    private void free(TranscodeResources resources) {
        log.info("【转码】释放流：{} 资源", param.getStream());
        if (resources.decoderContext != null) {
            avcodec.avcodec_free_context(resources.decoderContext);
        }
        if (resources.encoderContext != null) {
            avcodec.avcodec_free_context(resources.encoderContext);
        }
        if (resources.inputFormat != null) {
            avformat.avformat_close_input(resources.inputFormat);
        }
        AVFormatContext outputFormat = resources.outputFormat;
        if (outputFormat != null && !outputFormat.isNull()
                && (outputFormat.flags() & AVFMT_NOFILE) == 0) {
            AVIOContext output = outputFormat.pb();
            if (output != null && !output.isNull()) {
                avformat.avio_closep(output);
            }
        }
        if (outputFormat != null) {
            avformat.avformat_free_context(outputFormat);
        }
        if (resources.scaleContext != null) {
            swscale.sws_freeContext(resources.scaleContext);
        }
        if (resources.frame != null) {
            avutil.av_frame_free(resources.frame);
        }
        if (resources.scaledFrame != null) {
            avutil.av_frame_free(resources.scaledFrame);
        }
        if (resources.sourcePacket != null) {
            avcodec.av_packet_free(resources.sourcePacket);
        }
        if (resources.outputPacket != null) {
            avcodec.av_packet_free(resources.outputPacket);
        }
    }
}
