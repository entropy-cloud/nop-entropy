/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpath;

import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.adapter.XNodeAdapter;
import io.nop.xlang.xpath.adapter.XSelectorAdapter;
import io.nop.xlang.xpath.parse.XPathSelectorParser;

import static io.nop.commons.cache.CacheConfig.newConfig;

public class XPathHelper {
    /**
     * 模板中同一 xpath 字符串会被多处/多次编译，与 JPath.compileWithCache 一致按文本缓存编译产物。
     * selector 为无状态求值结构，跨调用共享安全
     */
    static final ICache<String, IXSelector<XNode>> cache = LocalCache.newCache("xpath-compile-cache",
            newConfig(1000), XPathHelper::compileXSelector);

    public static IXSelector<XNode> parseXSelector(String path) {
        return cache.get(path);
    }

    static IXSelector<XNode> compileXSelector(String path) {
        IXPathValueSelector<XNode, Object> selector = new XPathSelectorParser<XNode>().parseFromText(null, path);
        return new XSelectorAdapter<>(XNodeAdapter.INSTANCE, selector);
    }
}
