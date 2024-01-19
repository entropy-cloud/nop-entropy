package io.nop.orm.eql.utils;

import io.nop.commons.util.CollectionHelper;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.SQLDataType;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;

import java.util.List;
import java.util.Map;

public class OrmDialectHelper {

    public static Map<String, IDataParameterBinder> getEntityColBinders(IEntityModel entityModel, IDialect dialect,
                                                                        boolean useCodeKey) {
        return getColBinders(entityModel.getColumns(), dialect, useCodeKey);
    }

    public static Map<String, IDataParameterBinder> getPkColBinders(IEntityModel entityModel, IDialect dialect,
                                                                    boolean useCodeKey) {
        return getColBinders(entityModel.getPkColumns(), dialect, useCodeKey);
    }

    public static Map<String, IDataParameterBinder> getColBinders(List<? extends IColumnModel> cols, IDialect dialect,
                                                                  boolean useCodeKey) {
        Map<String, IDataParameterBinder> binders = CollectionHelper.newCaseInsensitiveMap(cols.size());

        for (IColumnModel col : cols) {
            String key = useCodeKey ? col.getCode() : col.getName();
            IDataParameterBinder binder = dialect.getDataParameterBinder(col.getStdDataType(), col.getStdSqlType());
            binders.put(key, binder);
        }
        return binders;
    }

    public static SQLDataType getSqlType(IColumnModel col, IDialect dialect) {
        Integer precision = col.getPrecision();
        if (precision == null)
            precision = -1;
        Integer scale = col.getScale();
        if (scale == null)
            scale = -1;

        SQLDataType sqlType = dialect.stdToNativeSqlType(col.getStdSqlType(), precision, scale);
        return sqlType;
    }
}
