package com.ss.excel.strategy;

import com.alibaba.excel.metadata.Head;
import com.alibaba.excel.write.merge.AbstractMergeStrategy;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 在一个列上应用多个已校验的行合并范围。
 */
public final class ManyRowMergeStrategy extends AbstractMergeStrategy {

    private final int columnIndex;
    private final boolean continuousRange;
    private final List<CellRangeAddress> ranges;
    private final Set<Sheet> appliedSheets = Collections.newSetFromMap(new WeakHashMap<>());

    public ManyRowMergeStrategy(boolean continuousRange, int columnIndex, List<Integer> rowIndexes) {
        if (columnIndex < 0) {
            throw new IllegalArgumentException("columnIndex must not be negative");
        }
        this.columnIndex = columnIndex;
        this.continuousRange = continuousRange;
        this.ranges = buildRanges(continuousRange, columnIndex, rowIndexes);
    }

    public ManyRowMergeStrategy(int columnIndex, List<Integer> rowIndexPairs) {
        this(false, columnIndex, rowIndexPairs);
    }

    public int getColumnIndex() {
        return columnIndex;
    }

    public boolean isContinuousRange() {
        return continuousRange;
    }

    @Override
    protected void merge(Sheet sheet, Cell cell, Head head, Integer relativeRowIndex) {
        if (cell.getColumnIndex() != columnIndex || !appliedSheets.add(sheet)) {
            return;
        }
        ranges.forEach(sheet::addMergedRegion);
    }

    private static List<CellRangeAddress> buildRanges(boolean continuous, int columnIndex,
                                                       List<Integer> rowIndexes) {
        if (rowIndexes == null || rowIndexes.isEmpty()) {
            return List.of();
        }
        List<Integer> indexes = List.copyOf(rowIndexes);
        List<CellRangeAddress> result = new ArrayList<>();
        if (continuous) {
            if (indexes.size() < 2) {
                throw new IllegalArgumentException("continuous ranges require a start and at least one boundary");
            }
            int start = requireNonNegative(indexes.get(0));
            for (int index = 1; index < indexes.size(); index++) {
                int end = requireNonNegative(indexes.get(index));
                if (end <= start) {
                    throw new IllegalArgumentException("continuous range boundaries must be strictly increasing");
                }
                result.add(new CellRangeAddress(start, end, columnIndex, columnIndex));
                start = end + 1;
            }
            return List.copyOf(result);
        }

        if (indexes.size() % 2 != 0) {
            throw new IllegalArgumentException("rowIndexPairs must contain complete start/end pairs");
        }
        int previousEnd = -1;
        for (int index = 0; index < indexes.size(); index += 2) {
            int start = requireNonNegative(indexes.get(index));
            int end = requireNonNegative(indexes.get(index + 1));
            if (start >= end) {
                throw new IllegalArgumentException("merge range start must be before end");
            }
            if (start <= previousEnd) {
                throw new IllegalArgumentException("merge ranges must not overlap");
            }
            result.add(new CellRangeAddress(start, end, columnIndex, columnIndex));
            previousEnd = end;
        }
        return List.copyOf(result);
    }

    private static int requireNonNegative(Integer value) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException("row index must not be negative");
        }
        return value;
    }
}
