/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.service.entity;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.commons.util.IoHelper;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.file.core.UploadRequestBean;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.rule.dao.entity.NopRuleDefinition;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

@NopTestConfig(localDb = true, initDatabaseSchema = true)
@NopTestProperty(name = "nop.file.store-dir", value = "./target")
@NopTestProperty(name = "nop.orm.dao-resource-check-interval", value = "0")
public class TestNopRuleDefinitionBizModel extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @EnableSnapshot
    @Test
    public void testImport() {
        runWithModelFile("decision-tree.rule.xlsx");
    }

    @EnableSnapshot
    @Test
    public void testDecisionMatrix() {
        runWithModelFile("decision-matrix.rule.xlsx");
    }

    @EnableSnapshot
    @Test
    public void testUpdateByFile() {
        runWithModelFile("decision-tree.rule.xlsx");

        ApiResponse<?> response = uploadFile("decision-tree.rule.xlsx");
        // 每次生成的下载路径都是一个随机值，所以需要注册为变量
        setVar("downloadPath2", BeanTool.getComplexProperty(response, "data.value"));
        output("upload-result2.json5", response);

        ApiRequest<?> request = request("request3-update.json5", Map.class);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(GraphQLOperationType.mutation,
                "NopRuleDefinition__update", request);
        response = graphQLEngine.executeRpc(ctx);
        output("response3-update.json5", response);

        request = request("request4-exec.json5", Map.class);
        ctx = graphQLEngine.newRpcContext(GraphQLOperationType.mutation, "RuleService__executeRule", request);
        response = graphQLEngine.executeRpc(ctx);
        output("response4-exec.json5", response);
    }

    ApiResponse<?> uploadFile(String fileName) {
        IResource resource = inputResource(fileName);
        InputStream is = resource.getInputStream();

        try {
            UploadRequestBean request = new UploadRequestBean();
            request.setFileName(fileName);
            request.setBizObjName(NopRuleDefinition.class.getSimpleName());
            request.setFieldName("importFile");
            request.setLength(1000);
            request.setMimeType("binary");
            request.setLastModified(1000);
            request.setInputStream(is);

            IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(GraphQLOperationType.mutation,
                    "NopFileStore__upload", ApiRequest.build(request));
            ApiResponse<?> response = graphQLEngine.executeRpc(ctx);
            return response;
        } finally {
            IoHelper.safeCloseObject(is);
        }
    }

    void runWithModelFile(String fileName) {
        ApiResponse<?> response = uploadFile(fileName);

        // 每次生成的下载路径都是一个随机值，所以需要注册为变量
        setVar("downloadPath", BeanTool.getComplexProperty(response, "data.value"));
        output("upload-result.json5", response);

        ApiRequest<?> request = request("request.json5", Map.class);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(GraphQLOperationType.mutation,
                "NopRuleDefinition__save", request);
        response = graphQLEngine.executeRpc(ctx);
        output("response.json5", response);

        request = request("request2.json5", Map.class);
        ctx = graphQLEngine.newRpcContext(GraphQLOperationType.mutation, "RuleService__executeRule", request);
        response = graphQLEngine.executeRpc(ctx);
        output("response2.json5", response);
    }
}
