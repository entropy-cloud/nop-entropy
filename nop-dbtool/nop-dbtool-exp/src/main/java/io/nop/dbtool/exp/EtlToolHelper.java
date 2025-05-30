package io.nop.dbtool.exp;

import io.nop.commons.util.CollectionHelper;
import io.nop.dao.dialect.IDialect;
import io.nop.dataset.IRowMapper;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.dataset.impl.BinderMapFieldMapper;
import io.nop.dataset.rowmapper.ColRowMapper;
import io.nop.dataset.rowmapper.ColumnMapRowMapper;
import io.nop.dbtool.exp.config.IFieldConfig;
import io.nop.dbtool.exp.config.TableFieldConfig;

import java.util.List;
import java.util.Map;

public class EtlToolHelper {

    public static Map<String, IDataParameterBinder> getColBinders(List<? extends IFieldConfig> cols, boolean useFrom,
                                                                  IDialect dialect) {
        Map<String, IDataParameterBinder> binders = CollectionHelper.newCaseInsensitiveMap(cols.size());

        for (IFieldConfig col : cols) {
            String key = useFrom ? col.getFrom() : col.getName();
            if (key == null)
                key = col.getName();

            IDataParameterBinder binder = dialect.getDataParameterBinder(col.getStdDataType(), col.getStdSqlType());
            if (binder == null)
                continue;
            binders.put(key, binder);
        }
        return binders;
    }

    public static IRowMapper<Map<String, Object>> buildRowMapper(List<TableFieldConfig> fields, boolean useFrom,
                                                                 IDialect dialect) {
        Map<String, IDataParameterBinder> binders = getColBinders(fields, useFrom, dialect);
        return new ColRowMapper<>(ColumnMapRowMapper.CASE_INSENSITIVE, new BinderMapFieldMapper(binders));
    }
}
