/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.functional.visit;

import java.util.Collection;

public abstract class AbstractVisitor<T> implements IVisitor<T> {

    protected void visitChildren(Collection<? extends T> children) {
        if (children != null) {
            for (T child : children) {
                visitChild(child);
            }
        }
    }

    protected void visitChild(T child) {
        if (child != null)
            visit(child);
    }
}
