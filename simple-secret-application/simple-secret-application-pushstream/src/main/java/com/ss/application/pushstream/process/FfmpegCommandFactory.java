package com.ss.application.pushstream.process;

import com.ss.application.pushstream.scan.MediaFile;

import java.util.List;
import java.util.Objects;

/**
 * 创建本地媒体文件循环推送到 ZLMediaKit 的 FFmpeg 命令。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public class FfmpegCommandFactory implements FfmpegCommand {

    /** FFmpeg 可执行文件。 */
    private final String ffmpegExecutable;

    /** ZLMediaKit RTSP 监听主机。 */
    private final String rtspHost;

    /** ZLMediaKit RTSP 监听端口。 */
    private final int rtspPort;

    /** ZLMediaKit 应用名。 */
    private final String app;

    /**
     * 创建 FFmpeg 命令工厂。
     *
     * @param ffmpegExecutable FFmpeg 可执行文件
     * @param rtspHost RTSP 监听主机
     * @param rtspPort RTSP 监听端口
     * @param app ZLMediaKit 应用名
     */
    public FfmpegCommandFactory(String ffmpegExecutable, String rtspHost, int rtspPort, String app) {
        this.ffmpegExecutable = Objects.requireNonNull(ffmpegExecutable, "ffmpegExecutable");
        this.rtspHost = Objects.requireNonNull(rtspHost, "rtspHost");
        this.rtspPort = rtspPort;
        this.app = Objects.requireNonNull(app, "app");
    }

    /**
     * 创建 FFmpeg 参数列表。
     *
     * @param mediaFile 待推流媒体文件
     * @return 不经过 Shell 解析的参数列表
     */
    @Override
    public List<String> create(MediaFile mediaFile) {
        Objects.requireNonNull(mediaFile, "mediaFile");
        String targetUrl = "rtsp://" + formatRtspHost() + ':' + rtspPort
                + '/' + app + '/' + mediaFile.streamId();
        return List.of(ffmpegExecutable, "-nostdin", "-hide_banner", "-loglevel", "warning",
                "-stream_loop", "-1", "-re", "-i", mediaFile.path().toString(),
                "-c", "copy", "-f", "rtsp", "-rtsp_transport", "tcp", targetUrl);
    }

    private String formatRtspHost() {
        if (rtspHost.indexOf(':') >= 0 && !rtspHost.startsWith("[")) {
            return '[' + rtspHost + ']';
        }
        return rtspHost;
    }
}
