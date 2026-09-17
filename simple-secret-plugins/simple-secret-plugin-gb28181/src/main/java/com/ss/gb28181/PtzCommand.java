package com.ss.gb28181;

import java.util.HexFormat;

/**
 * A GB/T 28181-2022 Annex A.3 PTZ command; constructing or encoding it does not move a device.
 * Pan/tilt speeds range from 0 to 255 and zoom speed from 0 to 15, slowest to fastest.
 * A zero speed still requests motion when its direction is active. Inactive axes require zero speed.
 * The host must explicitly send {@link #stop()} when motion should cease.
 */
public record PtzCommand(Pan pan, Tilt tilt, Zoom zoom, int panSpeed, int tiltSpeed, int zoomSpeed) {
    public PtzCommand {
        if (pan == null || tilt == null || zoom == null)
            throw new IllegalArgumentException("PTZ directions are required");
        if (panSpeed < 0 || panSpeed > 255 || tiltSpeed < 0 || tiltSpeed > 255
                || zoomSpeed < 0 || zoomSpeed > 15)
            throw new IllegalArgumentException("PTZ speed outside supported range");
        if ((pan == Pan.NONE && panSpeed != 0) || (tilt == Tilt.NONE && tiltSpeed != 0)
                || (zoom == Zoom.NONE && zoomSpeed != 0))
            throw new IllegalArgumentException("Inactive PTZ axes require zero speed");
    }

    /** Stops all three axes. Sending the result remains the host's responsibility. */
    public static PtzCommand stop() {
        return new PtzCommand(Pan.NONE, Tilt.NONE, Zoom.NONE, 0, 0, 0);
    }

    public static PtzCommand move(Pan pan, Tilt tilt, int panSpeed, int tiltSpeed) {
        return new PtzCommand(pan, tilt, Zoom.NONE, panSpeed, tiltSpeed, 0);
    }

    public static PtzCommand zoom(Zoom zoom, int speed) {
        return new PtzCommand(Pan.NONE, Tilt.NONE, zoom, 0, 0, speed);
    }

    /** Returns the eight wire bytes as sixteen uppercase hexadecimal characters. */
    public String hex() {
        int direction = switch (pan) {
            case NONE -> 0;
            case LEFT -> 0x02;
            case RIGHT -> 0x01;
        };
        direction |= switch (tilt) {
            case NONE -> 0;
            case UP -> 0x08;
            case DOWN -> 0x04;
        };
        direction |= switch (zoom) {
            case NONE -> 0;
            case IN -> 0x10;
            case OUT -> 0x20;
        };
        // Version 1.0 gives 0F in byte 2. The legacy address is unused: XML DeviceID selects the device.
        byte[] command = {(byte) 0xA5, 0x0F, 0x01, (byte) direction,
                (byte) panSpeed, (byte) tiltSpeed, (byte) (zoomSpeed << 4), 0};
        int checksum = 0;
        for (int i = 0; i < command.length - 1; i++) checksum += command[i] & 0xFF;
        command[7] = (byte) checksum;
        return HexFormat.of().withUpperCase().formatHex(command);
    }

    public enum Pan { NONE, LEFT, RIGHT }
    public enum Tilt { NONE, UP, DOWN }
    public enum Zoom { NONE, IN, OUT }
}
