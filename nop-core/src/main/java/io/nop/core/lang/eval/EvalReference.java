/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.eval;

public final class EvalReference implements IEvalReference<Object> {
    private Object value;

    public EvalReference(Object value) {
        this.value = value;
    }

    public String toString() {
        return "&" + value;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public static Object deRef(Object value) {
        if (value instanceof EvalReference) {
            return ((EvalReference) value).getValue();
        }
        return value;
    }
}