package com.ss.kmz.kml;

import com.ss.kmz.domain.Coordinate;

import java.util.List;

/**
 * 普通 KML 中一个具名 LineString。
 *
 * @param name 所属 Placemark 名称，可能为 {@code null}
 * @param coordinates 有序坐标列表
 */
public record KmlLineString(String name, List<Coordinate> coordinates) {

    /** 创建不可变 LineString。 */
    public KmlLineString {
        if (coordinates == null) {
            throw new IllegalArgumentException("coordinates must not be null");
        }
        coordinates = List.copyOf(coordinates);
    }
}
