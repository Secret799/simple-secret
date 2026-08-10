package com.ss.kmz.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmzDomainModelTest {

    @Test
    void coordinateShouldParseAndFormatIndependentlyFromDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            Coordinate coordinate = Coordinate.fromKml("116.123456789,39.987654321,88.126");

            assertThat(coordinate).isEqualTo(new Coordinate(116.123456789, 39.987654321, 88.126));
            assertThat(coordinate.toKml()).isEqualTo("116.12345679,39.98765432,88.13");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void coordinateShouldRejectMalformedOrOutOfRangeValues() {
        assertThatThrownBy(() -> Coordinate.fromKml("116.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longitude,latitude");
        assertThatThrownBy(() -> Coordinate.fromKml("181,39,0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longitude");
        assertThatThrownBy(() -> new Coordinate(116, Double.NaN, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latitude");
    }

    @Test
    void buildersAndChainSettersShouldPreserveHoneybeeStyleApi() {
        WaypointAction action = new WaypointAction()
                .setActionId(7)
                .setActionType("takePhoto")
                .setActionParam("{\"count\":1}");
        Waypoint waypoint = Waypoint.builder()
                .index(2)
                .coordinate(new Coordinate(116.4, 39.9, 80))
                .executeHeight(120)
                .waypointSpeed(8)
                .heading(new WaypointHeading().setHeadingAngle(90))
                .actions(List.of(action))
                .build();
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
        KmzMission mission = KmzMission.builder()
                .missionName("巡检任务")
                .missionConfig(config)
                .waypoints(List.of(waypoint))
                .build();

        assertThat(mission.getMissionName()).isEqualTo("巡检任务");
        assertThat(mission.getMissionConfig().getExecuteRCLostAction()).isEqualTo(1);
        assertThat(mission.getWaypoints()).containsExactly(waypoint);
        assertThat(mission.getWaypoints().get(0).getActions()).containsExactly(action);
        assertThat(mission.toString()).contains("巡检任务", "takePhoto");
    }

    @Test
    void listSettersShouldCopyInputAndNormalizeNull() {
        Waypoint waypoint = new Waypoint();
        KmzMission mission = new KmzMission().setWaypoints(List.of(waypoint));

        assertThat(mission.getWaypoints()).containsExactly(waypoint);
        assertThat(new KmzMission().setWaypoints(null).getWaypoints()).isEmpty();
        assertThat(new Waypoint().setActions(null).getActions()).isEmpty();
    }

    @Test
    void fullConstructorsShouldRemainAvailableForHoneybeeMigration() {
        MissionConfig config = new MissionConfig(
                "safely", "goHome", "executeLostAction", 1,
                80, 10, 89, 67, 100);
        Waypoint waypoint = new Waypoint(
                0, new Coordinate(116.4, 39.9, 80), 120, 8,
                new WaypointHeading(90), List.of(new WaypointAction(1, "takePhoto", null)));
        KmzMission mission = new KmzMission("巡检任务", config, List.of(waypoint));

        assertThat(mission.getMissionConfig()).isEqualTo(config);
        assertThat(mission.getWaypoints()).containsExactly(waypoint);
    }

    @Test
    void builderShouldReturnIndependentSnapshotsLikeLombok() {
        KmzMission.Builder builder = KmzMission.builder().missionName("first");

        KmzMission first = builder.build();
        KmzMission second = builder.missionName("second").build();

        assertThat(first.getMissionName()).isEqualTo("first");
        assertThat(second.getMissionName()).isEqualTo("second");
        assertThat(first).isNotSameAs(second);
    }
}
