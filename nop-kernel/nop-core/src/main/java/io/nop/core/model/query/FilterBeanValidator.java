/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.query;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.util.IVariableScope;

public class FilterBeanValidator extends FilterBeanVisitor<Void> {
    protected void validateVarFilter(FilterOp filterOp, String name, ITreeBean filter, IVariableScope scope) {
        scope.getValue(name);
    }

    protected void validateValueVarName(String name, ITreeBean filter, IVariableScope scope) {
        scope.getValue(name);
    }

    @Override
    protected Void visitCompareOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        String name = getName(filter);
        validateVarFilter(filterOp, name, filter, scope);

        String valueName = getValueName(filter);
        if (valueName != null) {
            validateValueVarName(valueName, filter, scope);
        }
        return null;
    }

    @Override
    protected Void visitAssertOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        String name = getName(filter);
        validateVarFilter(filterOp, name, filter, scope);
        return null;
    }

    @Override
    protected Void visitBetweenOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        String name = getName(filter);
        validateVarFilter(filterOp, name, filter, scope);

        String minName = getMinName(filter);
        if (minName != null) {
            validateValueVarName(minName, filter, scope);
        }

        String maxName = getMaxName(filter);
        if (maxName != null) {
            validateValueVarName(maxName, filter, scope);
        }
        return null;
    }
}