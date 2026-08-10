package com.ss.kmz.constants;

/**
 * KML 与 DJI WPML 命名空间及元素名。
 */
public final class DjiNamespaces {

    /** OGC KML 2.2 命名空间。 */
    public static final String KML_NAMESPACE = "http://www.opengis.net/kml/2.2";
    /** DJI WPML 1.0.3 命名空间。 */
    public static final String WPML_NAMESPACE = "http://www.dji.com/wpmz/1.0.3";
    /** DJI WPML 前缀。 */
    public static final String WPML_PREFIX = "wpml";

    public static final String KML = "kml";
    public static final String DOCUMENT = "Document";
    public static final String NAME = "name";
    public static final String FOLDER = "Folder";
    public static final String PLACEMARK = "Placemark";
    public static final String POINT = "Point";
    public static final String LINE_STRING = "LineString";
    public static final String COORDINATES = "coordinates";
    public static final String MISSION_CONFIG = "missionConfig";
    public static final String FLY_TO_WAYLINE_MODE = "flyToWaylineMode";
    public static final String FINISH_ACTION = "finishAction";
    public static final String EXIT_ON_RC_LOST = "exitOnRCLost";
    public static final String EXECUTE_RC_LOST_ACTION = "executeRCLostAction";
    public static final String TAKE_OFF_SECURITY_HEIGHT = "takeOffSecurityHeight";
    public static final String GLOBAL_TRANSITIONAL_SPEED = "globalTransitionalSpeed";
    public static final String DRONE_TYPE = "droneType";
    public static final String PAYLOAD_TYPE = "payloadType";
    public static final String GLOBAL_RTH_HEIGHT = "globalRTHHeight";
    public static final String INDEX = "index";
    public static final String EXECUTE_HEIGHT = "executeHeight";
    public static final String WAYPOINT_SPEED = "waypointSpeed";
    public static final String WAYPOINT_HEADING_PARAM = "waypointHeadingParam";
    public static final String WAYPOINT_HEADING_ANGLE = "waypointHeadingAngle";
    public static final String ACTION_GROUP = "actionGroup";
    public static final String ACTION = "action";
    public static final String ACTION_ID = "actionId";
    public static final String ACTION_TYPE = "actionType";
    public static final String ACTION_ACTUATOR_FUNC = "actionActuatorFunc";
    public static final String ACTION_PARAM = "actionParam";

    private DjiNamespaces() {
    }
}
