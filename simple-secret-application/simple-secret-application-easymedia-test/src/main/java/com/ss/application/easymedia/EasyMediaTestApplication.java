package com.ss.application.easymedia;

import com.ss.application.djisei.config.DjiSeiConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Simple Secret EasyMedia 测试应用启动入口。
 */
@SpringBootApplication
@Import(DjiSeiConfiguration.class)
public class EasyMediaTestApplication {
    /**
     * 启动测试应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(EasyMediaTestApplication.class, args);
    }
}
