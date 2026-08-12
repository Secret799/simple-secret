package com.ss.kmz.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * KMZ 飞行任务顶层容器。
 */
public class KmzMission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务名称。
     */
    private String missionName;
    /**
     * 任务全局配置。
     */
    private MissionConfig missionConfig;
    /**
     * 航点列表。
     */
    private List<Waypoint> waypoints = new ArrayList<>();

    /** 创建空任务。 */
    public KmzMission() {
    }

    /**
     * 创建完整任务。
     *
     * @param missionName 任务名称
     * @param missionConfig 任务全局配置
     * @param waypoints 航点列表
     */
    public KmzMission(String missionName, MissionConfig missionConfig, List<Waypoint> waypoints) {
        this.missionName = missionName;
        this.missionConfig = missionConfig;
        setWaypoints(waypoints);
    }

    /**
     * 创建构建器。
     *
     * @return 新的构建器
     */
    public static Builder builder() { return new Builder(); }

    /**
     * 返回任务名。
     *
     * @return 任务名称
     */
    public String getMissionName() { return missionName; }
    /**
     * 设置任务名。
     *
     * @param value 任务名称
     * @return 当前对象
     */
    public KmzMission setMissionName(String value) { missionName = value; return this; }
    /**
     * 返回全局配置。
     *
     * @return 任务全局配置
     */
    public MissionConfig getMissionConfig() { return missionConfig; }
    /**
     * 设置全局配置。
     *
     * @param value 任务全局配置
     * @return 当前对象
     */
    public KmzMission setMissionConfig(MissionConfig value) { missionConfig = value; return this; }
    /**
     * 返回航点列表。
     *
     * @return 航点列表
     */
    public List<Waypoint> getWaypoints() { return waypoints; }
    /**
     * 设置航点列表并复制输入。
     *
     * @param value 航点列表
     * @return 当前对象
     */
    public KmzMission setWaypoints(List<Waypoint> value) {
        waypoints = value == null ? new ArrayList<>() : new ArrayList<>(value);
        return this;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof KmzMission that)) return false;
        return Objects.equals(missionName, that.missionName)
                && Objects.equals(missionConfig, that.missionConfig)
                && Objects.equals(waypoints, that.waypoints);
    }

    @Override
    public int hashCode() { return Objects.hash(missionName, missionConfig, waypoints); }

    @Override
    public String toString() {
        return "KmzMission{missionName='" + missionName + "', missionConfig=" + missionConfig
                + ", waypoints=" + waypoints + '}';
    }

    /** KmzMission 构建器。 */
    public static final class Builder {
        private final KmzMission value = new KmzMission();
        private Builder() { }
        /**
         * 设置任务名。
         *
         * @param item 任务名称
         * @return 当前对象
         */
        public Builder missionName(String item) { value.setMissionName(item); return this; }
        /**
         * 设置全局配置。
         *
         * @param item 任务全局配置
         * @return 当前对象
         */
        public Builder missionConfig(MissionConfig item) { value.setMissionConfig(item); return this; }
        /**
         * 设置航点。
         *
         * @param item 航点列表
         * @return 当前对象
         */
        public Builder waypoints(List<Waypoint> item) { value.setWaypoints(item); return this; }
        /**
         * 构建独立任务快照。
         *
         * @return 构建完成的结果对象
         */
        public KmzMission build() {
            return new KmzMission(value.missionName, value.missionConfig, value.waypoints);
        }
    }
}
