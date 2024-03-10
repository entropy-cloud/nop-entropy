/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpath.selector;

import io.nop.commons.functional.select.ISelector;
import io.nop.commons.functional.select.selector.CompositeSelector;
import io.nop.xlang.xpath.IXPathContext;
import io.nop.xlang.xpath.IXPathElementSelector;
import io.nop.xlang.xpath.IXPathValueSelector;

import java.util.List;

public class CompositeNodeSelector<E> extends CompositeSelector<E, IXPathContext<E>>
        implements IXPathElementSelector<E> {
    private static final long serialVersionUID = 310227343479923722L;

    public CompositeNodeSelector(List<? extends ISelector<E, IXPathContext<E>, E>> iSelectors) {
        super(iSelectors);
    }

    @Override
    public boolean selectFromRoot() {
        List<? extends ISelector<E, IXPathContext<E>, E>> selectors = getSelectors();
        if (selectors.isEmpty())
            return false;
        IXPathValueSelector selector = (IXPathValueSelector) selectors.get(0);
        return selector.selectFromRoot();
    }

    public String toString() {
        List<? extends ISelector<E, IXPathContext<E>, E>> selectors = getSelectors();
        StringBuilder sb = new StringBuilder();
        boolean endsWithSlash = false;
        for (int i = 0, n = selectors.size(); i < n; i++) {
            ISelector<E, IXPathContext<E>, E> selector = selectors.get(i);
            String text = selector.toString();
            if (i >= 1) {
                if (text.charAt(0) != '/' && !endsWithSlash) {
                    sb.append('/');
                }
            }
            sb.append(text);
            endsWithSlash = text.charAt(text.length() - 1) == '/';
        }
        return sb.toString();
    }
}