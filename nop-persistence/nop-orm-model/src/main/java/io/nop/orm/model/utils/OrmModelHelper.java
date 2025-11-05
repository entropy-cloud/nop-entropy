/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model.utils;

import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
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
        return buildRelationName(entityName, propName);
    }

    public static String buildRelationName(String entityName, String propName) {
        return buildEntityPropKey(entityName, propName);
    }

    public static String buildEntityPropKey(String entityName, String propName) {
        return entityName + '@' + propName;
    }

    public static String buildEntityPropKey(IEntityPropModel propModel) {
        return buildEntityPropKey(propModel.getOwnerEntityModel().getName(), propModel.getName());
    }

    public static String buildRelationName(IEntityRelationModel rel) {
        return buildRelationName(rel.getOwnerEntityModel().getName(), rel.getName());
    }

}
