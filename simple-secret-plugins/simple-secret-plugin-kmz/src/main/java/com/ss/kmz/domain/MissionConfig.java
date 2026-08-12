package com.ss.kmz.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 飞行任务全局配置。
 */
public class MissionConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 飞向航线起点的模式。
     */
    private String flyToWaylineMode;
    /**
     * 航线完成后的飞行器动作。
     */
    private String finishAction;
    /**
     * 是否在遥控链路丢失时退出航线。
     */
    private String exitOnRCLost;
    /**
     * 遥控链路丢失后的执行动作。
     */
    private Integer executeRCLostAction;
    /**
     * 安全起飞高度。
     */
    private double takeOffSecurityHeight;
    /**
     * 全局航线过渡速度。
     */
    private double globalTransitionalSpeed;
    /**
     * DJI WPML 飞行器类型编码。
     */
    private Integer droneType;
    /**
     * DJI WPML 负载类型编码。
     */
    private Integer payloadType;
    /**
     * 全局返航高度。
     */
    private double globalRTHHeight;

    /** 创建空配置。 */
    public MissionConfig() {
    }

    /**
     * 创建完整的任务配置。
     *
     * @param flyToWaylineMode 飞向航线起点的模式
     * @param finishAction 航线完成后的飞行器动作
     * @param exitOnRCLost 是否在遥控链路丢失时退出航线
     * @param executeRCLostAction 遥控链路丢失后的执行动作
     * @param takeOffSecurityHeight 安全起飞高度
     * @param globalTransitionalSpeed 全局航线过渡速度
     * @param droneType DJI WPML 飞行器类型编码
     * @param payloadType 负载类型
     * @param globalRTHHeight 全局返航高度
     */
    public MissionConfig(String flyToWaylineMode, String finishAction, String exitOnRCLost,
                         Integer executeRCLostAction, double takeOffSecurityHeight,
                         double globalTransitionalSpeed, Integer droneType, Integer payloadType,
                         double globalRTHHeight) {
        this.flyToWaylineMode = flyToWaylineMode;
        this.finishAction = finishAction;
        this.exitOnRCLost = exitOnRCLost;
        this.executeRCLostAction = executeRCLostAction;
        this.takeOffSecurityHeight = takeOffSecurityHeight;
        this.globalTransitionalSpeed = globalTransitionalSpeed;
        this.droneType = droneType;
        this.payloadType = payloadType;
        this.globalRTHHeight = globalRTHHeight;
    }

    /**
     * 创建构建器。
     *
     * @return 新的构建器
     */
    public static Builder builder() { return new Builder(); }

    /**
     * 返回飞向首航点模式。
     *
     * @return 飞向航线起点的模式
     */
    public String getFlyToWaylineMode() { return flyToWaylineMode; }
    /**
     * 设置飞向首航点模式。
     *
     * @param value 飞向航线起点的模式
     * @return 当前对象
     */
    public MissionConfig setFlyToWaylineMode(String value) { flyToWaylineMode = value; return this; }
    /**
     * 返回任务结束动作。
     *
     * @return 航线完成后的飞行器动作
     */
    public String getFinishAction() { return finishAction; }
    /**
     * 设置任务结束动作。
     *
     * @param value 航线完成后的飞行器动作
     * @return 当前对象
     */
    public MissionConfig setFinishAction(String value) { finishAction = value; return this; }
    /**
     * 返回遥控器失联策略。
     *
     * @return 是否在遥控链路丢失时退出航线
     */
    public String getExitOnRCLost() { return exitOnRCLost; }
    /**
     * 设置遥控器失联策略。
     *
     * @param value 是否在遥控链路丢失时退出航线
     * @return 当前对象
     */
    public MissionConfig setExitOnRCLost(String value) { exitOnRCLost = value; return this; }
    /**
     * 返回失联动作。
     *
     * @return 遥控链路丢失后的执行动作
     */
    public Integer getExecuteRCLostAction() { return executeRCLostAction; }
    /**
     * 设置失联动作。
     *
     * @param value 遥控链路丢失后的执行动作
     * @return 当前对象
     */
    public MissionConfig setExecuteRCLostAction(Integer value) { executeRCLostAction = value; return this; }
    /**
     * 返回安全起飞高度。
     *
     * @return 安全起飞高度
     */
    public double getTakeOffSecurityHeight() { return takeOffSecurityHeight; }
    /**
     * 设置安全起飞高度。
     *
     * @param value 安全起飞高度
     * @return 当前对象
     */
    public MissionConfig setTakeOffSecurityHeight(double value) { takeOffSecurityHeight = value; return this; }
    /**
     * 返回全局过渡速度。
     *
     * @return 全局航线过渡速度
     */
    public double getGlobalTransitionalSpeed() { return globalTransitionalSpeed; }
    /**
     * 设置全局过渡速度。
     *
     * @param value 全局航线过渡速度
     * @return 当前对象
     */
    public MissionConfig setGlobalTransitionalSpeed(double value) { globalTransitionalSpeed = value; return this; }
    /**
     * 返回无人机类型。
     *
     * @return DJI WPML 飞行器类型编码
     */
    public Integer getDroneType() { return droneType; }
    /**
     * 设置无人机类型。
     *
     * @param value DJI WPML 飞行器类型编码
     * @return 当前对象
     */
    public MissionConfig setDroneType(Integer value) { droneType = value; return this; }
    /**
     * 返回负载类型。
     *
     * @return 负载类型
     */
    public Integer getPayloadType() { return payloadType; }
    /**
     * 设置负载类型。
     *
     * @param value 负载类型
     * @return 当前对象
     */
    public MissionConfig setPayloadType(Integer value) { payloadType = value; return this; }
    /**
     * 返回全局返航高度。
     *
     * @return 全局返航高度
     */
    public double getGlobalRTHHeight() { return globalRTHHeight; }
    /**
     * 设置全局返航高度。
     *
     * @param value 全局返航高度
     * @return 当前对象
     */
    public MissionConfig setGlobalRTHHeight(double value) { globalRTHHeight = value; return this; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof MissionConfig that)) return false;
        return Double.compare(takeOffSecurityHeight, that.takeOffSecurityHeight) == 0
                && Double.compare(globalTransitionalSpeed, that.globalTransitionalSpeed) == 0
                && Double.compare(globalRTHHeight, that.globalRTHHeight) == 0
                && Objects.equals(flyToWaylineMode, that.flyToWaylineMode)
                && Objects.equals(finishAction, that.finishAction)
                && Objects.equals(exitOnRCLost, that.exitOnRCLost)
                && Objects.equals(executeRCLostAction, that.executeRCLostAction)
                && Objects.equals(droneType, that.droneType)
                && Objects.equals(payloadType, that.payloadType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flyToWaylineMode, finishAction, exitOnRCLost, executeRCLostAction,
                takeOffSecurityHeight, globalTransitionalSpeed, droneType, payloadType, globalRTHHeight);
    }

    @Override
    public String toString() {
        return "MissionConfig{flyToWaylineMode='" + flyToWaylineMode + "', finishAction='" + finishAction
                + "', exitOnRCLost='" + exitOnRCLost + "', executeRCLostAction=" + executeRCLostAction
                + ", takeOffSecurityHeight=" + takeOffSecurityHeight + ", globalTransitionalSpeed="
                + globalTransitionalSpeed + ", droneType=" + droneType + ", payloadType=" + payloadType
                + ", globalRTHHeight=" + globalRTHHeight + '}';
    }

    /** MissionConfig 构建器。 */
    public static final class Builder {
        private final MissionConfig value = new MissionConfig();
        private Builder() { }
        /**
         * 设置飞向首航点模式。
         *
         * @param item 飞向航线起点的模式
         * @return 当前对象
         */
        public Builder flyToWaylineMode(String item) { value.setFlyToWaylineMode(item); return this; }
        /**
         * 设置结束动作。
         *
         * @param item 航线完成后的飞行器动作
         * @return 当前对象
         */
        public Builder finishAction(String item) { value.setFinishAction(item); return this; }
        /**
         * 设置失联策略。
         *
         * @param item 是否在遥控链路丢失时退出航线
         * @return 当前对象
         */
        public Builder exitOnRCLost(String item) { value.setExitOnRCLost(item); return this; }
        /**
         * 设置失联动作。
         *
         * @param item 遥控链路丢失后的执行动作
         * @return 当前对象
         */
        public Builder executeRCLostAction(Integer item) { value.setExecuteRCLostAction(item); return this; }
        /**
         * 设置安全起飞高度。
         *
         * @param item 安全起飞高度
         * @return 当前对象
         */
        public Builder takeOffSecurityHeight(double item) { value.setTakeOffSecurityHeight(item); return this; }
        /**
         * 设置全局过渡速度。
         *
         * @param item 全局航线过渡速度
         * @return 当前对象
         */
        public Builder globalTransitionalSpeed(double item) { value.setGlobalTransitionalSpeed(item); return this; }
        /**
         * 设置无人机类型。
         *
         * @param item DJI WPML 飞行器类型编码
         * @return 当前对象
         */
        public Builder droneType(Integer item) { value.setDroneType(item); return this; }
        /**
         * 设置负载类型。
         *
         * @param item 负载类型
         * @return 当前对象
         */
        public Builder payloadType(Integer item) { value.setPayloadType(item); return this; }
        /**
         * 设置返航高度。
         *
         * @param item 全局返航高度
         * @return 当前对象
         */
        public Builder globalRTHHeight(double item) { value.setGlobalRTHHeight(item); return this; }
        /**
         * 构建独立配置快照。
         *
         * @return 构建完成的结果对象
         */
        public MissionConfig build() {
            return new MissionConfig(value.flyToWaylineMode, value.finishAction, value.exitOnRCLost,
                    value.executeRCLostAction, value.takeOffSecurityHeight, value.globalTransitionalSpeed,
                    value.droneType, value.payloadType, value.globalRTHHeight);
        }
    }
}
