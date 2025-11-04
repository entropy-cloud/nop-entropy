/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.functional.select.evaluator;

import io.nop.commons.functional.select.IMatchEvaluator;

public class NotEvaluator<E, C> implements IMatchEvaluator<E, C> {
    private final IMatchEvaluator<E, C> evaluator;

    public NotEvaluator(IMatchEvaluator<E, C> evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public boolean matches(E element, C context) {
        return !evaluator.matches(element, context);
    }

    public String toString() {
        return "not(" + evaluator + ")";
    }
}