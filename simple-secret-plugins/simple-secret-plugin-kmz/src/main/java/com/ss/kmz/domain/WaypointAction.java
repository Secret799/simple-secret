package com.ss.kmz.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 航点动作。
 */
public class WaypointAction implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 航点动作编号。
     */
    private int actionId;
    /**
     * 航点动作类型。
     */
    private String actionType;
    /**
     * 航点动作参数。
     */
    private String actionParam;

    /** 创建空动作。 */
    public WaypointAction() {
    }

    /**
     * 创建完整动作。
     *
     * @param actionId 航点动作编号
     * @param actionType 航点动作类型
     * @param actionParam 航点动作参数
     */
    public WaypointAction(int actionId, String actionType, String actionParam) {
        this.actionId = actionId;
        this.actionType = actionType;
        this.actionParam = actionParam;
    }

    /**
     * 返回动作 ID。
     *
     * @return 航点动作编号
     */
    public int getActionId() { return actionId; }
    /**
     * 设置动作 ID。
     *
     * @param actionId 航点动作编号
     * @return 当前对象
     */
    public WaypointAction setActionId(int actionId) { this.actionId = actionId; return this; }
    /**
     * 返回动作类型。
     *
     * @return 航点动作类型
     */
    public String getActionType() { return actionType; }
    /**
     * 设置动作类型。
     *
     * @param actionType 航点动作类型
     * @return 当前对象
     */
    public WaypointAction setActionType(String actionType) { this.actionType = actionType; return this; }
    /**
     * 返回动作参数。
     *
     * @return 航点动作参数
     */
    public String getActionParam() { return actionParam; }
    /**
     * 设置动作参数。
     *
     * @param actionParam 航点动作参数
     * @return 当前对象
     */
    public WaypointAction setActionParam(String actionParam) { this.actionParam = actionParam; return this; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof WaypointAction that)) return false;
        return actionId == that.actionId
                && Objects.equals(actionType, that.actionType)
                && Objects.equals(actionParam, that.actionParam);
    }

    @Override
    public int hashCode() { return Objects.hash(actionId, actionType, actionParam); }

    @Override
    public String toString() {
        return "WaypointAction{actionId=" + actionId + ", actionType='" + actionType
                + "', actionParam='" + actionParam + "'}";
    }
}
