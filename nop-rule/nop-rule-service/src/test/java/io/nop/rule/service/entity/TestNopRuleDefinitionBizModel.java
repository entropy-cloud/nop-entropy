package io.nop.rule.service.entity;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
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
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.InputStream;
import java.util.Map;

@NopTestConfig(localDb = true, initDatabaseSchema = true)
public class TestNopRuleDefinitionBizModel extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @EnableSnapshot
    @Test
    public void testImport() {
        IResource resource = inputResource("decision-tree.rule.xlsx");
        InputStream is = resource.getInputStream();

        try {
            UploadRequestBean request = new UploadRequestBean();
            request.setFileName("decision-tree.rule.xlsx");
            request.setBizObjName(NopRuleDefinition.class.getSimpleName());
            request.setFieldName("importFile");
            request.setLength(1000);
            request.setMimeType("binary");
            request.setLastModified(1000);
            request.setInputStream(is);

            IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(GraphQLOperationType.mutation,
                    "NopFileStore__upload", ApiRequest.build(request));
            ApiResponse<?> response = graphQLEngine.executeRpc(ctx);
            // 每次生成的下载路径都是一个随机值，所以需要注册为变量
            setVar("downloadPath", BeanTool.getComplexProperty(response, "data.value"));
            output("upload-result.json5", response);
        } finally {
            IoHelper.safeCloseObject(is);
        }

        ApiRequest<?> request = request("request.json5", Map.class);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(GraphQLOperationType.mutation,
                "NopRuleDefinition__save", request);
        ApiResponse<?> response = graphQLEngine.executeRpc(ctx);
        output("response.json5", response);
    }
}
