package com.ss.excel.exporter;

import com.alibaba.excel.write.handler.RowWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteTableHolder;
import com.ss.excel.model.ExcelRowError;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;

import java.util.List;

/**
 * 在错误工作簿中为失败列创建红色批注。
 */
public final class ErrorCommentWriteHandler implements RowWriteHandler {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private final List<? extends ExcelRowError<?>> errors;
    private CellStyle errorStyle;
    private Drawing<?> drawing;

    /**
     * 创建并初始化实例。
     *
     * @param errors 错误信息列表
     */
    public ErrorCommentWriteHandler(List<? extends ExcelRowError<?>> errors) {
        this.errors = List.copyOf(errors);
    }

    @Override
    public void afterRowDispose(WriteSheetHolder writeSheetHolder, WriteTableHolder writeTableHolder,
                                Row row, Integer relativeRowIndex, Boolean isHead) {
        if (Boolean.TRUE.equals(isHead)) {
            return;
        }
        if (relativeRowIndex == null || relativeRowIndex < 0 || relativeRowIndex >= errors.size()) {
            throw new IllegalStateException("Error row index is outside the configured error list");
        }

        Workbook workbook = writeSheetHolder.getSheet().getWorkbook();
        initializeResources(workbook, writeSheetHolder);
        CreationHelper creationHelper = workbook.getCreationHelper();
        errors.get(relativeRowIndex).getColumnErrors().forEach((columnIndex, message) -> {
            Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            ClientAnchor anchor = creationHelper.createClientAnchor();
            anchor.setCol1(columnIndex);
            anchor.setCol2(columnIndex + 2);
            anchor.setRow1(row.getRowNum());
            anchor.setRow2(row.getRowNum() + 2);
            Comment comment = drawing.createCellComment(anchor);
            comment.setAuthor("simple-secret");
            comment.setString(creationHelper.createRichTextString(truncate(message)));
            cell.setCellStyle(errorStyle);
            cell.setCellComment(comment);
        });
    }

    private void initializeResources(Workbook workbook, WriteSheetHolder writeSheetHolder) {
        if (errorStyle == null) {
            errorStyle = workbook.createCellStyle();
            errorStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            errorStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
            errorStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        }
        if (drawing == null) {
            drawing = writeSheetHolder.getSheet().createDrawingPatriarch();
        }
    }

    private String truncate(String message) {
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? message : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
