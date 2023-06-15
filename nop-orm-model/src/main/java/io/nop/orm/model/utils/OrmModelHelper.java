package io.nop.orm.model.utils;

import io.nop.commons.util.CollectionHelper;
import io.nop.dao.dialect.IDialect;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmModelConstants;

import java.util.List;
import java.util.Map;

public class OrmModelHelper {

    public static int[] getPropIds(List<? extends IColumnModel> cols) {
        int[] ret = new int[cols.size()];
        for (int i = 0, n = cols.size(); i < n; i++) {
            ret[i] = cols.get(i).getPropId();
        }
        return ret;
    }


    public static String normalizeQuerySpace(String querySpace) {
        if (querySpace == null)
            return OrmModelConstants.DEFAULT_QUERY_SPACE;
        return querySpace;
    }

    public static String buildCollectionName(String entityName, String propName) {
        return entityName + '@' + propName;
    }

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
}
