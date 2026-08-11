package com.ss.excel.strategy;

import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 为指定列和包含首尾的行范围添加显式下拉选项。
 */
public final class DropdownWriteHandler implements SheetWriteHandler {

    private static final int MAX_EXPLICIT_LIST_LENGTH = 255;

    private final int firstRowIndex;
    private final int lastRowIndex;
    private final Map<Integer, List<String>> optionsByColumn;

    public DropdownWriteHandler(int firstRowIndex, int lastRowIndex,
                                Map<Integer, ? extends List<String>> optionsByColumn) {
        if (firstRowIndex < 0) {
            throw new IllegalArgumentException("firstRowIndex must not be negative");
        }
        if (lastRowIndex < firstRowIndex) {
            throw new IllegalArgumentException("lastRowIndex must not be before firstRowIndex");
        }
        Objects.requireNonNull(optionsByColumn, "optionsByColumn must not be null");
        TreeMap<Integer, List<String>> copy = new TreeMap<>();
        optionsByColumn.forEach((columnIndex, options) -> copy.put(
                validateColumnIndex(columnIndex), copyOptions(options)));
        this.firstRowIndex = firstRowIndex;
        this.lastRowIndex = lastRowIndex;
        this.optionsByColumn = Collections.unmodifiableMap(new LinkedHashMap<>(copy));
    }

    public DropdownWriteHandler(Map<Integer, ? extends List<String>> optionsByColumn) {
        this(1, 65_535, optionsByColumn);
    }

    @Override
    public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
        if (optionsByColumn.isEmpty()) {
            return;
        }
        Sheet sheet = writeSheetHolder.getSheet();
        DataValidationHelper helper = sheet.getDataValidationHelper();
        optionsByColumn.forEach((columnIndex, options) -> {
            DataValidationConstraint constraint = helper.createExplicitListConstraint(options.toArray(String[]::new));
            CellRangeAddressList addresses = new CellRangeAddressList(
                    firstRowIndex, lastRowIndex, columnIndex, columnIndex);
            DataValidation validation = helper.createValidation(constraint, addresses);
            validation.setShowErrorBox(true);
            sheet.addValidationData(validation);
        });
    }

    private static int validateColumnIndex(Integer columnIndex) {
        if (columnIndex == null || columnIndex < 0) {
            throw new IllegalArgumentException("columnIndex must not be negative");
        }
        return columnIndex;
    }

    private static List<String> copyOptions(List<String> source) {
        Objects.requireNonNull(source, "dropdown options must not be null");
        if (source.isEmpty()) {
            throw new IllegalArgumentException("dropdown options must not be empty");
        }
        List<String> copy = new ArrayList<>(source.size());
        int encodedLength = 0;
        for (String option : source) {
            Objects.requireNonNull(option, "dropdown option must not be null");
            if (option.isBlank()) {
                throw new IllegalArgumentException("dropdown option must not be blank");
            }
            copy.add(option);
            encodedLength += option.length();
        }
        encodedLength += copy.size() - 1;
        if (encodedLength > MAX_EXPLICIT_LIST_LENGTH) {
            throw new IllegalArgumentException("explicit dropdown options exceed 255 characters");
        }
        return List.copyOf(copy);
    }
}
