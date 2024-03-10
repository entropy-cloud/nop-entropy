/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import io.nop.graphql.core.ast._gen._GraphQLDirective;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GraphQLDirective extends _GraphQLDirective {

    public void addArgument(String name, GraphQLValue value) {
        GraphQLArgument arg = new GraphQLArgument();
        arg.setName(name);
        arg.setValue(value);
        makeArguments().add(arg);
    }

    public GraphQLArgument getArg(String name) {
        List<GraphQLArgument> args = this.getArguments();
        if (args == null || args.isEmpty())
            return null;
        for (GraphQLArgument arg : args) {
            if (arg.getName().equals(name))
                return arg;
        }
        return null;
    }

    public Object getArgValue(String name, Map<String, Object> vars) {
        GraphQLArgument arg = getArg(name);
        if (arg == null)
            return null;
        return arg.getValue().buildValue(vars);
    }

    public Object getArgValue(String name) {
        return getArgValue(name, Collections.emptyMap());
    }

    public void setArgValue(String name, Object value) {
        GraphQLArgument arg = getArg(name);
        if (arg == null) {
            arg = new GraphQLArgument();
            arg.setName(name);
            makeArguments().add(arg);
        }
        arg.setValue(GraphQLLiteral.valueOf(null, value));
    }

    public Map<String, Object> buildArgs(Map<String, Object> vars) {
        List<GraphQLArgument> args = this.getArguments();
        if (args == null || args.isEmpty())
            return Collections.emptyMap();

        Map<String, Object> map = new LinkedHashMap<>();
        for (GraphQLArgument arg : args) {
            map.put(arg.getName(), arg.getValue().buildValue(vars));
        }
        return map;
    }
}