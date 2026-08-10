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

    private int index;
    private Coordinate coordinate;
    private double executeHeight;
    private double waypointSpeed;
    private WaypointHeading heading;
    private List<WaypointAction> actions = new ArrayList<>();

    /** 创建空航点。 */
    public Waypoint() {
    }

    /** 创建完整航点。 */
    public Waypoint(int index, Coordinate coordinate, double executeHeight, double waypointSpeed,
                    WaypointHeading heading, List<WaypointAction> actions) {
        this.index = index;
        this.coordinate = coordinate;
        this.executeHeight = executeHeight;
        this.waypointSpeed = waypointSpeed;
        this.heading = heading;
        setActions(actions);
    }

    /** 创建构建器。 */
    public static Builder builder() { return new Builder(); }

    /** 返回航点序号。 */ public int getIndex() { return index; }
    /** 设置航点序号。 */ public Waypoint setIndex(int value) { index = value; return this; }
    /** 返回坐标。 */ public Coordinate getCoordinate() { return coordinate; }
    /** 设置坐标。 */ public Waypoint setCoordinate(Coordinate value) { coordinate = value; return this; }
    /** 返回执行高度。 */ public double getExecuteHeight() { return executeHeight; }
    /** 设置执行高度。 */ public Waypoint setExecuteHeight(double value) { executeHeight = value; return this; }
    /** 返回飞行速度。 */ public double getWaypointSpeed() { return waypointSpeed; }
    /** 设置飞行速度。 */ public Waypoint setWaypointSpeed(double value) { waypointSpeed = value; return this; }
    /** 返回航向参数。 */ public WaypointHeading getHeading() { return heading; }
    /** 设置航向参数。 */ public Waypoint setHeading(WaypointHeading value) { heading = value; return this; }
    /** 返回动作列表。 */ public List<WaypointAction> getActions() { return actions; }
    /** 设置动作列表并复制输入。 */
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
        /** 设置序号。 */ public Builder index(int item) { value.setIndex(item); return this; }
        /** 设置坐标。 */ public Builder coordinate(Coordinate item) { value.setCoordinate(item); return this; }
        /** 设置执行高度。 */ public Builder executeHeight(double item) { value.setExecuteHeight(item); return this; }
        /** 设置速度。 */ public Builder waypointSpeed(double item) { value.setWaypointSpeed(item); return this; }
        /** 设置航向。 */ public Builder heading(WaypointHeading item) { value.setHeading(item); return this; }
        /** 设置动作。 */ public Builder actions(List<WaypointAction> item) { value.setActions(item); return this; }
        /** 构建独立航点快照。 */
        public Waypoint build() {
            return new Waypoint(value.index, value.coordinate, value.executeHeight, value.waypointSpeed,
                    value.heading, value.actions);
        }
    }
}
