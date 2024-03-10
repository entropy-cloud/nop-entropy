/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.core.migration.operations;

import io.nop.autotest.core.data.AutoTestCaseData;
import io.nop.autotest.core.migration.MigrationOperation;
import io.nop.autotest.core.migration.TableMigrationConfig;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.record.csv.CsvHelper;
import io.nop.core.resource.record.csv.CsvRecordInput;
import io.nop.core.type.PredefinedGenericTypes;
import org.apache.commons.csv.CSVFormat;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class TransformTableMigration extends MigrationOperation implements TableMigrationConfig {
    private final String tableName;
    private Map<String, String> renameCols;
    private List<BiConsumer<File, Map<String, Object>>> rowTransformers;
    private List<BiConsumer<File, List<Map<String, Object>>>> tableTransformers;

    public TransformTableMigration(String tableName) {
        this.tableName = tableName;
    }

    public TransformTableMigration(String tableName,
                                   Map<String, String> renameCols,
                                   List<BiConsumer<File, Map<String, Object>>> rowTransformers,
                                   List<BiConsumer<File, List<Map<String, Object>>>> tableTransformers) {
        this.tableName = tableName;
        this.renameCols = renameCols;
        this.rowTransformers = rowTransformers;
        this.tableTransformers = tableTransformers;
    }

    @Override
    public TableMigrationConfig renameCol(String oldCol, String newCol) {
        if (renameCols == null)
            renameCols = new LinkedHashMap<>();
        renameCols.put(oldCol, newCol);
        return this;
    }

    @Override
    public TableMigrationConfig deleteCol(String col) {
        return renameCol(col,null);
    }

    @Override
    public TableMigrationConfig transformCol(String col, Function<Object, Object> transformer) {
        return transformRow((file, row) -> {
            if (row.containsKey(col)) {
                Object value = row.get(col);
                value = transformer.apply(value);
                row.put(col, value);
            }
        });
    }

    @Override
    public TableMigrationConfig transformRow(BiConsumer<File, Map<String, Object>> transformer) {
        if (rowTransformers == null)
            rowTransformers = new ArrayList<>();
        rowTransformers.add(transformer);
        return this;
    }

    @Override
    public TableMigrationConfig transformTable(BiConsumer<File, List<Map<String, Object>>> transformer) {
        if (tableTransformers == null)
            tableTransformers = new ArrayList<>();
        tableTransformers.add(transformer);
        return this;
    }

    @Override
    public void run(AutoTestCaseData caseData) {
        forTable(caseData, tableName, file -> {
            IResource resource = new FileResource(file);
            CsvRecordInput<Map<String, Object>> input = new CsvRecordInput<>(resource, null, CSVFormat.DEFAULT,
                    PredefinedGenericTypes.MAP_STRING_ANY_TYPE, null, true, true);
            List<String> headers;
            List<Map<String, Object>> rows;
            try {
                headers = input.getMeta().getHeaders();
                headers = renameHeaders(headers);
                rows = input.readAll();

                for (Map<String, Object> row : rows) {
                    transformHeaders(row);
                    transformRow(file, row);
                }

                transformRows(file, rows);
            } finally {
                IoHelper.safeCloseObject(input);
            }

            CsvHelper.writeCsv(resource, CSVFormat.DEFAULT, headers, rows);
        });
    }

    private List<String> renameHeaders(List<String> headers) {
        if (renameCols != null) {
            List<String> ret = new ArrayList<>();
            for (String header : headers) {
                String newHeader = renameCols.get(header);
                if (!StringHelper.isEmpty(newHeader)) {
                    ret.add(newHeader);
                } else if (!renameCols.containsKey(header)) {
                    ret.add(header);
                }
            }
            return ret;
        }
        return headers;
    }

    private void transformHeaders(Map<String, Object> row) {
        if (renameCols != null) {
            for (Map.Entry<String, String> entry : renameCols.entrySet()) {
                String name = entry.getKey();
                if (row.containsKey(name)) {
                    Object value = row.get(name);
                    row.put(entry.getValue(), value);
                }
            }
        }
    }

    private void transformRow(File file, Map<String, Object> row) {
        if (rowTransformers != null) {
            for (BiConsumer<File, Map<String, Object>> action : rowTransformers) {
                action.accept(file, row);
            }
        }
    }

    private void transformRows(File file, List<Map<String, Object>> rows) {
        if (tableTransformers != null) {
            for (BiConsumer<File, List<Map<String, Object>>> action : tableTransformers) {
                action.accept(file, rows);
            }
        }
    }
}