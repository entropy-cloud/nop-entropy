package io.nop.rpc.grpc.service;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.rpc.grpc.proto.codegen.GraphQLToApiModel;
import io.nop.rpc.model.ApiModel;
import jakarta.inject.Inject;

@Locale("zh-CN")
@BizModel("DevModel")
public class DevModelGrpcBizModel {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Description("为GraphQL服务生成对应ApiModel描述")
    @BizQuery
    public ApiModel apiModel() {
        ApiModel apiModel = new GraphQLToApiModel().transformToApi(graphQLEngine.getSchemaLoader());
        return apiModel;
    }
}