/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.xml;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.cache.LocalCache;

import static io.nop.commons.cache.CacheConfig.newConfig;
import static io.nop.core.CoreConfigs.CFG_XPATH_CACHE_SIZE;
import static io.nop.core.CoreErrors.ERR_XML_NO_XPATH_PROVIDER;

public abstract class XPathProvider {
    static XPathProvider _INSTANCE;

    private LocalCache<String, IXSelector<XNode>> cache;

    protected XPathProvider(LocalCache<String, IXSelector<XNode>> cache) {
        this.cache = cache;
    }

    public XPathProvider() {
        cache = LocalCache.newCache("xpath-compile-cache", newConfig(CFG_XPATH_CACHE_SIZE.get()), this::compile);
    }

    public static XPathProvider instance() {
        if (_INSTANCE == null)
            throw new NopException(ERR_XML_NO_XPATH_PROVIDER);
        return _INSTANCE;
    }

    public static void registerInstance(XPathProvider provider) {
        _INSTANCE = provider;
    }

    public IXSelector<XNode> compileWithCache(String xpath) {
        return cache.get(xpath);
    }

    public abstract IXSelector<XNode> compile(String xpath);
}
