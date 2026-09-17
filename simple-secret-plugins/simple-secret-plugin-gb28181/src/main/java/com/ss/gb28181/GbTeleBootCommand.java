package com.ss.gb28181;

/** Remote device boot operation. */
public enum GbTeleBootCommand {
    BOOT("Boot");
    private final String wireValue;
    GbTeleBootCommand(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
