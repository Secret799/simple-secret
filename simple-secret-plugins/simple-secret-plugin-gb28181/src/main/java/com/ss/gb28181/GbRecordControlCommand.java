package com.ss.gb28181;

/** Remote recording operation. */
public enum GbRecordControlCommand {
    RECORD("Record"), STOP_RECORD("StopRecord");
    private final String wireValue;
    GbRecordControlCommand(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
