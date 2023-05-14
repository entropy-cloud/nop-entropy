/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.rowmapper;

import io.nop.commons.collections.CaseInsensitiveMap;
import io.nop.commons.util.StringHelper;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSetMeta;
import io.nop.dataset.IFieldMapper;
import io.nop.dataset.IRowMapper;

import java.util.HashMap;
import java.util.Map;

import static io.nop.commons.util.CollectionHelper.calcInitSize;

/**
 * @author canonical_entropy@163.com
 */
public class ColumnMapRowMapper implements IRowMapper<Map<String, Object>> {
    public static ColumnMapRowMapper INSTANCE = new ColumnMapRowMapper();

    public static ColumnMapRowMapper CAMEL_CASE = new ColumnMapRowMapper() {
        protected Map<String, Object> createColumnMap(int columnCount) {
            return new CaseInsensitiveMap<>(columnCount);
        }

        protected String getColumnKey(String columnName) {
            return StringHelper.camelCase(columnName, false);
        }
    };

    public static ColumnMapRowMapper CASE_SENSITIVE = new ColumnMapRowMapper() {
        protected Map<String, Object> createColumnMap(int columnCount) {
            return new HashMap<>(columnCount);
        }
    };

    public static ColumnMapRowMapper CASE_INSENSITIVE = new ColumnMapRowMapper() {
        protected Map<String, Object> createColumnMap(int columnCount) {
            return new CaseInsensitiveMap<>(columnCount);
        }
    };

    @Override
    public Map<String, Object> mapRow(IDataRow row, long rowNumber, IFieldMapper colMapper) {
        int columnCount = row.getFieldCount();
        Map<String, Object> mapOfColValues = createColumnMap(columnCount);
        IDataSetMeta meta = row.getMeta();
        for (int i = 0; i < columnCount; i++) {
            String key = getColumnKey(meta.getFieldName(i));
            Object obj = colMapper.getValue(row, i);
            mapOfColValues.put(key, obj);
        }
        return mapOfColValues;
    }

    protected Map<String, Object> createColumnMap(int columnCount) {
        return new CaseInsensitiveMap<>(calcInitSize(columnCount));
    }

    protected String getColumnKey(String columnName) {
        return columnName;
    }
}