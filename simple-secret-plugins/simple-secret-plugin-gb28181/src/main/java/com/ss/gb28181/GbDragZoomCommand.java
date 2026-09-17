package com.ss.gb28181;

/** Pixel dimensions and rectangle coordinates for GB28181 drag zoom control. */
public record GbDragZoomCommand(int length, int width, int midPointX, int midPointY, int lengthX, int lengthY) {
    private static final int MAX_PIXEL = 1_000_000;

    public GbDragZoomCommand {
        if (length < 0 || length > MAX_PIXEL || width < 0 || width > MAX_PIXEL
                || midPointX < 0 || midPointX > MAX_PIXEL || midPointY < 0 || midPointY > MAX_PIXEL
                || lengthX < 0 || lengthX > MAX_PIXEL || lengthY < 0 || lengthY > MAX_PIXEL) {
            throw new IllegalArgumentException("Drag zoom pixel values must be in 0..1000000");
        }
    }
}
