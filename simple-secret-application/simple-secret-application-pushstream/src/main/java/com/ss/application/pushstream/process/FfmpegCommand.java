package com.ss.application.pushstream.process;

import com.ss.application.pushstream.scan.MediaFile;

import java.util.List;

/**
 * 为媒体文件创建 FFmpeg 参数列表的函数接口。
 *
 * @author junpzx
 * @since 2026-08-12
 */
@FunctionalInterface
public interface FfmpegCommand {

    /**
     * 创建不经过 Shell 解释的进程参数列表。
     *
     * @param mediaFile 待推流媒体文件
     * @return FFmpeg 参数列表
     */
    List<String> create(MediaFile mediaFile);
}
