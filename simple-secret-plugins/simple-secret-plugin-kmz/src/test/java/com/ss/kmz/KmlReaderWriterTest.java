package com.ss.kmz;

import com.ss.kmz.domain.Coordinate;
import com.ss.kmz.domain.KmzMission;
import com.ss.kmz.domain.MissionConfig;
import com.ss.kmz.domain.Waypoint;
import com.ss.kmz.domain.WaypointAction;
import com.ss.kmz.domain.WaypointHeading;
import com.ss.kmz.exception.KmzException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmlReaderWriterTest {

    @Test
    void shouldRoundTripAllMigratedHoneybeeFields() {
        KmzMission mission = sampleMission();

        String kml = KmlWriter.writeToString(mission);
        KmzMission parsed = KmlReader.parse(kml);

        assertThat(kml).contains("<name>巡检任务</name>");
        assertThat(kml).contains("<wpml:executeRCLostAction>1</wpml:executeRCLostAction>");
        assertThat(parsed).isEqualTo(mission);
    }

    @Test
    void inputStreamShouldRemainOpen() {
        TrackingInputStream input = new TrackingInputStream(
                KmlWriter.writeToString(sampleMission()).getBytes(StandardCharsets.UTF_8));

        KmlReader.parse(input);

        assertThat(input.closed).isFalse();
    }

    @Test
    void shouldRejectDtdAndExternalEntities() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE kml [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Document><name>&xxe;</name></Document>
                </kml>
                """;

        assertThatThrownBy(() -> KmlReader.parse(xxe))
                .isInstanceOf(KmzException.class)
                .hasMessageContaining("KML");
    }

    @Test
    void shouldRejectInputBeyondConfiguredLimit() {
        byte[] bytes = "<kml/>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> KmlReader.parse(new ByteArrayInputStream(bytes), 4))
                .isInstanceOf(KmzException.class)
                .hasMessageContaining("4");
    }

    @Test
    void shouldRejectInvalidNumbersInsteadOfFallingBackToZero() {
        String kml = """
                <kml xmlns="http://www.opengis.net/kml/2.2"
                     xmlns:wpml="http://www.dji.com/wpmz/1.0.3">
                  <Document>
                    <wpml:missionConfig>
                      <wpml:globalRTHHeight>not-a-number</wpml:globalRTHHeight>
                    </wpml:missionConfig>
                  </Document>
                </kml>
                """;

        assertThatThrownBy(() -> KmlReader.parse(kml))
                .isInstanceOf(KmzException.class)
                .hasMessageContaining("globalRTHHeight");
    }

    @Test
    void writerShouldRejectInvalidMissionBeforeProducingXml() {
        KmzMission mission = sampleMission();
        mission.getWaypoints().get(0).setWaypointSpeed(Double.NaN);

        assertThatThrownBy(() -> KmlWriter.writeToString(mission))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("waypoint speed");
    }

    @Test
    void shouldReadDjiActionActuatorFunctionWithoutDependingOnNestedParamModel() {
        String kml = """
                <kml xmlns="http://www.opengis.net/kml/2.2"
                     xmlns:wpml="http://www.dji.com/wpmz/1.0.3">
                  <Document><Folder><Placemark>
                    <Point><coordinates>116.4,39.9,80</coordinates></Point>
                    <wpml:index>0</wpml:index>
                    <wpml:executeHeight>100</wpml:executeHeight>
                    <wpml:waypointSpeed>8</wpml:waypointSpeed>
                    <wpml:actionGroup><wpml:action>
                      <wpml:actionId>3</wpml:actionId>
                      <wpml:actionActuatorFunc>takePhoto</wpml:actionActuatorFunc>
                      <wpml:actionActuatorFuncParam>
                        <wpml:payloadPositionIndex>0</wpml:payloadPositionIndex>
                      </wpml:actionActuatorFuncParam>
                    </wpml:action></wpml:actionGroup>
                  </Placemark></Folder></Document>
                </kml>
                """;

        WaypointAction action = KmlReader.parse(kml)
                .getWaypoints().get(0).getActions().get(0);

        assertThat(action.getActionId()).isEqualTo(3);
        assertThat(action.getActionType()).isEqualTo("takePhoto");
        assertThat(action.getActionParam()).isNull();
    }

    private static KmzMission sampleMission() {
        MissionConfig config = MissionConfig.builder()
                .flyToWaylineMode("safely")
                .finishAction("goHome")
                .exitOnRCLost("executeLostAction")
                .executeRCLostAction(1)
                .takeOffSecurityHeight(80)
                .globalTransitionalSpeed(10)
                .droneType(89)
                .payloadType(67)
                .globalRTHHeight(100)
                .build();
        Waypoint waypoint = Waypoint.builder()
                .index(0)
                .coordinate(new Coordinate(116.4, 39.9, 80))
                .executeHeight(120)
                .waypointSpeed(8)
                .heading(new WaypointHeading(90))
                .actions(List.of(new WaypointAction(1, "takePhoto", "{\"count\":1}")))
                .build();
        return KmzMission.builder()
                .missionName("巡检任务")
                .missionConfig(config)
                .waypoints(List.of(waypoint))
                .build();
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
