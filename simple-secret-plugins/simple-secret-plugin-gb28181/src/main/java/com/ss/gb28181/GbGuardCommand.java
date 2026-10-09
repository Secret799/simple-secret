package com.ss.gb28181;

/** Remote alarm guard operation. */
public enum GbGuardCommand {
    SET_GUARD("SetGuard"), RESET_GUARD("ResetGuard");
    private final String wireValue;
    GbGuardCommand(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
