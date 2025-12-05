/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import io.nop.graphql.core.ast._gen._GraphQLObjectValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.commons.util.CollectionHelper.toNotNull;

public class GraphQLObjectValue extends _GraphQLObjectValue {
    public boolean containsVariable() {
        for (GraphQLPropertyValue prop : toNotNull(getProperties())) {
            if (prop.getValue().containsVariable())
                return true;
        }
        return false;
    }

    @Override
    public Object buildValue(Map<String, Object> vars) {
        List<GraphQLPropertyValue> props = this.getProperties();
        if (props == null || props.isEmpty())
            return Collections.emptyMap();

        Map<String, Object> ret = new LinkedHashMap<>();
        props.forEach(prop -> {
            ret.put(prop.getName(), prop.getValue().buildValue(vars));
        });
        return ret;
    }
}