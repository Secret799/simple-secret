package com.ss.application.pushstream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Simple Secret 本地媒体文件推流应用启动入口。
 *
 * @author junpzx
 * @since 2026-08-12
 */
@SpringBootApplication
public class PublishStreamApplication {

    /**
     * 启动推流应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(PublishStreamApplication.class, args);
    }
}
