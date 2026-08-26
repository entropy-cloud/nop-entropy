/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.decorator;

import io.nop.biz.BizConstants;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.ICacheProvider;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.context.action.IServiceActionDecorator;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;

import java.util.concurrent.CompletionStage;

import static io.nop.biz.BizConstants.CACHE_DECORATOR_PRIORITY;

/**
 * action结果缓存装饰器。
 * <p>
 * 缓存命中与写入均按共享引用返回/保存，不做防御性拷贝（与Spring @Cacheable语义一致）。
 * 因此被缓存标注的action必须返回不可变对象（或调用方承诺不修改返回值），否则并发请求对返回值
 * （PageBean/List/Map等可变容器）的修改会污染缓存并影响所有后续命中请求。
 */
public class CacheActionDecorator implements IServiceActionDecorator {
    private final ICacheProvider cacheProvider;
    private final String cacheName;
    private final IEvalAction cacheKeyExpr;

    public CacheActionDecorator(ICacheProvider cacheProvider, String cacheName, IEvalAction cacheKeyExpr) {
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
            Object key = getCacheKey(request, ctx);
            // key为null时无法写缓存，直接回源执行
            if (key == null)
                return action.invoke(request, selection, ctx);

            ICache<Object, Object> cache = cacheProvider.getCache(cacheName);
            Object value = cache.get(key);
            // 命中缓存直接返回，不触发底层action
            if (value != null)
                return value;

            Object result = action.invoke(request, selection, ctx);
            return cacheResult(cache, key, result);
        };
    }

    private Object cacheResult(ICache<Object, Object> cache, Object key, Object result) {
        if (result instanceof CompletionStage) {
            // 异步结果在正常完成后再写入缓存
            return ((CompletionStage<Object>) result).thenApply(v -> {
                putIfNotNull(cache, key, v);
                return v;
            });
        }
        putIfNotNull(cache, key, result);
        return result;
    }

    private void putIfNotNull(ICache<Object, Object> cache, Object key, Object value) {
        // null值被视为缓存miss，因此null结果不写缓存（底层cache实现普遍不支持null值）
        if (value != null)
            cache.put(key, value);
    }

    private Object getCacheKey(Object request, IServiceContext ctx) {
        IEvalScope scope = ctx.getEvalScope().newChildScope();
        scope.setLocalValue(null, BizConstants.ATTR_REQUEST, request);
        scope.setLocalValue(null, BizConstants.ATTR_REQUEST_HEADERS, ctx.getRequestHeaders());

        return cacheKeyExpr.invoke(scope);
    }
}