package com.ss.kmz;

import com.ss.kmz.domain.KmzMission;
import com.ss.kmz.domain.MissionConfig;
import com.ss.kmz.domain.Waypoint;
import com.ss.kmz.domain.WaypointAction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 写出和读取后共用的任务完整性校验。
 */
final class MissionValidator {

    static final int MAX_WAYPOINTS = 10_000;
    static final int MAX_ACTIONS_PER_WAYPOINT = 128;

    private MissionValidator() {
    }

    static void validate(KmzMission mission) {
        if (mission == null) {
            throw new IllegalArgumentException("mission must not be null");
        }
        if (mission.getMissionName() != null && mission.getMissionName().length() > 256) {
            throw new IllegalArgumentException("mission name must not exceed 256 characters");
        }
        validateConfig(mission.getMissionConfig());
        List<Waypoint> waypoints = mission.getWaypoints();
        if (waypoints == null) {
            throw new IllegalArgumentException("waypoints must not be null");
        }
        if (waypoints.size() > MAX_WAYPOINTS) {
            throw new IllegalArgumentException("waypoint count must not exceed " + MAX_WAYPOINTS);
        }
        Set<Integer> indexes = new HashSet<>();
        for (int position = 0; position < waypoints.size(); position++) {
            validateWaypoint(waypoints.get(position), position, indexes);
        }
    }

    private static void validateConfig(MissionConfig config) {
        if (config == null) {
            return;
        }
        requireFiniteNonNegative(config.getTakeOffSecurityHeight(), "take off security height");
        requireFiniteNonNegative(config.getGlobalTransitionalSpeed(), "global transitional speed");
        requireFiniteNonNegative(config.getGlobalRTHHeight(), "global RTH height");
        if (config.getExecuteRCLostAction() != null && config.getExecuteRCLostAction() < 0) {
            throw new IllegalArgumentException("execute RC lost action must be non-negative");
        }
    }

    private static void validateWaypoint(Waypoint waypoint, int position, Set<Integer> indexes) {
        if (waypoint == null) {
            throw new IllegalArgumentException("waypoint at position " + position + " must not be null");
        }
        if (waypoint.getIndex() < 0 || !indexes.add(waypoint.getIndex())) {
            throw new IllegalArgumentException("waypoint index must be non-negative and unique: " + waypoint.getIndex());
        }
        if (waypoint.getCoordinate() == null) {
            throw new IllegalArgumentException("waypoint coordinate must not be null: " + waypoint.getIndex());
        }
        requireFinite(waypoint.getExecuteHeight(), "waypoint execute height");
        requireFiniteNonNegative(waypoint.getWaypointSpeed(), "waypoint speed");
        if (waypoint.getHeading() != null) {
            double angle = waypoint.getHeading().getHeadingAngle();
            requireFinite(angle, "waypoint heading angle");
            if (angle < -180.0 || angle > 360.0) {
                throw new IllegalArgumentException("waypoint heading angle must be within [-180, 360]");
            }
        }
        List<WaypointAction> actions = waypoint.getActions();
        if (actions == null) {
            throw new IllegalArgumentException("waypoint actions must not be null");
        }
        if (actions.size() > MAX_ACTIONS_PER_WAYPOINT) {
            throw new IllegalArgumentException("actions per waypoint must not exceed " + MAX_ACTIONS_PER_WAYPOINT);
        }
        for (WaypointAction action : actions) {
            if (action == null || action.getActionId() < 0
                    || action.getActionType() == null || action.getActionType().isBlank()) {
                throw new IllegalArgumentException("waypoint action requires non-negative id and non-blank type");
            }
        }
    }

    private static void requireFiniteNonNegative(double value, String name) {
        requireFinite(value, name);
        if (value < 0.0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
