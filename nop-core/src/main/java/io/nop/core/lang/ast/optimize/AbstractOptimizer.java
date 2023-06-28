/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.ast.optimize;

import io.nop.api.core.util.IFreezable;
import io.nop.core.lang.ast.ASTNode;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractOptimizer<T extends IFreezable, C> implements IOptimizer<T, C> {
    private int changeCount;
    private boolean inplaceUpdate;

    public int getChangeCount() {
        return changeCount;
    }

    public void setChangeCount(int changeCount) {
        this.changeCount = changeCount;
    }

    public void incChangeCount() {
        this.changeCount++;
    }

    public boolean isInplaceUpdate() {
        return inplaceUpdate;
    }

    public void setInplaceUpdate(boolean inplaceUpdate) {
        this.inplaceUpdate = inplaceUpdate;
    }

    public boolean shouldClone(T ret, T node) {
        if (ret != node)
            return false;

        if (node.frozen())
            return true;

        return !isInplaceUpdate();
    }

    public <N extends T> List<N> optimizeList(List<N> list, boolean ignoreNullItem, C context) {
        if (list == null || list.isEmpty())
            return list;

        List<N> ret = list;
        for (int i = 0, n = list.size(); i < n; i++) {
            N item = list.get(i);
            if (item != null) {
                N opt = (N) optimize(item, context);
                if (opt != item) {
                    if (ret == list) {
                        ret = new ArrayList<>(list.size());
                        for (int j = 0; j < i; j++) {
                            ret.add(list.get(j));
                        }
                    }
                    if (!ignoreNullItem || opt != null) {
                        ret.add(opt);
                    }
                } else if (ret != list) {
                    ret.add(item);
                }
            } else {
                if (ignoreNullItem) {
                    if (ret == list) {
                        ret = new ArrayList<>(list.size());
                        for (int j = 0; j < i; j++) {
                            ret.add(list.get(j));
                        }
                    }
                }
            }
        }
        return ret;
    }

    public void clearParent(List<? extends ASTNode<?>> children) {
        if (children != null) {
            for (ASTNode<?> child : children) {
                child.setASTParent(null);
            }
        }
    }
}
