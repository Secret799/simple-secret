package com.ss.zlm4j.context;

import com.ss.common.toolbox.net.SafeUriFormatter;
import com.ss.zlm4j.service.domain.bo.VideoStackWindowBO;
import com.ss.zlm4j.support.SpringUtils;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVBufferRef;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.ffmpeg.global.swscale;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.*;

import static org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF;
import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR;
import static org.bytedeco.ffmpeg.presets.avutil.AVERROR_EAGAIN;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 视频拼接窗口上下文
 *
 * @author JunPzx
 * @since 2025/8/20 18:28
 */
@Slf4j
public class VideoStackWindowContext {


    /**
     * 调用参数。
     */
    private VideoStackWindowBO param;

    /**
     * {@code fillImg}地址。
     */
    private String fillImgUrl;

    /**
     * 宽度。
     */
    private Integer width;

    /**
     * 高度。
     */
    private Integer height;

    /**
     * 窗口在目标画布中的纵向字节偏移。
     */
    private Integer yPos;

    /**
     * 窗口在拼接画布中的序号。
     */
    private Integer vIndex;

    /**
     * 拼接窗口每行字节跨度。
     */
    private Integer lineWidth;

    /**
     * 目标图像各平面数据指针。
     */
    private PointerPointer<Pointer> dataPointer;

    /**
     * 目标图像各平面行跨度指针。
     */
    private IntPointer linePointer;

    /**
     * FFmpeg 输入格式上下文。
     */
    private AVFormatContext iFmtCtx = null;

    /**
     * FFmpeg 硬件设备上下文。
     */
    private AVBufferRef hwDeviceCtx = null;

    /**
     * FFmpeg 媒体流。
     */
    private AVStream avStream = null;

    /**
     * FFmpeg 视频帧。
     */
    private AVFrame avFrame = null;

    /**
     * 从硬件设备复制到本地内存的视频帧。
     */
    private AVFrame localFrame = null;

    /**
     * 转换后的 RGB 视频帧。
     */
    private AVFrame rgbFrame = null;

    /**
     * FFmpeg 编码数据包。
     */
    private AVPacket avPacket = null;

    /**
     * FFmpeg 解码器上下文。
     */
    private AVCodecContext deCodecCtx = null;

    /**
     * FFmpeg 像素格式转换上下文。
     */
    private SwsContext avSwsCtx = null;

    /**
     * 占位图片像素格式转换上下文。
     */
    private SwsContext imgSwsCtx = null;

    /**
     * 是否已经停止。
     */
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    /**
     * 窗口视频填充任务句柄。
     */
    private volatile Future<?> fillVideoFuture;


    /**
     * 创建并初始化实例。
     *
     * @param param 调用参数
     * @param fillImgUrl 默认填充图片地址
     * @param width 宽度
     * @param height 高度
     * @param yPos 窗口在画布中的纵向字节偏移
     * @param dataPointer 目标图像各平面数据指针
     * @param linePointer 图像行跨度指针
     */
    public VideoStackWindowContext(VideoStackWindowBO param, String fillImgUrl, int width, int height, int yPos, PointerPointer<Pointer> dataPointer, IntPointer linePointer) {
        this.param = param;
        this.fillImgUrl = fillImgUrl;
        this.width = width;
        this.height = height;
        this.yPos = yPos;
        this.dataPointer = dataPointer;
        this.linePointer = linePointer;
    }


    /**
     * 根据窗口配置初始化视频、图片、填充色或默认占位图。
     */
    public void init() {
        if ((param.getVideoUrl() != null && !param.getVideoUrl().isBlank())) {
            fillVideoFuture = SpringUtils.getSimpleSecretScheduledExecutor().submit(this::initFillVideo);
        } else if ((param.getImgUrl() != null && !param.getImgUrl().isBlank())) {
            initFillImg(param.getImgUrl());
        } else if ((param.getFillColor() != null && !param.getFillColor().isBlank())) {
            initFillColor(param.getFillColor());
        } else if ((fillImgUrl != null && !fillImgUrl.isBlank())) {
            initFillImg(fillImgUrl);
        }
    }

