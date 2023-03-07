/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.decorator;

import io.nop.api.core.util.FutureHelper;
import io.nop.biz.BizConstants;
import io.nop.commons.cache.ICacheProvider;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.context.action.IServiceActionDecorator;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;

import static io.nop.biz.BizConstants.CACHE_DECORATOR_PRIORITY;

public class CacheEvictActionDecorator implements IServiceActionDecorator {
    private final ICacheProvider cacheProvider;
    private final String cacheName;
    private final IEvalAction cacheKeyExpr;

    public CacheEvictActionDecorator(ICacheProvider cacheProvider, String cacheName, IEvalAction cacheKeyExpr) {
        this.cacheProvider = cacheProvider;
        this.cacheName = cacheName;
        this.cacheKeyExpr = cacheKeyExpr;
    }

    @Override
    public int order() {
        return CACHE_DECORATOR_PRIORITY;
    }

    @Override
    public IServiceAction decorate(IServiceAction action) {
        return (request, selection, ctx) -> {
            Object result = action.invoke(request, selection, ctx);
            return FutureHelper.thenRun(result, () -> {
                IEvalScope scope = ctx.getEvalScope().newChildScope();
                scope.setLocalValue(null, BizConstants.ATTR_REQUEST, request);
                scope.setLocalValue(null, BizConstants.ATTR_REQUEST_HEADERS, ctx.getRequestHeaders());

                Object key = cacheKeyExpr.invoke(scope);
                if (key != null) {
                    cacheProvider.getCache(cacheName).remove(key);
                }
            });
        };
    }
}