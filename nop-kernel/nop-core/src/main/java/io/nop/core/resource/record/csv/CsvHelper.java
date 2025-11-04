/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.record.csv;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.core.model.table.ICellView;
import io.nop.core.model.table.ITableView;
import io.nop.core.model.table.impl.BaseCell;
import io.nop.core.model.table.impl.BaseRow;
import io.nop.core.model.table.impl.BaseTable;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.IResource;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import org.apache.commons.csv.CSVFormat;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class CsvHelper {

    public static <T> List<T> readCsv(IResource resource, Type type, CSVFormat format) {
        return readCsv(resource, type, format, null);
    }

    public static <T> List<T> readCsv(IResource resource, Type type, CSVFormat format, String encoding) {
        IGenericType rowType = type == null ? null : ReflectionManager.instance().buildGenericType(type);
        CsvRecordInput<T> input = new CsvRecordInput<>(resource, encoding,
                format, rowType, true, true);
        try {
            input.beforeRead(null);
            return input.readAll();
        } finally {
            IoHelper.safeCloseObject(input);
        }
    }

    public static List<Map<String, Object>> readCsv(IResource resource) {
        return readCsv(resource, null, CSVFormat.DEFAULT);
    }

    public static String format(Object value) {
        return CSVFormat.DEFAULT.format(value);
    }

    public static void print(Object value, Appendable out, boolean newRecord) throws IOException {
        CSVFormat.DEFAULT.print(value, out, newRecord);
    }

    public static <T> void writeCsv(IResource resource, CSVFormat format, List<String> headers, List<T> data) {
        CsvRecordOutput<T> output = new CsvRecordOutput<>(resource, null, format, true);
        output.setHeaders(headers);
        try {
            output.beginWrite(null);
            output.writeBatch(data);
            output.endWrite(null);
            output.flush();
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeCloseObject(output);
        }
    }

    public static BaseTable parseCsvToTable(String text) {
        StringReader reader = new StringReader(text);
        CsvRecordInput<String[]> input = new CsvRecordInput<>("text", reader, CSVFormat.DEFAULT,
                PredefinedGenericTypes.ARRAY_STRING_TYPE, false);

        BaseTable table = new BaseTable();
        List<String> headers = input.getHeaders();
        if (headers != null && !headers.isEmpty()) {
            BaseRow row = new BaseRow();
            for (int i = 0, n = headers.size(); i < n; i++) {
                BaseCell cell = new BaseCell();
                cell.setValue(headers.get(i));
                row.internalAddCell(cell);
            }
            table.addRow(row);
        }

        for (String[] record : input) {
            BaseRow row = new BaseRow();
            for (int i = 0, n = record.length; i < n; i++) {
                BaseCell cell = new BaseCell();
                cell.setValue(record[i]);
                row.internalAddCell(cell);
            }
            table.addRow(row);
        }
        return table;
    }

    public static <T> void writeCsvToWriter(Writer out, CSVFormat format, List<String> headers, List<T> data)
            throws IOException {
        CsvRecordOutput<T> output = new CsvRecordOutput<>(out, format);
        output.setHeaders(headers);
        output.beginWrite(null);
        output.writeBatch(data);
        output.endWrite(null);
        output.flush();
    }

    public static <T> String buildCsvFromData(CSVFormat format, List<String> headers, List<T> data) {
        StringWriter out = new StringWriter();
        try {
            writeCsvToWriter(out, format, headers, data);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
        return out.toString();
    }

    public static String buildCsvFromTable(CSVFormat format, ITableView table) {
        int rowCount = table.getRowCount();
        if (rowCount <= 0)
            return "";

        StringWriter out = new StringWriter();

        CsvRecordOutput<List<Object>> output = new CsvRecordOutput<>(out, format);
        List<String> headers = table.getRow(0).getCellValues(ICellView::getText);
        output.setHeaders(headers);
        try {
            output.beginWrite(null);
            for (int i = 1; i < rowCount; i++) {
                List<Object> values = table.getRow(i).getCellValues();
                output.write(values);
            }
            output.endWrite(null);
            output.flush();
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
        return out.toString();
    }
}