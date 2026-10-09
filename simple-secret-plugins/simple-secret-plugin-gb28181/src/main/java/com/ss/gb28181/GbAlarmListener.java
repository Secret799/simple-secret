package com.ss.gb28181;

/** Receives alarms admitted by a GB28181 server. */
@FunctionalInterface
public interface GbAlarmListener {

    void onAlarm(GbAlarmEvent event);
}
