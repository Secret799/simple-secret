package com.ss.kmz;

import com.ss.kmz.constants.DjiNamespaces;
import com.ss.kmz.domain.Coordinate;
import com.ss.kmz.domain.KmzMission;
import com.ss.kmz.domain.MissionConfig;
import com.ss.kmz.domain.Waypoint;
import com.ss.kmz.domain.WaypointAction;
import com.ss.kmz.domain.WaypointHeading;
import com.ss.kmz.exception.KmzException;
import com.ss.kmz.internal.XmlSupport;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 安全、流式读取 KML 或 DJI WPML 航点任务。
 */
public final class KmlReader {

    /** 默认允许的 KML/WPML 字节数。 */
    public static final int DEFAULT_MAX_BYTES = 16 * 1024 * 1024;

    private KmlReader() {
    }

    /** 从字符串解析任务。 */
    public static KmzMission parse(String kml) {
        if (kml == null) {
            throw new IllegalArgumentException("KML string must not be null");
        }
        byte[] bytes = kml.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > DEFAULT_MAX_BYTES) {
            throw new KmzException("KML exceeds maximum size of " + DEFAULT_MAX_BYTES + " bytes");
        }
        return parseBytes(bytes);
    }

    /** 从输入流解析任务，输入流不会被关闭。 */
    public static KmzMission parse(InputStream inputStream) {
        return parse(inputStream, DEFAULT_MAX_BYTES);
    }

    /** 从输入流解析任务并限制最大字节数，输入流不会被关闭。 */
    public static KmzMission parse(InputStream inputStream, int maxBytes) {
        return parseBytes(XmlSupport.readLimited(inputStream, maxBytes, "KML"));
    }

    private static KmzMission parseBytes(byte[] bytes) {
        XMLEventReader reader = null;
        try {
            reader = XmlSupport.newSecureInputFactory().createXMLEventReader(new ByteArrayInputStream(bytes));
            while (reader.hasNext()) {
                XMLEvent event = reader.nextEvent();
                if (event.getEventType() == XMLStreamConstants.DTD) {
                    throw new KmzException("KML must not contain a DTD declaration");
                }
                if (event.isStartElement() && isKml(event.asStartElement(), DjiNamespaces.KML)) {
                    KmzMission mission = parseRoot(reader);
                    MissionValidator.validate(mission);
                    return mission;
                }
            }
            throw new KmzException("KML document does not contain an official kml root element");
        } catch (XMLStreamException | IllegalArgumentException exception) {
            if (exception instanceof KmzException kmzException) {
                throw kmzException;
            }
            throw new KmzException("解析 KML 失败: " + exception.getMessage(), exception);
        } finally {
            close(reader);
        }
    }

    private static KmzMission parseRoot(XMLEventReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement()) {
                StartElement start = event.asStartElement();
                if (isKml(start, DjiNamespaces.DOCUMENT)) {
                    return parseDocument(reader);
                }
                skipElement(reader);
            } else if (event.isEndElement() && isKml(event.asEndElement().getName(), DjiNamespaces.KML)) {
                break;
            }
        }
        throw new KmzException("KML document does not contain Document");
    }

    private static KmzMission parseDocument(XMLEventReader reader) throws XMLStreamException {
        KmzMission mission = new KmzMission();
        List<Waypoint> waypoints = new ArrayList<>();
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement()) {
                StartElement start = event.asStartElement();
                if (isKml(start, DjiNamespaces.NAME)) {
                    mission.setMissionName(text(reader, DjiNamespaces.NAME));
                } else if (isWpml(start, DjiNamespaces.MISSION_CONFIG)) {
                    mission.setMissionConfig(parseMissionConfig(reader));
                } else if (isKml(start, DjiNamespaces.FOLDER)) {
                    parseFolder(reader, waypoints);
                } else if (isKml(start, DjiNamespaces.PLACEMARK)) {
                    addWaypoint(waypoints, parseWaypoint(reader));
                } else {
                    skipElement(reader);
                }
            } else if (event.isEndElement() && isKml(event.asEndElement().getName(), DjiNamespaces.DOCUMENT)) {
                return mission.setWaypoints(waypoints);
            }
        }
        throw new KmzException("KML Document is not closed");
    }

    private static MissionConfig parseMissionConfig(XMLEventReader reader) throws XMLStreamException {
        MissionConfig config = new MissionConfig();
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement()) {
                StartElement start = event.asStartElement();
                String name = start.getName().getLocalPart();
                if (!DjiNamespaces.WPML_NAMESPACE.equals(start.getName().getNamespaceURI())) {
                    skipElement(reader);
                    continue;
                }
                switch (name) {
                    case DjiNamespaces.FLY_TO_WAYLINE_MODE -> config.setFlyToWaylineMode(text(reader, name));
                    case DjiNamespaces.FINISH_ACTION -> config.setFinishAction(text(reader, name));
                    case DjiNamespaces.EXIT_ON_RC_LOST -> config.setExitOnRCLost(text(reader, name));
                    case DjiNamespaces.EXECUTE_RC_LOST_ACTION -> config.setExecuteRCLostAction(integer(reader, name));
                    case DjiNamespaces.TAKE_OFF_SECURITY_HEIGHT -> config.setTakeOffSecurityHeight(number(reader, name));
                    case DjiNamespaces.GLOBAL_TRANSITIONAL_SPEED -> config.setGlobalTransitionalSpeed(number(reader, name));
                    case DjiNamespaces.DRONE_TYPE -> config.setDroneType(integer(reader, name));
                    case DjiNamespaces.PAYLOAD_TYPE -> config.setPayloadType(integer(reader, name));
                    case DjiNamespaces.GLOBAL_RTH_HEIGHT -> config.setGlobalRTHHeight(number(reader, name));
                    default -> skipElement(reader);
                }
            } else if (event.isEndElement() && isWpml(event.asEndElement().getName(), DjiNamespaces.MISSION_CONFIG)) {
                return config;
            }
        }
        throw new KmzException("wpml:missionConfig is not closed");
    }

    private static void parseFolder(XMLEventReader reader, List<Waypoint> waypoints) throws XMLStreamException {
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement()) {
                StartElement start = event.asStartElement();
                if (isKml(start, DjiNamespaces.PLACEMARK)) {
                    addWaypoint(waypoints, parseWaypoint(reader));
                } else if (isKml(start, DjiNamespaces.FOLDER)) {
                    parseFolder(reader, waypoints);
                } else {
                    skipElement(reader);
                }
            } else if (event.isEndElement() && isKml(event.asEndElement().getName(), DjiNamespaces.FOLDER)) {
                return;
            }
        }
        throw new KmzException("KML Folder is not closed");
    }

    private static Waypoint parseWaypoint(XMLEventReader reader) throws XMLStreamException {
        Waypoint waypoint = new Waypoint();
        List<WaypointAction> actions = new ArrayList<>();
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement()) {
                StartElement start = event.asStartElement();
                String name = start.getName().getLocalPart();
                if (isKml(start, DjiNamespaces.POINT)) {
                    waypoint.setCoordinate(parsePoint(reader));
                } else if (isWpml(start, DjiNamespaces.INDEX)) {
                    waypoint.setIndex(integer(reader, name));
                } else if (isWpml(start, DjiNamespaces.EXECUTE_HEIGHT)) {
                    waypoint.setExecuteHeight(number(reader, name));
                } else if (isWpml(start, DjiNamespaces.WAYPOINT_SPEED)) {
                    waypoint.setWaypointSpeed(number(reader, name));
                } else if (isWpml(start, DjiNamespaces.WAYPOINT_HEADING_PARAM)) {
                    waypoint.setHeading(parseHeading(reader));
                } else if (isWpml(start, DjiNamespaces.ACTION_GROUP)) {
                    parseActionGroup(reader, actions);
                } else {
                    skipElement(reader);
                }
            } else if (event.isEndElement() && isKml(event.asEndElement().getName(), DjiNamespaces.PLACEMARK)) {
                return waypoint.setActions(actions);
            }
        }
        throw new KmzException("KML Placemark is not closed");
    }

    private static Coordinate parsePoint(XMLEventReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement()) {
                if (isKml(event.asStartElement(), DjiNamespaces.COORDINATES)) {
                    return Coordinate.fromKml(text(reader, DjiNamespaces.COORDINATES));
                }
                skipElement(reader);
            } else if (event.isEndElement() && isKml(event.asEndElement().getName(), DjiNamespaces.POINT)) {
                return null;
            }
        }
        throw new KmzException("KML Point is not closed");
    }

    private static WaypointHeading parseHeading(XMLEventReader reader) throws XMLStreamException {
        WaypointHeading heading = new WaypointHeading();
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement()) {
                StartElement start = event.asStartElement();
                if (isWpml(start, DjiNamespaces.WAYPOINT_HEADING_ANGLE)) {
                    heading.setHeadingAngle(number(reader, DjiNamespaces.WAYPOINT_HEADING_ANGLE));
                } else {
                    skipElement(reader);
                }
            } else if (event.isEndElement()
                    && isWpml(event.asEndElement().getName(), DjiNamespaces.WAYPOINT_HEADING_PARAM)) {
                return heading;
            }
        }
        throw new KmzException("wpml:waypointHeadingParam is not closed");
    }

    private static void parseActionGroup(XMLEventReader reader, List<WaypointAction> actions)
            throws XMLStreamException {
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement()) {
                if (isWpml(event.asStartElement(), DjiNamespaces.ACTION)) {
                    if (actions.size() >= MissionValidator.MAX_ACTIONS_PER_WAYPOINT) {
                        throw new KmzException("actions per waypoint exceed " + MissionValidator.MAX_ACTIONS_PER_WAYPOINT);
                    }
                    actions.add(parseAction(reader));
                } else {
                    skipElement(reader);
                }
            } else if (event.isEndElement() && isWpml(event.asEndElement().getName(), DjiNamespaces.ACTION_GROUP)) {
                return;
            }
        }
        throw new KmzException("wpml:actionGroup is not closed");
    }

    private static WaypointAction parseAction(XMLEventReader reader) throws XMLStreamException {
        WaypointAction action = new WaypointAction();
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement()) {
                StartElement start = event.asStartElement();
                String name = start.getName().getLocalPart();
                if (isWpml(start, DjiNamespaces.ACTION_ID)) {
                    action.setActionId(integer(reader, name));
                } else if (isWpml(start, DjiNamespaces.ACTION_TYPE)
                        || isWpml(start, DjiNamespaces.ACTION_ACTUATOR_FUNC)) {
                    action.setActionType(text(reader, name));
                } else if (isWpml(start, DjiNamespaces.ACTION_PARAM)) {
                    action.setActionParam(text(reader, name));
                } else {
                    skipElement(reader);
                }
            } else if (event.isEndElement() && isWpml(event.asEndElement().getName(), DjiNamespaces.ACTION)) {
                return action;
            }
        }
        throw new KmzException("wpml:action is not closed");
    }

    private static void addWaypoint(List<Waypoint> waypoints, Waypoint waypoint) {
        if (waypoints.size() >= MissionValidator.MAX_WAYPOINTS) {
            throw new KmzException("waypoint count exceeds " + MissionValidator.MAX_WAYPOINTS);
        }
        waypoints.add(waypoint);
    }

    private static double number(XMLEventReader reader, String element) throws XMLStreamException {
        String value = text(reader, element);
        try {
            double number = Double.parseDouble(value);
            if (!Double.isFinite(number)) {
                throw new NumberFormatException("not finite");
            }
            return number;
        } catch (NumberFormatException exception) {
            throw new KmzException("invalid number in " + element + ": " + value, exception);
        }
    }

    private static int integer(XMLEventReader reader, String element) throws XMLStreamException {
        String value = text(reader, element);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new KmzException("invalid integer in " + element + ": " + value, exception);
        }
    }

    private static String text(XMLEventReader reader, String element) throws XMLStreamException {
        try {
            return reader.getElementText().trim();
        } catch (XMLStreamException exception) {
            throw new KmzException("invalid text content in " + element, exception);
        }
    }

    private static void skipElement(XMLEventReader reader) throws XMLStreamException {
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement()) depth++;
            if (event.isEndElement()) depth--;
        }
    }

    private static boolean isKml(StartElement element, String localName) {
        return isKml(element.getName(), localName);
    }

    private static boolean isKml(QName name, String localName) {
        String namespace = name.getNamespaceURI();
        return localName.equals(name.getLocalPart())
                && (DjiNamespaces.KML_NAMESPACE.equals(namespace) || namespace == null || namespace.isEmpty());
    }

    private static boolean isWpml(StartElement element, String localName) {
        return isWpml(element.getName(), localName);
    }

    private static boolean isWpml(QName name, String localName) {
        return localName.equals(name.getLocalPart())
                && DjiNamespaces.WPML_NAMESPACE.equals(name.getNamespaceURI());
    }

    private static void close(XMLEventReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (XMLStreamException ignored) {
                // 已完成解析或正在处理原始解析异常，无需覆盖主异常。
            }
        }
    }
}
