/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.model;

import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.auth.core.AuthCoreConstants;
import io.nop.auth.core.model._gen._RoleDataAuthModel;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.IXNodeGenerator;
import io.nop.core.lang.xml.XNode;

public class RoleDataAuthModel extends _RoleDataAuthModel implements Comparable<RoleDataAuthModel> {
    public RoleDataAuthModel() {

    }

    @Override
    public int compareTo(RoleDataAuthModel o) {
        return Integer.compare(getPriority(), o.getPriority());
    }

    public void mergeChecker(IEvalPredicate checker) {
        if (checker == null)
            return;

        if (this.getCheck() == null) {
            this.setCheck(checker);
        } else {
            this.setCheck(getCheck().and(checker));
        }
    }

    public void mergeFilter(IXNodeGenerator filter) {
        if (filter == null)
            return;

        if (getFilter() == null) {
            setFilter(filter);
        } else {
            setFilter(getFilter().both(filter));
        }
    }

    public XNode generateFilter(IEvalScope scope) {
        XNode filter = XNode.make(FilterBeanConstants.FILTER_OP_AND);
        if (getFilter() != null) {
            scope.setLocalValue(AuthCoreConstants.VAR_FILTER, filter);
            XNode node = getFilter().generateNode(scope);
            if (node != null) {
                if (node.isDummyNode()) {
                    filter.appendChildren(node.detachChildren());
                } else {
                    filter.appendChild(node);
                }
            }
        }
        return filter;
    }
}
