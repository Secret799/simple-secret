package com.ss.zlm4j.service.validation;

import com.ss.zlm4j.config.properties.VideoStackValidationProperties;
import com.ss.zlm4j.service.domain.bo.VideoStackBO;
import com.ss.zlm4j.service.domain.bo.VideoStackWindowBO;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 在分配 native 内存前校验视频拼接参数和布局。
 */
public class VideoStackValidator {

    private static final Pattern RGB_HEX = Pattern.compile("(?i)^[0-9a-f]{6}$");

    private final VideoStackValidationProperties properties;

    public VideoStackValidator(VideoStackValidationProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验拼接任务参数。
     *
     * @param value 拼接参数
     */
    public void validate(VideoStackBO value) {
        if (value == null) {
            throw new IllegalArgumentException("Video stack configuration is required");
        }
        if (value.getId() == null || value.getId().isBlank()) {
            throw new IllegalArgumentException("Video stack id is required");
        }
        int rows = positive(value.getRow(), "row");
        int columns = positive(value.getCol(), "col");
        int width = positive(value.getWidth(), "width");
        int height = positive(value.getHeight(), "height");
        if (width > properties.getMaxDimension() || height > properties.getMaxDimension()) {
            throw new IllegalArgumentException("Video stack dimensions exceed the configured limit");
        }
        long pixels = multiplyExact(width, height, "Video stack dimensions overflow");
        if (pixels > properties.getMaxPixels()) {
            throw new IllegalArgumentException("Video stack pixel count exceeds the configured limit");
        }
        long cells = multiplyExact(rows, columns, "Video stack grid size overflows");
        if (cells > properties.getMaxCells()) {
            throw new IllegalArgumentException("Video stack grid exceeds the configured limit");
        }
        if ((width & 1) != 0 || (height & 1) != 0 || width % columns != 0 || height % rows != 0) {
            throw new IllegalArgumentException("Video stack dimensions must be even and divisible by the grid");
        }
        requireColor(value.getFillColor(), "fillColor");
        if (Boolean.TRUE.equals(value.getGridLineEnable())) {
            requireColor(value.getGridLineColor(), "gridLineColor");
            int gridLineWidth = positive(value.getGridLineWidth(), "gridLineWidth");
            if (gridLineWidth >= Math.min(width / columns, height / rows)) {
                throw new IllegalArgumentException("Grid line width is too large");
            }
        }
        validateWindows(value.getWindowList(), rows, columns, Math.toIntExact(cells));
    }

    private void validateWindows(List<VideoStackWindowBO> windows, int rows, int columns, int cellCount) {
        if (windows == null || windows.isEmpty()) {
            return;
        }
        if (windows.size() > properties.getMaxWindows()) {
            throw new IllegalArgumentException("Video stack window count exceeds the configured limit");
        }
        Set<Integer> occupied = new HashSet<>();
        for (VideoStackWindowBO window : windows) {
            if (window == null) {
                throw new IllegalArgumentException("Video stack window must not be null");
            }
            boolean hasVideo = window.getVideoUrl() != null && !window.getVideoUrl().isBlank();
            boolean hasImage = window.getImgUrl() != null && !window.getImgUrl().isBlank();
            if (hasVideo && hasImage) {
                throw new IllegalArgumentException("Video stack window cannot define both videoUrl and imgUrl");
            }
            requireColor(window.getFillColor(), "window.fillColor");
            List<Integer> span = window.getSpan();
            if (span == null || span.isEmpty()) {
                throw new IllegalArgumentException("Video stack window span is required");
            }
            Set<Integer> unique = new HashSet<>();
            for (Integer cell : span) {
                if (cell == null || cell < 1 || cell > cellCount || !unique.add(cell)) {
                    throw new IllegalArgumentException("Video stack window span is invalid");
                }
                if (!occupied.add(cell)) {
                    throw new IllegalArgumentException("Video stack windows overlap");
                }
            }
            requireRectangle(unique, rows, columns);
        }
    }

    private static void requireRectangle(Set<Integer> span, int rows, int columns) {
        int minRow = rows;
        int maxRow = -1;
        int minColumn = columns;
        int maxColumn = -1;
        for (int cell : span) {
            int index = cell - 1;
            int row = index / columns;
            int column = index % columns;
            minRow = Math.min(minRow, row);
            maxRow = Math.max(maxRow, row);
            minColumn = Math.min(minColumn, column);
            maxColumn = Math.max(maxColumn, column);
        }
        long rectangleSize = (long) (maxRow - minRow + 1) * (maxColumn - minColumn + 1);
        if (rectangleSize != span.size()) {
            throw new IllegalArgumentException("Video stack window span must form a rectangle");
        }
    }

    private static int positive(Integer value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static long multiplyExact(int left, int right, String message) {
        try {
            return Math.multiplyExact((long) left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(message, exception);
        }
    }

    private static void requireColor(String value, String name) {
        if (value == null || !RGB_HEX.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a six-digit RGB color");
        }
    }
}