    /**
     * 填充颜色
     *
     * @param fillColor
     */
    private void initFillColor(String fillColor) {
        int[] bgr = VideoStackContext.convertRGBHex(fillColor);
        BytePointer bytePointer = new BytePointer(dataPointer.get(0).getPointer(yPos));
        long yDiff = linePointer.get(0);
        long yImgPos = 0L;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                long xImgPos = x * 3L;
                bytePointer.put(yImgPos + xImgPos, (byte) bgr[0]);
                bytePointer.put(yImgPos + xImgPos + 1, (byte) bgr[1]);
                bytePointer.put(yImgPos + xImgPos + 2, (byte) bgr[2]);
            }
            yImgPos = yImgPos + yDiff;
        }
    }


    /**
     * 填充视频
     */
    private void initFillVideo() {
        int ret = 0;
        log.debug("FFmpeg配置: {}", avutil.avutil_configuration());
        iFmtCtx = new AVFormatContext(null);
        boolean isRtsp = param.getVideoUrl().startsWith("rtsp");
        if (isRtsp) {
            AVDictionary rtspOptions = new AVDictionary(null);
            avutil.av_dict_set(rtspOptions, "rtsp_transport", "tcp", 0);
            ret = avformat.avformat_open_input(iFmtCtx, param.getVideoUrl(), null, rtspOptions);
            avutil.av_dict_free(rtspOptions);
        } else {
            ret = avformat.avformat_open_input(iFmtCtx, param.getVideoUrl(), null, null);
        }
        if (ret < 0) {
            avutil.av_log(iFmtCtx, AV_LOG_ERROR, "avformat_open_input error \n");
            free();
            return;
        }
        ret = avformat.avformat_find_stream_info(iFmtCtx, (PointerPointer) null);
        if (ret < 0) {
            avutil.av_log(iFmtCtx, AV_LOG_ERROR, "avformat_find_stream_info error \n");
            free();
            return;
        }
        vIndex = avformat.av_find_best_stream(iFmtCtx, avutil.AVMEDIA_TYPE_VIDEO, -1, -1, (PointerPointer) null, 0);
        if (vIndex < 0) {
            avutil.av_log(iFmtCtx, AV_LOG_ERROR, "av_find_best_stream error \n");
            free();
            return;
        }
        avStream = iFmtCtx.streams(vIndex);
        AVCodec deCodec = null;
        deCodec = avcodec.avcodec_find_decoder(avStream.codecpar().codec_id());
        deCodecCtx = avcodec.avcodec_alloc_context3(deCodec);
        if (deCodecCtx == null) {
            avutil.av_log(iFmtCtx, AV_LOG_ERROR, "avcodec_alloc_context3 error \n");
            free();
            return;
        }
        ret = avcodec.avcodec_parameters_to_context(deCodecCtx, avStream.codecpar());
        if (nativeCallFailed(ret)) {
            avutil.av_log(deCodecCtx, AV_LOG_ERROR, "avcodec_parameters_to_context error \n");
            free();
            return;
        }
        if (hwDeviceCtx != null) {
            deCodecCtx.hw_device_ctx(avutil.av_buffer_ref(hwDeviceCtx));
        }
        ret = avcodec.avcodec_open2(deCodecCtx, deCodec, (PointerPointer) null);
        if (ret < 0) {
            avutil.av_log(deCodecCtx, AV_LOG_ERROR, "avcodec_open2 error \n");
            free();
            return;
        }
        avPacket = avcodec.av_packet_alloc();
        avFrame = avutil.av_frame_alloc();
        if (hwDeviceCtx == null) {
            avFrame.width(deCodecCtx.width());
            avFrame.height(deCodecCtx.height());
            avFrame.format(deCodecCtx.pix_fmt());
        } else {
            localFrame = avutil.av_frame_alloc();
        }
        avutil.av_frame_get_buffer(avFrame, 1);
        rgbFrame = avutil.av_frame_alloc();
        rgbFrame.width(width);
        rgbFrame.height(height);
        rgbFrame.format(avutil.AV_PIX_FMT_BGR24);
        avutil.av_frame_get_buffer(rgbFrame, 1);
        avSwsCtx = swscale.sws_getContext(avFrame.width(), avFrame.height(), avFrame.format(), width, height, avutil.AV_PIX_FMT_BGR24, swscale.SWS_BICUBIC, null, null, (DoublePointer) null);
        if (avSwsCtx == null) {
            avutil.av_log(deCodecCtx, AV_LOG_ERROR, "sws_getContext error \n");
            free();
            return;
        }
        while (!stopped.get() && (ret = avformat.av_read_frame(iFmtCtx, avPacket)) == 0) {
            if (avPacket.stream_index() == vIndex) {
                ret = avcodec.avcodec_send_packet(deCodecCtx, avPacket);
                if (ret < 0) {
                    avutil.av_log(deCodecCtx, AV_LOG_ERROR, "avcodec_send_packet error \n");
                    free();
                    return;
                }
                while (true) {
                    if (avutil.av_frame_is_writable(avFrame) != 1) {
                        avutil.av_frame_make_writable(avFrame);
                    }
                    ret = avcodec.avcodec_receive_frame(deCodecCtx, avFrame);
                    if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF()) {
                        break;
                    }
                    if (ret < 0) {
                        avutil.av_log(deCodecCtx, AV_LOG_ERROR, "avcodec_receive_frame error \n");
                        free();
                        return;
                    }
                    if (avutil.av_frame_is_writable(rgbFrame) != 1) {
                        avutil.av_frame_make_writable(rgbFrame);
                    }
                    swscale.sws_scale(avSwsCtx, avFrame.data(), avFrame.linesize(), 0, avFrame.height(), rgbFrame.data(), rgbFrame.linesize());
                    int yRgbPos = 0;
                    int destPos = yPos;
                    int diffW = linePointer.get(0);
                    int destW = rgbFrame.linesize().get(0);
                    for (int i = 0; i < rgbFrame.height(); i++) {
                        Pointer.memcpy(dataPointer.get(0).getPointer(destPos), rgbFrame.data().get(0).getPointer(yRgbPos), rgbFrame.width() * 3L);
                        yRgbPos = yRgbPos + destW;
                        destPos = destPos + diffW;
                    }
                    avutil.av_frame_unref(avFrame);
                }
            }
            avcodec.av_packet_unref(avPacket);
        }

        free();
    }

    /**
     * 填充图像
     */
    private void initFillImg(String imgUrl) {
        VideoStackContext.ImageData imageData = VideoStackContext.getImageBGRData(imgUrl);
        if (imageData != null) {
            imgSwsCtx = swscale.sws_getContext(imageData.width, imageData.height, avutil.AV_PIX_FMT_BGR24, width, height, avutil.AV_PIX_FMT_BGR24, swscale.SWS_BICUBIC, null, null, (DoublePointer) null);
            if (imgSwsCtx != null) {
                PointerPointer<Pointer> srcDataPointerPointer = new PointerPointer<>(4);
                IntPointer srcLineSize = new IntPointer(4);
                BytePointer rgbImgPointer = new BytePointer(imageData.bgrData);
                avutil.av_image_fill_arrays(srcDataPointerPointer, srcLineSize, rgbImgPointer, avutil.AV_PIX_FMT_BGR24, imageData.width, imageData.height, 1);
                int destDataSize = avutil.av_image_get_buffer_size(avutil.AV_PIX_FMT_BGR24, width, height, 1);
                Pointer destPointer = avutil.av_mallocz(destDataSize);
                PointerPointer<Pointer> destDataPointerPointer = new PointerPointer<>(4);
                IntPointer destLineSize = new IntPointer(4);
                avutil.av_image_fill_arrays(destDataPointerPointer, destLineSize, new BytePointer(destPointer), avutil.AV_PIX_FMT_BGR24, width, height, 1);
                swscale.sws_scale(imgSwsCtx, srcDataPointerPointer, srcLineSize, 0, imageData.height, destDataPointerPointer, destLineSize);
                int yImgPos = 0;
                int diffW = linePointer.get(0);
                int destW = destLineSize.get(0);
                int destPos = yPos;
                for (int i = 0; i < height; i++) {
                    Pointer.memcpy(dataPointer.get(0).getPointer(destPos), destPointer.getPointer(yImgPos), width * 3);
                    yImgPos = yImgPos + destW;
                    destPos = destPos + diffW;
                }
                swscale.sws_freeContext(imgSwsCtx);
                avutil.av_free(destPointer);
                imgSwsCtx = null;
            }
        }
    }


    /**
     * 停止
     */
    public void stop() {
        stopped.set(true);
        Future<?> currentFuture = fillVideoFuture;
        if (currentFuture != null) {
            currentFuture.cancel(true);
        }
    }

    static boolean nativeCallFailed(int returnCode) {
        return returnCode < 0;
    }


    /**
     * 释放资源
     */
    private void free() {
        if ((param.getVideoUrl() != null && !param.getVideoUrl().isBlank())) {
            log.info("【拼接屏窗口】 释放区域：{} 资源",
                    SafeUriFormatter.forLog(param.getVideoUrl()));
        }
        if (iFmtCtx != null) {
            avformat.avformat_close_input(iFmtCtx);
            iFmtCtx = null;
        }
        if (deCodecCtx != null) {
            avcodec.avcodec_free_context(deCodecCtx);
            deCodecCtx = null;
        }
        if (avFrame != null) {
            avutil.av_frame_free(avFrame);
            avFrame = null;
        }
        if (localFrame != null) {
            avutil.av_frame_free(localFrame);
            localFrame = null;
        }
        if (hwDeviceCtx != null) {
            avutil.av_buffer_unref(hwDeviceCtx);
        }
        if (rgbFrame != null) {
            avutil.av_frame_free(rgbFrame);
            rgbFrame = null;
        }
        if (avPacket != null) {
            avcodec.av_packet_free(avPacket);
            avPacket = null;
        }
        if (avSwsCtx != null) {
            swscale.sws_freeContext(avSwsCtx);
            avSwsCtx = null;
        }
    }
}
