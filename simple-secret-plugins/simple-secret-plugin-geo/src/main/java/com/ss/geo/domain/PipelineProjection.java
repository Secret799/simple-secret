package com.ss.geo.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 管线中心线和缓冲区的像素投影结果。
 */
public class PipelineProjection implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 名称。
     */
    private String name;
    /**
     * 管线中心线地理坐标。
     */
    private List<PipelineProjectionPoint> centerline = new ArrayList<>();
    /**
     * 管线缓冲区地理多边形。
     */
    private List<PipelineProjectionPoint> area = new ArrayList<>();

    /**
     * 返回管线名称。
     *
     * @return 名称
     */
    public String getName() { return name; }
    /**
     * 设置管线名称。
     *
     * @param name 名称
     * @return 当前对象
     */
    public PipelineProjection setName(String name) { this.name = name; return this; }
    /**
     * 返回中心线投影点。
     *
     * @return 管线中心线地理坐标
     */
    public List<PipelineProjectionPoint> getCenterline() { return centerline; }
    /**
     * 使用输入集合副本设置中心线。
     *
     * @param points 坐标点集合
     * @return 当前对象
     */
    public PipelineProjection setCenterline(List<PipelineProjectionPoint> points) {
        this.centerline = points == null ? new ArrayList<>() : new ArrayList<>(points);
        return this;
    }
    /**
     * 返回缓冲区多边形投影点。
     *
     * @return 管线缓冲区地理多边形
     */
    public List<PipelineProjectionPoint> getArea() { return area; }
    /**
     * 使用输入集合副本设置缓冲区。
     *
     * @param points 坐标点集合
     * @return 当前对象
     */
    public PipelineProjection setArea(List<PipelineProjectionPoint> points) {
        this.area = points == null ? new ArrayList<>() : new ArrayList<>(points);
        return this;
    }
}
