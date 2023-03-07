/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.type;

public enum GenericTypeKind {
    RAW_TYPE,

    RAW_TYPE_REF,

    PARAMETERIZED_TYPE,

    FUNCTION,

    TYPE_VARIABLE,

    TYPE_VARIABLE_BOUND,

    WILDCARD,

    ARRAY,

    TUPLE,

    UNION,

    INTERSECTION,

    STRUCT,

    CLASS_TYPE;

    public boolean isVariable() {
        return this == TYPE_VARIABLE || this == TYPE_VARIABLE_BOUND;
    }

    public boolean isComposite() {
        return this == TUPLE || this == UNION || this == INTERSECTION;
    }
}
