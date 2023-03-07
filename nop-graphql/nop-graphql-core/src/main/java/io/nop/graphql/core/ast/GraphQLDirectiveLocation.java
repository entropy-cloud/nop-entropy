/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.ast;

import io.nop.api.core.annotations.core.StaticFactoryMethod;

import java.util.HashMap;
import java.util.Map;

public enum GraphQLDirectiveLocation {
    QUERY, MUTATION, FIELD, FRAGMENT_DEFINITION, FRAGMENT_SPREAD, INLINE_FRAGMENT,
    //
    // schema SDL places
    //
    SCHEMA, SCALAR, OBJECT, FIELD_DEFINITION, ARGUMENT_DEFINITION, INTERFACE, UNION, ENUM, ENUM_VALUE, INPUT_OBJECT,
    INPUT_FIELD_DEFINITION;

    private static final Map<String, GraphQLDirectiveLocation> s_map = new HashMap<>();

    static {
        for (GraphQLDirectiveLocation value : values()) {
            s_map.put(value.name(), value);
        }
    }

    @StaticFactoryMethod
    public static GraphQLDirectiveLocation fromText(String text) {
        return s_map.get(text);
    }
}
