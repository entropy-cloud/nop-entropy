/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.crud;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizObject;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IEntityDao;
import io.nop.xlang.xmeta.IObjPropMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static io.nop.biz.BizConstants.METHOD_FIND_FIRST;

public class BizObjHelper {
    public static Object getProp(Object entity, IObjPropMeta propMeta, IServiceContext context) {
        if (propMeta.getGetter() != null) {
            IEvalAction getter = propMeta.getGetter();
            IEvalScope scope = context.getEvalScope().newChildScope();
            scope.setLocalValue(null, BizConstants.VAR_ENTITY, entity);
            return getter.invoke(scope);
        }

        String mapTo = propMeta.getMapToProp();
        if (mapTo != null)
            return BeanTool.instance().getProperty(entity, mapTo);
        return BeanTool.instance().getProperty(entity, propMeta.getName());
    }

    public static Object loadByProp(IBizObject bizObj, String propName, Object propValue, IServiceContext context) {
        String bizObjName = bizObj.getBizObjName();
        Object refEntity = context.getCache().computeIfAbsent(Arrays.asList(propValue, propName, bizObjName, BizConstants.KEY_REF_ENTITY), k -> {
            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.eq(propName, propValue));
            Map<String, Object> request = new HashMap<>();
            request.put(BizConstants.ARG_QUERY, query);
            return bizObj.invoke(METHOD_FIND_FIRST, request, null, context);
        });
        return refEntity;
    }

    public static <T extends IDaoEntity> IEntityDao<T> getDao(IBizObject bizObj, IServiceContext context) {
        return (IEntityDao<T>) bizObj.invoke(BizConstants.METHOD_GET_DAO, null, null, context);
    }
}
