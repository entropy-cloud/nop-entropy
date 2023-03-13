/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.MutableIntArray;

import static io.nop.core.CoreErrors.ARG_MAX_LEVEL;
import static io.nop.core.CoreErrors.ERR_HANDLER_EXCEED_MAX_NESTED_LEVEL;
import static io.nop.core.CoreErrors.ERR_HANDLER_STATE_INCOMPLETE;

/**
 * JsonHandler内部实现时使用的帮助类，用于记录当前处理状态。也可以用在其他类似需要进行递归处理的场景。
 */
public class NestedProcessingState extends MutableIntArray {
    private final int maxNestedLevel;

    public NestedProcessingState(int maxNestedLevel) {
        this.maxNestedLevel = maxNestedLevel;
    }

    public void push(int newTop) {
        if (size() >= maxNestedLevel)
            throw new NopException(ERR_HANDLER_EXCEED_MAX_NESTED_LEVEL).param(ARG_MAX_LEVEL,maxNestedLevel);
        super.push(newTop);
    }

    public void complete(int end) {
        int size = size();
        if (size > 1 || size == 1 && peek() != end) {
            throw new NopException(ERR_HANDLER_STATE_INCOMPLETE);
        }
        clear();
    }
}