package com.ss.application.dahuatest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 大华 SDK 播放与云台控制测试应用入口。
 *
 * @author junpzx
 * @since 2026-09-16
 */
@SpringBootApplication
public class DahuaTestApplication {

    /**
     * 启动测试应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DahuaTestApplication.class, args);
    }

}
