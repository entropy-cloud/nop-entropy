package io.nop.graphql.core.utils;

import io.nop.api.core.util.Symbol;
import io.nop.graphql.core.ast.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GraphQLValueHelper {
    public static GraphQLValue buildValue(Object value) {
        if (value instanceof Collection) {
            GraphQLArrayValue ret = new GraphQLArrayValue();
            List<GraphQLValue> items = ((Collection<?>) value).stream()
                    .map(GraphQLValueHelper::buildValue).collect(Collectors.toList());
            ret.setItems(items);
            return ret;
        } else if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            GraphQLObjectValue ret = new GraphQLObjectValue();
            List<GraphQLPropertyValue> props = new ArrayList<>(map.size());
            map.forEach((k, v) -> {
                GraphQLPropertyValue prop = new GraphQLPropertyValue();
                prop.setName(k);
                prop.setValue(buildValue(v));
                props.add(prop);
            });
            ret.setProperties(props);
            return ret;
        } else if (value instanceof Symbol) {
            GraphQLVariable var = new GraphQLVariable();
            var.setName(((Symbol) value).getText());
            return var;
        } else {
            GraphQLLiteral ret = new GraphQLLiteral();
            ret.setValue(value);
            return ret;
        }
    }
}
