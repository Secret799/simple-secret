package com.ss.application.dahuatest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 关闭原生能力后的接口层测试：自检、校验和 SDK 缺失路径。
 *
 * @author junpzx
 * @since 2026-09-16
 */
@SpringBootTest(properties = {
        "simple-secret.camera-sdk.dahua.enabled=false",
        "simple-secret.zlm4j.enabled=false",
        "simple-secret.camera-zlm.enabled=false"
})
@AutoConfigureMockMvc
class DahuaTestApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void configReportsSdkAndStreamServiceNotReadyWithoutNativeEnvironment() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sdkReady").value(false))
                .andExpect(jsonPath("$.streamServiceReady").value(false));
    }

    @Test
    void ptzRejectedWhenSdkServiceMissing() throws Exception {
        mockMvc.perform(post("/api/ptz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ip":"192.0.2.10","port":"37777","username":"admin",
                                "password":"secret","channel":"1","command":"UP","isBegin":true,"speedLevel":4}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void streamStartRejectsNonPositiveChannel() throws Exception {
        mockMvc.perform(post("/api/stream/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ip":"192.0.2.10","port":"37777","username":"admin",
                                "password":"secret","channel":"0","streamType":0,"app":"live","stream":"dahua-main"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void ptzRejectsUnknownCommand() throws Exception {
        mockMvc.perform(post("/api/ptz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ip":"192.0.2.10","port":"37777","username":"admin",
                                "password":"secret","channel":"1","command":"TELEPORT","isBegin":true}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void streamStopWithoutActiveSessionIsIdempotent() throws Exception {
        mockMvc.perform(post("/api/stream/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stopped").value(false));
    }

}
