package com.ss.excel.importer;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.enums.CellExtraTypeEnum;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.metadata.CellExtra;
import com.alibaba.excel.read.builder.ExcelReaderBuilder;
import com.alibaba.excel.read.builder.ExcelReaderSheetBuilder;
import com.ss.excel.exception.ExcelOperationException;
import com.ss.excel.model.ExcelImportResult;
import com.ss.excel.model.ExcelRowError;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 以固定大小批次读取 Excel，并只在内存中保留失败行。
 */
public final class ExcelImporter {

    public static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    /**
     * 读取指定工作表。该方法不会关闭 {@code input}。
     *
     * @param input 调用方拥有的输入流
     * @param request 导入配置
     * @param <T> 行数据类型
     * @return 导入结果
     */
    public <T> ExcelImportResult<T> read(InputStream input, ExcelImportRequest<T> request) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(request, "request must not be null");
        BatchReadListener<T> listener = new BatchReadListener<>(request);
        try {
            ExcelReaderBuilder reader = EasyExcel.read(input, request.getModelType(), listener)
                    .autoCloseStream(false);
            if (request.isFillMergedCells()) {
                reader.extraRead(CellExtraTypeEnum.MERGE);
            }
            ExcelReaderSheetBuilder sheet = request.getSheetName() == null
                    ? reader.sheet(request.getSheetNo())
                    : reader.sheet(request.getSheetName());
            sheet.headRowNumber(request.getHeadRowCount()).doRead();
            return listener.result();
        } catch (ExcelOperationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExcelOperationException("read", request.sheetDescription(), exception);
        }
    }

    static void validateMergeRanges(List<CellExtra> ranges, int headRowCount) {
        Objects.requireNonNull(ranges, "ranges must not be null");
        List<CellExtra> validated = new ArrayList<>();
        for (CellExtra range : ranges) {
            Objects.requireNonNull(range, "merge range must not be null");
            Integer firstRow = range.getFirstRowIndex();
            Integer lastRow = range.getLastRowIndex();
            Integer firstColumn = range.getFirstColumnIndex();
            Integer lastColumn = range.getLastColumnIndex();
            if (firstRow == null || lastRow == null || firstColumn == null || lastColumn == null
                    || firstRow < 0 || firstColumn < 0 || firstRow > lastRow || firstColumn > lastColumn) {
                throw new IllegalArgumentException("Invalid merge range");
            }
            if (firstRow < headRowCount && lastRow >= headRowCount) {
                throw new IllegalArgumentException("Merge range must not cross the header boundary");
            }
            for (CellExtra existing : validated) {
                if (overlaps(range, existing)) {
                    throw new IllegalArgumentException("Merge ranges must not overlap");
                }
            }
            validated.add(range);
        }
    }

    private static boolean overlaps(CellExtra first, CellExtra second) {
        return first.getFirstRowIndex() <= second.getLastRowIndex()
                && first.getLastRowIndex() >= second.getFirstRowIndex()
                && first.getFirstColumnIndex() <= second.getLastColumnIndex()
                && first.getLastColumnIndex() >= second.getFirstColumnIndex();
    }

    private static final class BatchReadListener<T> extends AnalysisEventListener<T> {

        private final ExcelImportRequest<T> request;
        private final List<T> batchRows;
        private final List<Integer> batchRowNumbers;
        private final List<ExcelRowError<T>> errors = new ArrayList<>();
        private final Map<Integer, String> headers = new TreeMap<>();
        private final List<CellExtra> mergeRanges = new ArrayList<>();
        private int batchIndex;
        private int rowCount;
        private int successCount;

        private BatchReadListener(ExcelImportRequest<T> request) {
            this.request = request;
            this.batchRows = new ArrayList<>(request.getBatchSize());
            this.batchRowNumbers = new ArrayList<>(request.getBatchSize());
        }

        @Override
        public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
            headers.clear();
            if (headMap != null) {
                headers.putAll(headMap);
            }
        }

        @Override
        public void invoke(T data, AnalysisContext context) {
            if (rowCount >= request.getMaxRows()) {
                throw new IllegalStateException("Excel row limit exceeded: " + request.getMaxRows());
            }
            batchRows.add(data);
            batchRowNumbers.add(context.readRowHolder().getRowIndex() + 1);
            rowCount++;
            if (!request.isFillMergedCells() && batchRows.size() == request.getBatchSize()) {
                processBatch();
            }
        }

        @Override
        public void extra(CellExtra extra, AnalysisContext context) {
            if (request.isFillMergedCells() && extra.getType() == CellExtraTypeEnum.MERGE) {
                mergeRanges.add(extra);
            }
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            if (request.isFillMergedCells()) {
                fillMergedCells();
                processDeferredRows();
            } else {
                processBatch();
            }
        }

        private void fillMergedCells() {
            validateMergeRanges(mergeRanges, request.getHeadRowCount());
            if (mergeRanges.isEmpty()) {
                return;
            }
            Map<Integer, Integer> dataIndexByPhysicalRow = new HashMap<>();
            for (int index = 0; index < batchRowNumbers.size(); index++) {
                dataIndexByPhysicalRow.put(batchRowNumbers.get(index) - 1, index);
            }
            Map<Integer, Field> fields = resolveColumnFields(request.getModelType());
            for (CellExtra range : mergeRanges) {
                if (range.getLastRowIndex() < request.getHeadRowCount()) {
                    continue;
                }
                Integer sourceDataIndex = dataIndexByPhysicalRow.get(range.getFirstRowIndex());
                Field sourceField = fields.get(range.getFirstColumnIndex());
                if (sourceDataIndex == null || sourceField == null) {
                    throw new IllegalArgumentException("Merge range references an unread row or unmapped column");
                }
                Object sourceValue = getField(sourceField, batchRows.get(sourceDataIndex));
                for (int physicalRow = range.getFirstRowIndex();
                     physicalRow <= range.getLastRowIndex(); physicalRow++) {
                    Integer targetDataIndex = dataIndexByPhysicalRow.get(physicalRow);
                    if (targetDataIndex == null) {
                        throw new IllegalArgumentException("Merge range references an unread data row");
                    }
                    for (int column = range.getFirstColumnIndex();
                         column <= range.getLastColumnIndex(); column++) {
                        Field targetField = fields.get(column);
                        if (targetField == null) {
                            throw new IllegalArgumentException("Merge range references an unmapped column");
                        }
                        setField(targetField, batchRows.get(targetDataIndex), sourceValue);
                    }
                }
            }
        }

        private void processDeferredRows() {
            List<T> deferredRows = List.copyOf(batchRows);
            List<Integer> deferredRowNumbers = List.copyOf(batchRowNumbers);
            batchRows.clear();
            batchRowNumbers.clear();
            for (int offset = 0; offset < deferredRows.size(); offset += request.getBatchSize()) {
                int end = Math.min(offset + request.getBatchSize(), deferredRows.size());
                batchRows.addAll(deferredRows.subList(offset, end));
                batchRowNumbers.addAll(deferredRowNumbers.subList(offset, end));
                processBatch();
            }
        }

        private Map<Integer, Field> resolveColumnFields(Class<?> modelType) {
            Map<Integer, Field> result = new LinkedHashMap<>();
            List<Field> implicitFields = new ArrayList<>();
            for (Class<?> current = modelType; current != null && current != Object.class;
                 current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    ExcelProperty property = field.getAnnotation(ExcelProperty.class);
                    if (property == null) {
                        continue;
                    }
                    if (!field.trySetAccessible()) {
                        throw new IllegalArgumentException("Excel model field is not accessible: " + field.getName());
                    }
                    if (property.index() >= 0) {
                        if (result.put(property.index(), field) != null) {
                            throw new IllegalArgumentException("Duplicate Excel column index: " + property.index());
                        }
                    } else {
                        implicitFields.add(field);
                    }
                }
            }
            int columnIndex = 0;
            for (Field field : implicitFields) {
                while (result.containsKey(columnIndex)) {
                    columnIndex++;
                }
                result.put(columnIndex++, field);
            }
            return result;
        }

        private Object getField(Field field, T target) {
            try {
                return field.get(target);
            } catch (IllegalAccessException exception) {
                throw new IllegalArgumentException("Unable to read merged Excel field", exception);
            }
        }

        private void setField(Field field, T target, Object value) {
            try {
                field.set(target, value);
            } catch (IllegalAccessException | IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unable to assign merged Excel field", exception);
            }
        }

        private void processBatch() {
            if (batchRows.isEmpty()) {
                return;
            }
            List<T> rows = List.copyOf(batchRows);
            List<Integer> rowNumbers = List.copyOf(batchRowNumbers);
            ExcelBatchContext context = new ExcelBatchContext(
                    batchIndex, rowNumbers.get(0), request.getAttributes());
            Map<Integer, Map<Integer, String>> processorErrors = request.getProcessor().process(rows, context);
            if (processorErrors == null) {
                throw new IllegalArgumentException("processor result must not be null");
            }

            TreeMap<Integer, Map<Integer, String>> sortedErrors = new TreeMap<>(processorErrors);
            for (Map.Entry<Integer, Map<Integer, String>> rowError : sortedErrors.entrySet()) {
                Integer batchRowIndex = rowError.getKey();
                if (batchRowIndex == null || batchRowIndex < 0 || batchRowIndex >= rows.size()) {
                    throw new IllegalArgumentException("processor row index is outside the current batch");
                }
                Map<Integer, String> columnErrors = sanitizeColumnErrors(rowError.getValue());
                errors.add(new ExcelRowError<>(
                        rowNumbers.get(batchRowIndex), rows.get(batchRowIndex), columnErrors));
            }
            successCount += rows.size() - sortedErrors.size();
            batchIndex++;
            batchRows.clear();
            batchRowNumbers.clear();
        }

        private Map<Integer, String> sanitizeColumnErrors(Map<Integer, String> source) {
            Objects.requireNonNull(source, "column errors must not be null");
            if (source.isEmpty()) {
                throw new IllegalArgumentException("column errors must not be empty");
            }
            TreeMap<Integer, String> sanitized = new TreeMap<>();
            source.forEach((columnIndex, message) -> {
                if (columnIndex == null || columnIndex < 0) {
                    throw new IllegalArgumentException("processor column index must not be negative");
                }
                Objects.requireNonNull(message, "processor error message must not be null");
                sanitized.put(columnIndex, message.length() <= MAX_ERROR_MESSAGE_LENGTH
                        ? message : message.substring(0, MAX_ERROR_MESSAGE_LENGTH));
            });
            return Collections.unmodifiableMap(sanitized);
        }

        private ExcelImportResult<T> result() {
            return new ExcelImportResult<>(successCount, headers, errors);
        }
    }
}
