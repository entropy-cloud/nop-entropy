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
import io.nop.core.context.IEvalContext;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IEntityDao;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.utils.ObjMetaPropHelper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.biz.BizConstants.METHOD_FIND_FIRST;

public class BizObjHelper {
    public static Object getProp(Object entity, IObjPropMeta propMeta, IEvalContext context) {
        return ObjMetaPropHelper.getPropValue(entity, propMeta, context);
    }

    /**
     * 2.3-C2-003（plan 2550 裁定 B）：computeIfAbsent 的映射 lambda 在缓存锁内执行，
     * biz invoke（校验链 + DB 查询）会把 I/O 拉进全局缓存监视器。改为双检形态：
     * getIfPresent 命中即返回；未命中锁外 invoke，再以 putIfAbsent 原子回写——竞争
     * 失败方丢弃自建结果（查询无资源需回收），以先回写方为准。
     */
    public static Object loadByProp(IBizObject bizObj, String propName, Object propValue, IServiceContext context) {
        String bizObjName = bizObj.getBizObjName();
        List<Object> key = Arrays.asList(propValue, propName, bizObjName, BizConstants.KEY_REF_ENTITY);

        // getIfPresent（非 get——ICache.get 的 default 带计算语义，会重入加载）
        Object cached = context.getCache().getIfPresent(key);
        if (cached != null)
            return cached;

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(propName, propValue));
        Map<String, Object> request = new HashMap<>();
        request.put(BizConstants.ARG_QUERY, query);
        Object loaded = bizObj.invoke(METHOD_FIND_FIRST, request, null, context);
        // 与原 computeIfAbsent 语义一致：null 结果不入缓存（每次重查），也避免缓存实现的 null value 约束
        if (loaded == null)
            return null;

        if (context.getCache().putIfAbsent(key, loaded)) {
            return loaded;
        }
        // 竞争失败方丢弃自建结果——DB 查询产物无资源需回收，以先回写方为准
        Object winner = context.getCache().getIfPresent(key);
        return winner != null ? winner : loaded;
    }

    public static <T extends IDaoEntity> IEntityDao<T> getDao(IBizObject bizObj, IServiceContext context) {
        return (IEntityDao<T>) bizObj.invoke(BizConstants.METHOD_GET_DAO, null, null, context);
    }
}
