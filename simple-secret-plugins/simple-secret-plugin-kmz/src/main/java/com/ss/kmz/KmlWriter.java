package com.ss.kmz;

import com.ss.kmz.constants.DjiNamespaces;
import com.ss.kmz.domain.KmzMission;
import com.ss.kmz.domain.MissionConfig;
import com.ss.kmz.domain.Waypoint;
import com.ss.kmz.domain.WaypointAction;
import com.ss.kmz.exception.KmzException;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;

/**
 * 将航点任务安全序列化为 KML/WPML XML。
 */
public final class KmlWriter {

    private KmlWriter() {
    }

    /**
     * 将任务序列化为 UTF-8 KML 字符串。
     *
     * @param mission KMZ 航点任务
     * @return 返回的 {@code String} 结果
     * @throws IllegalArgumentException 任务数据不满足约束时抛出
     * @throws KmzException KML 序列化失败时抛出
     */
    public static String writeToString(KmzMission mission) {
        MissionValidator.validate(mission);
        try {
            StringWriter output = new StringWriter();
            XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            write(writer, mission);
            writer.flush();
            writer.close();
            return output.toString();
        } catch (XMLStreamException exception) {
            throw new KmzException("写入 KML 失败", exception);
        }
    }

    private static void write(XMLStreamWriter writer, KmzMission mission) throws XMLStreamException {
        writer.setDefaultNamespace(DjiNamespaces.KML_NAMESPACE);
        writer.setPrefix(DjiNamespaces.WPML_PREFIX, DjiNamespaces.WPML_NAMESPACE);
        writer.writeStartDocument("UTF-8", "1.0");
        writer.writeStartElement("", DjiNamespaces.KML, DjiNamespaces.KML_NAMESPACE);
        writer.writeDefaultNamespace(DjiNamespaces.KML_NAMESPACE);
        writer.writeNamespace(DjiNamespaces.WPML_PREFIX, DjiNamespaces.WPML_NAMESPACE);
        startKml(writer, DjiNamespaces.DOCUMENT);
        if (mission.getMissionName() != null) {
            kmlText(writer, DjiNamespaces.NAME, mission.getMissionName());
        }
        if (mission.getMissionConfig() != null) {
            writeConfig(writer, mission.getMissionConfig());
        }
        if (!mission.getWaypoints().isEmpty()) {
            startKml(writer, DjiNamespaces.FOLDER);
            for (Waypoint waypoint : mission.getWaypoints()) {
                writeWaypoint(writer, waypoint);
            }
            writer.writeEndElement();
        }
        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndDocument();
    }

    private static void writeConfig(XMLStreamWriter writer, MissionConfig config) throws XMLStreamException {
        startWpml(writer, DjiNamespaces.MISSION_CONFIG);
        optionalWpml(writer, DjiNamespaces.FLY_TO_WAYLINE_MODE, config.getFlyToWaylineMode());
        optionalWpml(writer, DjiNamespaces.FINISH_ACTION, config.getFinishAction());
        optionalWpml(writer, DjiNamespaces.EXIT_ON_RC_LOST, config.getExitOnRCLost());
        if (config.getExecuteRCLostAction() != null) {
            wpmlText(writer, DjiNamespaces.EXECUTE_RC_LOST_ACTION,
                    Integer.toString(config.getExecuteRCLostAction()));
        }
        wpmlNumber(writer, DjiNamespaces.TAKE_OFF_SECURITY_HEIGHT, config.getTakeOffSecurityHeight());
        wpmlNumber(writer, DjiNamespaces.GLOBAL_TRANSITIONAL_SPEED, config.getGlobalTransitionalSpeed());
        if (config.getDroneType() != null) {
            wpmlText(writer, DjiNamespaces.DRONE_TYPE, Integer.toString(config.getDroneType()));
        }
        if (config.getPayloadType() != null) {
            wpmlText(writer, DjiNamespaces.PAYLOAD_TYPE, Integer.toString(config.getPayloadType()));
        }
        wpmlNumber(writer, DjiNamespaces.GLOBAL_RTH_HEIGHT, config.getGlobalRTHHeight());
        writer.writeEndElement();
    }

    private static void writeWaypoint(XMLStreamWriter writer, Waypoint waypoint) throws XMLStreamException {
        startKml(writer, DjiNamespaces.PLACEMARK);
        startKml(writer, DjiNamespaces.POINT);
        kmlText(writer, DjiNamespaces.COORDINATES, waypoint.getCoordinate().toKml());
        writer.writeEndElement();
        wpmlText(writer, DjiNamespaces.INDEX, Integer.toString(waypoint.getIndex()));
        wpmlNumber(writer, DjiNamespaces.EXECUTE_HEIGHT, waypoint.getExecuteHeight());
        wpmlNumber(writer, DjiNamespaces.WAYPOINT_SPEED, waypoint.getWaypointSpeed());
        if (waypoint.getHeading() != null) {
            startWpml(writer, DjiNamespaces.WAYPOINT_HEADING_PARAM);
            wpmlNumber(writer, DjiNamespaces.WAYPOINT_HEADING_ANGLE,
                    waypoint.getHeading().getHeadingAngle());
            writer.writeEndElement();
        }
        if (!waypoint.getActions().isEmpty()) {
            startWpml(writer, DjiNamespaces.ACTION_GROUP);
            for (WaypointAction action : waypoint.getActions()) {
                startWpml(writer, DjiNamespaces.ACTION);
                wpmlText(writer, DjiNamespaces.ACTION_ID, Integer.toString(action.getActionId()));
                wpmlText(writer, DjiNamespaces.ACTION_TYPE, action.getActionType());
                optionalWpml(writer, DjiNamespaces.ACTION_PARAM, action.getActionParam());
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    private static void startKml(XMLStreamWriter writer, String localName) throws XMLStreamException {
        writer.writeStartElement("", localName, DjiNamespaces.KML_NAMESPACE);
    }

    private static void startWpml(XMLStreamWriter writer, String localName) throws XMLStreamException {
        writer.writeStartElement(DjiNamespaces.WPML_PREFIX, localName, DjiNamespaces.WPML_NAMESPACE);
    }

    private static void kmlText(XMLStreamWriter writer, String localName, String value) throws XMLStreamException {
        startKml(writer, localName);
        writer.writeCharacters(value);
        writer.writeEndElement();
    }

    private static void wpmlText(XMLStreamWriter writer, String localName, String value) throws XMLStreamException {
        startWpml(writer, localName);
        writer.writeCharacters(value);
        writer.writeEndElement();
    }

    private static void wpmlNumber(XMLStreamWriter writer, String localName, double value) throws XMLStreamException {
        wpmlText(writer, localName, Double.toString(value));
    }

    private static void optionalWpml(XMLStreamWriter writer, String localName, String value) throws XMLStreamException {
        if (value != null) {
            wpmlText(writer, localName, value);
        }
    }
}
