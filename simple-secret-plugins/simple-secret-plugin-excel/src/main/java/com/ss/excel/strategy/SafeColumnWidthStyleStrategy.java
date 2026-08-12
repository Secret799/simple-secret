package com.ss.excel.strategy;

import com.alibaba.excel.metadata.Head;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.style.column.AbstractColumnWidthStyleStrategy;
import org.apache.poi.ss.usermodel.Cell;

import java.util.List;

/**
 * 根据单元格文本增长列宽，并限制在 POI 安全范围内。
 */
public final class SafeColumnWidthStyleStrategy extends AbstractColumnWidthStyleStrategy {

    public static final int DEFAULT_MAX_CHARACTERS = 60;

    private final int maxWidth;

    /**
     * 创建并初始化实例。
     */
    public SafeColumnWidthStyleStrategy() {
        this(DEFAULT_MAX_CHARACTERS);
    }

    /**
     * 创建并初始化实例。
     *
     * @param maxCharacters 自动列宽允许的最大字符数
     */
    public SafeColumnWidthStyleStrategy(int maxCharacters) {
        if (maxCharacters < 1 || maxCharacters > 255) {
            throw new IllegalArgumentException("maxCharacters must be between 1 and 255");
        }
        this.maxWidth = maxCharacters * 256;
    }

    @Override
    protected void setColumnWidth(WriteSheetHolder writeSheetHolder,
                                  List<WriteCellData<?>> cellDataList, Cell cell, Head head,
                                  Integer relativeRowIndex, Boolean isHead) {
        int textLength = displayWidth(cell);
        int desiredWidth = Math.min(maxWidth, Math.max(256, (textLength + 2) * 256));
        int columnIndex = cell.getColumnIndex();
        int currentWidth = writeSheetHolder.getSheet().getColumnWidth(columnIndex);
        if (currentWidth > maxWidth) {
            writeSheetHolder.getSheet().setColumnWidth(columnIndex, maxWidth);
        } else if (desiredWidth > currentWidth) {
            writeSheetHolder.getSheet().setColumnWidth(columnIndex, desiredWidth);
        }
    }

    private int displayWidth(Cell cell) {
        String value = switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> cell.getNumericCellValue() == Math.rint(cell.getNumericCellValue())
                    ? Long.toString((long) cell.getNumericCellValue()) : Double.toString(cell.getNumericCellValue());
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            case BLANK, ERROR, _NONE -> "";
        };
        return value.codePoints().map(codePoint -> codePoint > 0xFF ? 2 : 1).sum();
    }
}
