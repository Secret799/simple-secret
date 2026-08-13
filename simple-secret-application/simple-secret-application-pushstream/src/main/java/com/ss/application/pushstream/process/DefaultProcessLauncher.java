package com.ss.application.pushstream.process;

import java.io.IOException;
import java.util.List;

/**
 * 基于 {@link ProcessBuilder} 的默认进程启动器。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public class DefaultProcessLauncher implements ProcessLauncher {

    /**
     * 启动外部进程并丢弃输出，避免长期进程因输出缓冲区阻塞。
     *
     * @param command 进程参数列表
     * @return 已启动进程
     * @throws IOException 进程无法启动时抛出
     */
    @Override
    public Process launch(List<String> command) throws IOException {
        return new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
    }
}
