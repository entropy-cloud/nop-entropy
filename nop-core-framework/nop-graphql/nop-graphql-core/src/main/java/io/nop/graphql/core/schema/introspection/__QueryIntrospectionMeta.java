package io.nop.graphql.core.schema.introspection;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLObject;

@GraphQLObject
@DataBean
public class __QueryIntrospectionMeta {
    private __Schema __schema;
    private __Type __type;

    public __Schema get__schema() {
        return __schema;
    }

    public void set__schema(__Schema __schema) {
        this.__schema = __schema;
    }

    public __Type get__type() {
        return __type;
    }

    public void set__type(__Type __type) {
        this.__type = __type;
    }
}