package com.ss.geo.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 由 WGS84 坐标点组成的地理管线。
 */
public class GeoPipeline implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    private List<GeoCoordinate> points = new ArrayList<>();

    /** 返回管线名称。 */
    public String getName() { return name; }
    /** 设置管线名称。 */
    public GeoPipeline setName(String name) { this.name = name; return this; }
    /** 返回可修改的管线点集合。 */
    public List<GeoCoordinate> getPoints() { return points; }
    /** 使用输入集合的副本设置管线点。 */
    public GeoPipeline setPoints(List<GeoCoordinate> points) {
        this.points = points == null ? new ArrayList<>() : new ArrayList<>(points);
        return this;
    }
}
