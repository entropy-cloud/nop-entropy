/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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