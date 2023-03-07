/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.decorator;

import io.nop.api.core.annotations.cache.Cache;
import io.nop.api.core.annotations.cache.CacheEvict;
import io.nop.api.core.annotations.cache.CacheEvicts;
import io.nop.biz.model.BizActionModel;
import io.nop.biz.model.BizCacheModel;
import io.nop.commons.cache.ICacheProvider;
import io.nop.core.context.action.IServiceActionDecorator;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.reflect.IFunctionModel;
import io.nop.xlang.api.XLang;

import java.util.List;

public class CacheActionDecoratorCollector implements IActionDecoratorCollector {
    private final ICacheProvider cacheProvider;

    public CacheActionDecoratorCollector(ICacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    @Override
    public void collectDecorator(IFunctionModel funcModel, List<IServiceActionDecorator> decorators) {
        Cache cache = funcModel.getAnnotation(Cache.class);
        if (cache != null) {
            String cacheName = cache.cacheName();
            IEvalAction cacheKeyExpr = XLang.newCompileTool().compileSimpleExpr(null, cache.cacheKey());
            decorators.add(new CacheActionDecorator(cacheProvider, cacheName, cacheKeyExpr));
        }

        CacheEvicts cacheEvicts = funcModel.getAnnotation(CacheEvicts.class);
        if (cacheEvicts != null) {
            for (CacheEvict cacheEvict : cacheEvicts.value()) {
                String cacheName = cacheEvict.cacheName();
                IEvalAction cacheKeyExpr = XLang.newCompileTool().compileSimpleExpr(null, cacheEvict.cacheKey());
                decorators.add(new CacheEvictActionDecorator(cacheProvider, cacheName, cacheKeyExpr));
            }
        }
    }

    @Override
    public void collectDecorator(BizActionModel actionModel, List<IServiceActionDecorator> decorators) {
        BizCacheModel cacheModel = actionModel.getCache();
        if (cacheModel != null) {
            decorators.add(
                    new CacheActionDecorator(cacheProvider, cacheModel.getCacheName(), cacheModel.getCacheKeyExpr()));
        }
    }
}
