package io.nop.orm.model.utils;

import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.OrmModelConstants;

import java.util.List;

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

}
