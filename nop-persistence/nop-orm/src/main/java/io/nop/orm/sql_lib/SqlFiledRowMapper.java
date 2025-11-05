package io.nop.orm.sql_lib;

import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSetMeta;
import io.nop.dataset.IFieldMapper;
import io.nop.dataset.IRowMapper;

import java.util.Map;

public class SqlFiledRowMapper implements IRowMapper<Object> {
    private final SqlItemModel sqlItemModel;
    private final boolean caseInsensitive;
    private final IEvalScope scope;

    public SqlFiledRowMapper(SqlItemModel sqlItemModel, boolean caseInsensitive, IEvalScope scope) {
        this.sqlItemModel = sqlItemModel;
        this.caseInsensitive = caseInsensitive;
        this.scope = scope;
    }

    @Override
    public Map<String, Object> mapRow(IDataRow row, long rowNumber, IFieldMapper colMapper) {
        int columnCount = row.getFieldCount();
        Map<String, Object> mapOfColValues = createColumnMap(columnCount);
        IDataSetMeta meta = row.getMeta();
        for (int i = 0; i < columnCount; i++) {
            String key = getColumnKey(meta.getFieldName(i));
            Object obj = colMapper.getValue(row, i);
            if (key.indexOf('.') > 0) {
                BeanTool.setComplexProperty(mapOfColValues, key, obj);
            } else {
                mapOfColValues.put(key, obj);
            }
        }

        for (SqlFieldModel field : sqlItemModel.getFields()) {
            IEvalFunction expr = field.getComputeExpr();
            if (expr != null) {
                Object value = expr.call2(null, mapOfColValues, sqlItemModel, scope);
                String as = field.getAs();
                if (as == null)
                    as = field.getName();

                if (as.indexOf('.') > 0) {
                    BeanTool.setComplexProperty(mapOfColValues, as, value);
                } else {
                    mapOfColValues.put(field.getAs(), value);
                }
            }
        }
        return mapOfColValues;
    }

    protected Map<String, Object> createColumnMap(int columnCount) {
        if (caseInsensitive)
            return CollectionHelper.newCaseInsensitiveMap(columnCount);
        return CollectionHelper.newLinkedHashMap(columnCount);
    }

    protected String getColumnKey(String columnName) {
        SqlFieldModel field = sqlItemModel.getField(columnName);
        if (field != null) {
            if (field.getAs() != null)
                return field.getAs();
        }

        String name = columnName;
        if (sqlItemModel.isColNameCamelCase())
            name = StringHelper.camelCase(name, '_', false);
        
        return name;
    }
}