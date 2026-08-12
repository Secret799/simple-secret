package com.ss.kmz.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 航点航向控制参数。
 */
public class WaypointHeading implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 航向角。
     */
    private double headingAngle;

    /** 创建零度航向。 */
    public WaypointHeading() {
    }

    /**
     * 创建指定角度的航向。
     *
     * @param headingAngle 航向角
     */
    public WaypointHeading(double headingAngle) {
        this.headingAngle = headingAngle;
    }

    /**
     * 返回航向角。
     *
     * @return 航向角
     */
    public double getHeadingAngle() { return headingAngle; }
    /**
     * 设置航向角。
     *
     * @param headingAngle 航向角
     * @return 当前对象
     */
    public WaypointHeading setHeadingAngle(double headingAngle) { this.headingAngle = headingAngle; return this; }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof WaypointHeading that
                && Double.compare(headingAngle, that.headingAngle) == 0;
    }

    @Override
    public int hashCode() { return Objects.hash(headingAngle); }

    @Override
    public String toString() { return "WaypointHeading{headingAngle=" + headingAngle + '}'; }
}
