/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpath;

import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.XPathProvider;

public class DefaultXPathProvider extends XPathProvider {
    @Override
    public IXSelector<XNode> compile(String xpath) {
        return XPathHelper.parseXSelector(xpath);
    }
}
