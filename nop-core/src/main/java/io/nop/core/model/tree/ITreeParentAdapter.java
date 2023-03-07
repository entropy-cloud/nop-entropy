/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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