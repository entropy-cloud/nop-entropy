/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.bean;

import io.nop.commons.diff.IDiffValue;

public interface IBeanDiffer {
    IDiffValue beanDiff(Object src, Object target, BeanDiffOptions options);

    void applyDiff(Object bean, IDiffValue diffValue);
}