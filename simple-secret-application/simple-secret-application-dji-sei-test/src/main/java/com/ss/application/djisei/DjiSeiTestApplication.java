package com.ss.application.djisei;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DJI RTMP SEI 诊断应用启动入口。
 *
 * @author junpzx
 * @since 2026-08-13
 */
@SpringBootApplication
public class DjiSeiTestApplication {

    /**
     * 启动诊断应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DjiSeiTestApplication.class, args);
    }
}
