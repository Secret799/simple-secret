package com.ss.easymedia.h264.parser;


/**
 * H264NalUnit 处理接口
 *
 * @author junpzx
 */
public interface H264NalUnitProcessor {
    /**
     * 处理一个 H264NalUnit
     *
     * @param nalUnit H264NalUnit
     */
    void process(H264NalUnit nalUnit);
}


