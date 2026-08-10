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

    private String flyToWaylineMode;
    private String finishAction;
    private String exitOnRCLost;
    private Integer executeRCLostAction;
    private double takeOffSecurityHeight;
    private double globalTransitionalSpeed;
    private Integer droneType;
    private Integer payloadType;
    private double globalRTHHeight;

    /** 创建空配置。 */
    public MissionConfig() {
    }

    /** 创建完整的任务配置。 */
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

    /** 创建构建器。 */
    public static Builder builder() { return new Builder(); }

    /** 返回飞向首航点模式。 */
    public String getFlyToWaylineMode() { return flyToWaylineMode; }
    /** 设置飞向首航点模式。 */
    public MissionConfig setFlyToWaylineMode(String value) { flyToWaylineMode = value; return this; }
    /** 返回任务结束动作。 */
    public String getFinishAction() { return finishAction; }
    /** 设置任务结束动作。 */
    public MissionConfig setFinishAction(String value) { finishAction = value; return this; }
    /** 返回遥控器失联策略。 */
    public String getExitOnRCLost() { return exitOnRCLost; }
    /** 设置遥控器失联策略。 */
    public MissionConfig setExitOnRCLost(String value) { exitOnRCLost = value; return this; }
    /** 返回失联动作。 */
    public Integer getExecuteRCLostAction() { return executeRCLostAction; }
    /** 设置失联动作。 */
    public MissionConfig setExecuteRCLostAction(Integer value) { executeRCLostAction = value; return this; }
    /** 返回安全起飞高度。 */
    public double getTakeOffSecurityHeight() { return takeOffSecurityHeight; }
    /** 设置安全起飞高度。 */
    public MissionConfig setTakeOffSecurityHeight(double value) { takeOffSecurityHeight = value; return this; }
    /** 返回全局过渡速度。 */
    public double getGlobalTransitionalSpeed() { return globalTransitionalSpeed; }
    /** 设置全局过渡速度。 */
    public MissionConfig setGlobalTransitionalSpeed(double value) { globalTransitionalSpeed = value; return this; }
    /** 返回无人机类型。 */
    public Integer getDroneType() { return droneType; }
    /** 设置无人机类型。 */
    public MissionConfig setDroneType(Integer value) { droneType = value; return this; }
    /** 返回负载类型。 */
    public Integer getPayloadType() { return payloadType; }
    /** 设置负载类型。 */
    public MissionConfig setPayloadType(Integer value) { payloadType = value; return this; }
    /** 返回全局返航高度。 */
    public double getGlobalRTHHeight() { return globalRTHHeight; }
    /** 设置全局返航高度。 */
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
        /** 设置飞向首航点模式。 */ public Builder flyToWaylineMode(String item) { value.setFlyToWaylineMode(item); return this; }
        /** 设置结束动作。 */ public Builder finishAction(String item) { value.setFinishAction(item); return this; }
        /** 设置失联策略。 */ public Builder exitOnRCLost(String item) { value.setExitOnRCLost(item); return this; }
        /** 设置失联动作。 */ public Builder executeRCLostAction(Integer item) { value.setExecuteRCLostAction(item); return this; }
        /** 设置安全起飞高度。 */ public Builder takeOffSecurityHeight(double item) { value.setTakeOffSecurityHeight(item); return this; }
        /** 设置全局过渡速度。 */ public Builder globalTransitionalSpeed(double item) { value.setGlobalTransitionalSpeed(item); return this; }
        /** 设置无人机类型。 */ public Builder droneType(Integer item) { value.setDroneType(item); return this; }
        /** 设置负载类型。 */ public Builder payloadType(Integer item) { value.setPayloadType(item); return this; }
        /** 设置返航高度。 */ public Builder globalRTHHeight(double item) { value.setGlobalRTHHeight(item); return this; }
        /** 构建独立配置快照。 */
        public MissionConfig build() {
            return new MissionConfig(value.flyToWaylineMode, value.finishAction, value.exitOnRCLost,
                    value.executeRCLostAction, value.takeOffSecurityHeight, value.globalTransitionalSpeed,
                    value.droneType, value.payloadType, value.globalRTHHeight);
        }
    }
}
