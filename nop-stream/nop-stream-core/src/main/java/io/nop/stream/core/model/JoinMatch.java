/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * WI13: one equi-join output — a cross-side pair, or an outer completion where the
 * missing side is {@code null} (D1=(a) final-value semantics: pairs and completions
 * emit once, no retract markers; a completion is final even if a late partner would
 * have matched — the engine's established fire-and-update late-data behavior).
 */
public class JoinMatch<L, R> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final L left;
    private final R right;

    public JoinMatch(L left, R right) {
        this.left = left;
        this.right = right;
    }

    /** the left-side payload, or null for a RIGHT/FULL completion of the left side. */
    public L getLeft() {
        return left;
    }

    /** the right-side payload, or null for a LEFT/FULL completion of the right side. */
    public R getRight() {
        return right;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JoinMatch)) {
            return false;
        }
        JoinMatch<?, ?> that = (JoinMatch<?, ?>) o;
        return Objects.equals(left, that.left) && Objects.equals(right, that.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right);
    }

    @Override
    public String toString() {
        return "J|" + left + "|" + right;
    }
}
