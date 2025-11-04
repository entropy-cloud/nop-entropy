/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.functional.select.evaluator;

import io.nop.commons.functional.select.IMatchEvaluator;

import java.util.List;

public abstract class AbstractCombiningEvaluator<E, C> implements IMatchEvaluator<E, C> {
    final List<IMatchEvaluator<E, C>> evaluators;

    AbstractCombiningEvaluator(List<IMatchEvaluator<E, C>> evaluators) {
        this.evaluators = evaluators;
    }
}