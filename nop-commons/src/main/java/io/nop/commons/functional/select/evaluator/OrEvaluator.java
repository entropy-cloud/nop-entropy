/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.functional.select.evaluator;

import io.nop.commons.functional.select.IMatchEvaluator;
import io.nop.commons.util.StringHelper;

import java.util.List;

public final class OrEvaluator<E, C> extends AbstractCombiningEvaluator<E, C> {

    public OrEvaluator(List<IMatchEvaluator<E, C>> evaluators) {
        super(evaluators);
    }

    @Override
    public boolean matches(E node, C context) {
        for (int i = 0; i < evaluators.size(); i++) {
            IMatchEvaluator s = evaluators.get(i);
            if (s.matches(node, context))
                return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return StringHelper.join(evaluators, " or ");
    }
}
