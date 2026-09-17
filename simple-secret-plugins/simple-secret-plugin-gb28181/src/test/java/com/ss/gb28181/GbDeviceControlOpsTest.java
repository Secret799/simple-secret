package com.ss.gb28181;

import com.ss.gb28181.internal.GbXml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.ss.gb28181.Gb28181ServerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class GbDeviceControlOpsTest {
    static final String CHANNEL = "34020000001320000002";
    final String password = "device-control-password";

    @Test
    void commandsRejectNullAndExposeExactOperations() {
        assertThrows(NullPointerException.class, () -> GbTeleBootCommand.valueOf(null));
        assertThrows(NullPointerException.class, () -> GbRecordControlCommand.valueOf(null));
        assertThrows(NullPointerException.class, () -> GbGuardCommand.valueOf(null));
        assertEquals("BOOT", GbTeleBootCommand.BOOT.name());
        assertEquals("RECORD", GbRecordControlCommand.RECORD.name());
        assertEquals("STOP_RECORD", GbRecordControlCommand.STOP_RECORD.name());
        assertEquals("SET_GUARD", GbGuardCommand.SET_GUARD.name());
        assertEquals("RESET_GUARD", GbGuardCommand.RESET_GUARD.name());
    }

    @Test
    void encodesDeviceControlOperationsWithExactXml() {
        String boot = xml(GbXml.teleBoot(DEVICE, 7));
        assertTrue(boot.contains("<Control>"));
        assertTrue(boot.contains("<CmdType>DeviceControl</CmdType>"));
        assertTrue(boot.contains("<SN>7</SN>"));
        assertTrue(boot.contains("<DeviceID>" + DEVICE + "</DeviceID>"));
        assertTrue(boot.contains("<TeleBoot>Boot</TeleBoot>"));
        assertFalse(boot.contains("<Info>"));
        assertTrue(xml(GbXml.recordControl(CHANNEL, 8, GbRecordControlCommand.RECORD))
                .contains("<RecordCmd>Record</RecordCmd>"));
        assertTrue(xml(GbXml.recordControl(CHANNEL, 9, GbRecordControlCommand.STOP_RECORD))
                .contains("<RecordCmd>StopRecord</RecordCmd>"));
        assertTrue(xml(GbXml.guard(CHANNEL, 10, GbGuardCommand.SET_GUARD))
                .contains("<GuardCmd>SetGuard</GuardCmd>"));
        assertTrue(xml(GbXml.guard(CHANNEL, 11, GbGuardCommand.RESET_GUARD))
                .contains("<GuardCmd>ResetGuard</GuardCmd>"));
        assertThrows(IllegalArgumentException.class, () -> GbXml.teleBoot("123", 1));
        assertThrows(IllegalArgumentException.class, () -> GbXml.guard(CHANNEL, 0, GbGuardCommand.SET_GUARD));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UDP", "TCP"})
    void sendsControlsAndCompletesOnlyOnSipReceipt(String transport) throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password));
             var peer = new Peer(port, transport)) {
            peer.register(password);
            var boot = server.teleBoot(DEVICE);
            var request = peer.receive();
            assertTrue(request.body().contains("<TeleBoot>Boot</TeleBoot>"));
            assertFalse(boot.isDone());
            peer.respond(request, 200);
            boot.get(2, TimeUnit.SECONDS);

            var recording = server.controlRecording(DEVICE, CHANNEL, GbRecordControlCommand.STOP_RECORD);
            request = peer.receive();
            assertTrue(request.body().contains("<RecordCmd>StopRecord</RecordCmd>"));
            peer.respond(request, 200);
            recording.get(2, TimeUnit.SECONDS);

            var guard = server.controlGuard(DEVICE, CHANNEL, GbGuardCommand.RESET_GUARD);
            request = peer.receive();
            assertTrue(request.body().contains("<GuardCmd>ResetGuard</GuardCmd>"));
            peer.respond(request, 200);
            guard.get(2, TimeUnit.SECONDS);
        }
    }

    private static String xml(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
