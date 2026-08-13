package com.ss.application.pushstream.process;

import java.io.IOException;
import java.util.List;

/**
 * 启动外部进程的可替换边界。
 *
 * @author junpzx
 * @since 2026-08-12
 */
@FunctionalInterface
public interface ProcessLauncher {

    /**
     * 启动指定参数的进程。
     *
     * @param command 进程参数列表
     * @return 已启动进程
     * @throws IOException 进程无法启动时抛出
     */
    Process launch(List<String> command) throws IOException;
}
