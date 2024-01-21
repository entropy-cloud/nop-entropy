package io.nop.rpc.grpc.service;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Description;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.rpc.grpc.proto.codegen.GraphQLToApiModel;
import io.nop.rpc.model.ApiModel;
import io.nop.rpc.model.proto.ProtoFileGenerator;
import jakarta.inject.Inject;

@BizModel("DevDoc")
public class DevDocGrpcBizModel {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Description("为GraphQL服务生成对应Grpc proto描述")
    @BizQuery
    public String grpc() {
        ApiModel apiModel = new GraphQLToApiModel().transformToApi(graphQLEngine.getSchemaLoader());
        return new ProtoFileGenerator().generateProtoFile(apiModel);
    }
}