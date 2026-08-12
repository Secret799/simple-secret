package com.ss.kmz.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 航点。
 */
public class Waypoint implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 索引。
     */
    private int index;
    /**
     * 地理坐标。
     */
    private Coordinate coordinate;
    /**
     * 航点执行高度，单位米。
     */
    private double executeHeight;
    /**
     * 航点飞行速度。
     */
    private double waypointSpeed;
    /**
     * 航向配置。
     */
    private WaypointHeading heading;
    /**
     * 航点动作列表。
     */
    private List<WaypointAction> actions = new ArrayList<>();

    /** 创建空航点。 */
    public Waypoint() {
    }

    /**
     * 创建完整航点。
     *
     * @param index 索引
     * @param coordinate 地理坐标
     * @param executeHeight 航点执行高度，单位米
     * @param waypointSpeed 航点飞行速度
     * @param heading 航向配置
     * @param actions 航点动作列表
     */
    public Waypoint(int index, Coordinate coordinate, double executeHeight, double waypointSpeed,
                    WaypointHeading heading, List<WaypointAction> actions) {
        this.index = index;
        this.coordinate = coordinate;
        this.executeHeight = executeHeight;
        this.waypointSpeed = waypointSpeed;
        this.heading = heading;
        setActions(actions);
    }

    /**
     * 创建构建器。
     *
     * @return 新的构建器
     */
    public static Builder builder() { return new Builder(); }

    /**
     * 返回航点序号。
     *
     * @return 索引
     */
    public int getIndex() { return index; }
    /**
     * 设置航点序号。
     *
     * @param value 索引
     * @return 当前对象
     */
    public Waypoint setIndex(int value) { index = value; return this; }
    /**
     * 返回坐标。
     *
     * @return 地理坐标
     */
    public Coordinate getCoordinate() { return coordinate; }
    /**
     * 设置坐标。
     *
     * @param value 地理坐标
     * @return 当前对象
     */
    public Waypoint setCoordinate(Coordinate value) { coordinate = value; return this; }
    /**
     * 返回执行高度。
     *
     * @return 航点执行高度，单位米
     */
    public double getExecuteHeight() { return executeHeight; }
    /**
     * 设置执行高度。
     *
     * @param value 航点执行高度，单位米
     * @return 当前对象
     */
    public Waypoint setExecuteHeight(double value) { executeHeight = value; return this; }
    /**
     * 返回飞行速度。
     *
     * @return 航点飞行速度
     */
    public double getWaypointSpeed() { return waypointSpeed; }
    /**
     * 设置飞行速度。
     *
     * @param value 航点飞行速度
     * @return 当前对象
     */
    public Waypoint setWaypointSpeed(double value) { waypointSpeed = value; return this; }
    /**
     * 返回航向参数。
     *
     * @return 航向配置
     */
    public WaypointHeading getHeading() { return heading; }
    /**
     * 设置航向参数。
     *
     * @param value 航向配置
     * @return 当前对象
     */
    public Waypoint setHeading(WaypointHeading value) { heading = value; return this; }
    /**
     * 返回动作列表。
     *
     * @return 航点动作列表
     */
    public List<WaypointAction> getActions() { return actions; }
    /**
     * 设置动作列表并复制输入。
     *
     * @param value 航点动作列表
     * @return 当前对象
     */
    public Waypoint setActions(List<WaypointAction> value) {
        actions = value == null ? new ArrayList<>() : new ArrayList<>(value);
        return this;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Waypoint that)) return false;
        return index == that.index && Double.compare(executeHeight, that.executeHeight) == 0
                && Double.compare(waypointSpeed, that.waypointSpeed) == 0
                && Objects.equals(coordinate, that.coordinate) && Objects.equals(heading, that.heading)
                && Objects.equals(actions, that.actions);
    }

    @Override
    public int hashCode() { return Objects.hash(index, coordinate, executeHeight, waypointSpeed, heading, actions); }

    @Override
    public String toString() {
        return "Waypoint{index=" + index + ", coordinate=" + coordinate + ", executeHeight=" + executeHeight
                + ", waypointSpeed=" + waypointSpeed + ", heading=" + heading + ", actions=" + actions + '}';
    }

    /** Waypoint 构建器。 */
    public static final class Builder {
        private final Waypoint value = new Waypoint();
        private Builder() { }
        /**
         * 设置序号。
         *
         * @param item 索引
         * @return 当前对象
         */
        public Builder index(int item) { value.setIndex(item); return this; }
        /**
         * 设置坐标。
         *
         * @param item 地理坐标
         * @return 当前对象
         */
        public Builder coordinate(Coordinate item) { value.setCoordinate(item); return this; }
        /**
         * 设置执行高度。
         *
         * @param item 航点执行高度，单位米
         * @return 当前对象
         */
        public Builder executeHeight(double item) { value.setExecuteHeight(item); return this; }
        /**
         * 设置速度。
         *
         * @param item 航点飞行速度
         * @return 当前对象
         */
        public Builder waypointSpeed(double item) { value.setWaypointSpeed(item); return this; }
        /**
         * 设置航向。
         *
         * @param item 航向配置
         * @return 当前对象
         */
        public Builder heading(WaypointHeading item) { value.setHeading(item); return this; }
        /**
         * 设置动作。
         *
         * @param item 航点动作列表
         * @return 当前对象
         */
        public Builder actions(List<WaypointAction> item) { value.setActions(item); return this; }
        /**
         * 构建独立航点快照。
         *
         * @return 构建完成的结果对象
         */
        public Waypoint build() {
            return new Waypoint(value.index, value.coordinate, value.executeHeight, value.waypointSpeed,
                    value.heading, value.actions);
        }
    }
}
