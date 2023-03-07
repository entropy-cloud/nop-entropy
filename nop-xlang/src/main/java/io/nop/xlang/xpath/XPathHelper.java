/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpath;

import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.adapter.XNodeAdapter;
import io.nop.xlang.xpath.adapter.XSelectorAdapter;
import io.nop.xlang.xpath.parse.XPathSelectorParser;

public class XPathHelper {
    public static IXSelector<XNode> parseXSelector(String path) {
        IXPathValueSelector<XNode, Object> selector = new XPathSelectorParser<XNode>().parseFromText(null, path);
        return new XSelectorAdapter<>(XNodeAdapter.INSTANCE, selector);
    }
}