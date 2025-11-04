/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.tree;

public interface ITreeParentAdapter<T> {
    T getParent(T node);

    default T getRoot(T node) {
        T parent = getParent(node);
        if (parent == null)
            return node;
        return getRoot(parent);
    }
}