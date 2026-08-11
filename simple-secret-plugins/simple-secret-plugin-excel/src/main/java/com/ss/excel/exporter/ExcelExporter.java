package com.ss.excel.exporter;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.builder.ExcelWriterSheetBuilder;
import com.alibaba.excel.write.handler.WriteHandler;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.ss.excel.exception.ExcelOperationException;
import com.ss.excel.model.ExcelRowError;
import com.ss.excel.model.ExcelSheet;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;

import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 基于调用方提供的输出流写入一个或多个 Excel 工作表。
 */
public final class ExcelExporter {

    public static final int MAX_SHEETS = 100;

    /**
     * 写入完整工作簿。该方法不会关闭 {@code output}。
     *
     * @param output 调用方拥有的输出流
     * @param sheets 工作表定义
     */
    public void write(OutputStream output, List<ExcelSheet<?>> sheets) {
        Objects.requireNonNull(output, "output must not be null");
        Objects.requireNonNull(sheets, "sheets must not be null");
        if (sheets.isEmpty()) {
            throw new IllegalArgumentException("At least one sheet is required");
        }
        if (sheets.size() > MAX_SHEETS) {
            throw new IllegalArgumentException("Workbook must not contain more than 100 sheets");
        }
        for (int index = 0; index < sheets.size(); index++) {
            Objects.requireNonNull(sheets.get(index), "sheets must not contain null: " + index);
        }

        ExcelWriter writer = null;
        RuntimeException writeFailure = null;
        try {
            writer = EasyExcel.write(output)
                    .autoCloseStream(false)
                    .build();
            for (int sheetIndex = 0; sheetIndex < sheets.size(); sheetIndex++) {
                ExcelSheet<?> sheet = sheets.get(sheetIndex);
                try {
                    writer.write(sheet.getRows(), createWriteSheet(sheetIndex, sheet));
                } catch (RuntimeException exception) {
                    throw new ExcelOperationException("write", sheet.getName(), exception);
                }
            }
        } catch (RuntimeException exception) {
            writeFailure = exception;
            throw exception;
        } finally {
            if (writer != null) {
                try {
                    writer.finish();
                } catch (RuntimeException finishFailure) {
                    if (writeFailure != null) {
                        writeFailure.addSuppressed(finishFailure);
                    } else {
                        throw new ExcelOperationException("finish", sheets.get(0).getName(), finishFailure);
                    }
                }
            }
        }
    }

    /**
     * 将失败行紧凑写入一个工作表，并在错误列添加批注。该方法不会关闭 {@code output}。
     *
     * @param output 调用方拥有的输出流
     * @param sheet 工作表表头、名称和自定义处理器
     * @param errors 失败行及列级错误
     * @param <T> 行数据类型
     */
    public <T> void writeErrors(OutputStream output, ExcelSheet<T> sheet, List<ExcelRowError<T>> errors) {
        Objects.requireNonNull(sheet, "sheet must not be null");
        Objects.requireNonNull(errors, "errors must not be null");
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("At least one error row is required");
        }
        ExcelSheet.Builder<T> builder = ExcelSheet.<T>builder()
                .name(sheet.getName())
                .rows(errors.stream()
                        .map(ExcelRowError::getValue)
                        .collect(Collectors.toList()));
        if (sheet.getModelType() != null) {
            builder.modelType(sheet.getModelType());
        } else {
            builder.head(sheet.getHead());
        }
        sheet.getWriteHandlers().forEach(builder::addWriteHandler);
        builder.addWriteHandler(new ErrorCommentWriteHandler(errors));
        write(output, List.of(builder.build()));
    }

    private WriteSheet createWriteSheet(int sheetIndex, ExcelSheet<?> sheet) {
        ExcelWriterSheetBuilder builder = EasyExcel.writerSheet(sheetIndex, sheet.getName());
        if (sheet.getModelType() != null) {
            builder.head(sheet.getModelType());
        } else {
            builder.head(sheet.getHead());
        }
        builder.registerWriteHandler(centeredStyle());
        for (WriteHandler writeHandler : sheet.getWriteHandlers()) {
            builder.registerWriteHandler(writeHandler);
        }
        return builder.build();
    }

    private HorizontalCellStyleStrategy centeredStyle() {
        WriteCellStyle headStyle = new WriteCellStyle();
        headStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        headStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        WriteCellStyle contentStyle = new WriteCellStyle();
        contentStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        contentStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        return new HorizontalCellStyleStrategy(headStyle, contentStyle);
    }
}
