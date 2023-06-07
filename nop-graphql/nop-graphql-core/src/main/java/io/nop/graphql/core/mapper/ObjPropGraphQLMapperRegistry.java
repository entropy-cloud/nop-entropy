package io.nop.graphql.core.mapper;

import io.nop.xlang.xmeta.mapper.ObjPropMapperRegistry;

public class ObjPropGraphQLMapperRegistry {
    static final ObjPropMapperRegistry _instance = new ObjPropMapperRegistry();

    public static ObjPropMapperRegistry instance() {
        return _instance;
    }
}
