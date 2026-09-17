package com.ss.gb28181;

/** Receives accepted MobilePosition subscription notifications. */
@FunctionalInterface
public interface GbMobilePositionListener {
    void onMobilePosition(GbMobilePositionEvent event);
}
