package io.nop.biz.api;

import io.nop.graphql.core.reflection.GraphQLBizModel;

import java.util.Set;

public interface ITenantBizModelProvider {
    Set<String> getTenantBizObjNames();

    GraphQLBizModel getTenantBizModel(String bizObjName);
}
