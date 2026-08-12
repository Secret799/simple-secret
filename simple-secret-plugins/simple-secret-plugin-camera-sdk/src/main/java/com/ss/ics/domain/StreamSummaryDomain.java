package com.ss.ics.domain;

import java.io.Serial;
import java.io.Serializable;

/** 可选媒体适配层使用的目标流摘要。 */
public class StreamSummaryDomain implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 媒体应用名。
     */
    private String app;
    /**
     * 媒体流标识。
     */
    private String stream;
    /**
     * 数据结构定义。
     */
    private String schema;
    /**
     * 等待推流上线的时间，单位毫秒。
     */
    private Long pushStreamWaitTime;

    /** @return 媒体应用名 */
    public String getApp() {
        return app;
    }

    /**
     * @param app 媒体应用名
     *
     * @return 当前对象
     */
    public StreamSummaryDomain setApp(String app) {
        this.app = app;
        return this;
    }

    /** @return 媒体流名 */
    public String getStream() {
        return stream;
    }

    /**
     * @param stream 媒体流名
     *
     * @return 当前对象
     */
    public StreamSummaryDomain setStream(String stream) {
        this.stream = stream;
        return this;
    }

    /** @return 目标协议 */
    public String getSchema() {
        return schema;
    }

    /**
     * @param schema 目标协议
     *
     * @return 当前对象
     */
    public StreamSummaryDomain setSchema(String schema) {
        this.schema = schema;
        return this;
    }

    /** @return 等待推流注册的毫秒数 */
    public Long getPushStreamWaitTime() {
        return pushStreamWaitTime;
    }

    /**
     * @param pushStreamWaitTime 等待推流注册的毫秒数
     *
     * @return 当前对象
     */
    public StreamSummaryDomain setPushStreamWaitTime(Long pushStreamWaitTime) {
        this.pushStreamWaitTime = pushStreamWaitTime;
        return this;
    }
}
