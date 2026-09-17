package com.ss.gb28181;

import java.util.HexFormat;

/** GB/T 28181-2022 A.3.4 preset operation. Construction does not send a command or move a device. */
public record PresetCommand(Action action, int presetId) {
    public PresetCommand {
        if (action == null) throw new IllegalArgumentException("Preset action is required");
        if (presetId < 1 || presetId > 255)
            throw new IllegalArgumentException("Preset number must be in 1..255; zero is reserved");
    }

    /** Saves the device's current position when explicitly sent by the host. */
    public static PresetCommand set(int presetId) { return new PresetCommand(Action.SET, presetId); }
    /** Moves to a stored preset when explicitly sent by the host. */
    public static PresetCommand goTo(int presetId) { return new PresetCommand(Action.GOTO, presetId); }
    /** Deletes the device preset when explicitly sent by the host. */
    public static PresetCommand remove(int presetId) { return new PresetCommand(Action.REMOVE, presetId); }

    /** Returns the eight wire bytes as sixteen uppercase hexadecimal characters. */
    public String hex() {
        int operation = switch (action) {
            case SET -> 0x81;
            case GOTO -> 0x82;
            case REMOVE -> 0x83;
        };
        // XML DeviceID selects the channel; keep the existing legacy address of one.
        byte[] bytes = {(byte) 0xA5, 0x0F, 0x01, (byte) operation, 0, (byte) presetId, 0, 0};
        int checksum = 0;
        for (int i = 0; i < bytes.length - 1; i++) checksum += bytes[i] & 0xFF;
        bytes[7] = (byte) checksum;
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }

    public enum Action { SET, GOTO, REMOVE }
}
