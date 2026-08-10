package com.ss.zlm4j.context;

import com.ss.zlm4j.handler.*;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * zlm全局回调处理器上下文
 *
 * @author JunPzx
 * @since 2025/8/21 16:15
 */
@Data
@Accessors(chain = true)
public class ZlmCallbackHandlerContext {
    /**
     * 停止rtsp/rtmp/http-flv会话后流量汇报事件处理器
     */
    private FlowReportHandler flowReportHandler;
    /**
     * 在http文件服务器中,收到http访问文件或目录的广播,通过该事件控制访问http目录的权限
     */
    private HttpAccessHandler httpAccessHandler;
    /**
     * 在http文件服务器中,收到http访问文件或目录前的广播,通过该事件可以控制http url到文件路径的映射
     */
    private HttpBeforeAccessHandler httpBeforeAccessHandler;
    /**
     * http请求回调处理器
     */
    private HttpRequestHandler httpRequestHandler;
    /**
     * 录制mp4分片文件成功后广播
     */
    private RecordMp4Handler recordMp4Handler;
    /**
     * 录制ts分片文件成功后广播
     */
    private RecordTsHandler recordTsHandler;
    /**
     * 注册或反注册MediaSource事件处理
     */
    private StreamChangeHandler streamChangeHandler;
    /**
     * Zlm 流未找到作用域处理器（可用于未找到流后按需拉流）
     */
    private StreamNoFoundHandler streamNoFoundHandler;
    /**
     * Zlm 无人观看作用域处理器
     */
    private StreamNoReaderHandler streamNoReaderHandler;
    /**
     * 播放回调 作用域处理器
     */
    private StreamPlayHandler streamPlayHandler;
    /**
     * 推流回调 作用域处理器
     */
    private StreamPublishHandler streamPublishHandler;
}
