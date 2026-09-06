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
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.commons.util.IoHelper;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.file.core.UploadRequestBean;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.ooxml.xlsx.util.ExcelHelper;
import io.nop.rule.dao.entity.NopRuleDefinition;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
@NopTestProperty(name = "nop.file.store-dir", value = "./target")
@NopTestProperty(name = "nop.orm.dao-resource-check-interval", value = "0")
public class TestNopRuleDefinitionBizModel extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Test
    public void testImport() {
        runWithModelFile("decision-tree.rule.xlsx");
    }

    @Test
    public void testDecisionMatrix() {
        runWithModelFile("decision-matrix.rule.xlsx");
    }

    /**
     * 同一父节点下两个分支的predicate完全相同时（复制粘贴常见失误），
     * 保存时不允许后一个分支覆盖前一个分支的输出与子树。
     * 这里把决策树第4行的条件从Winter改为Fall，与第3行构成重复predicate，
     * 修复后第一个Fall分支（&lt;= 8 输出 Spareribs）应保留，执行结果为Spareribs；
     * 修复前该分支被第二个Fall分支覆盖，执行结果为Roastbeef。
     */
    @Test
    @EnableSnapshot(checkOutput = false)
    public void testImportDuplicatePredicate() {
        IResource template = inputResource("decision-tree.rule.xlsx");
        ExcelWorkbook wk = ExcelHelper.parseExcel(template);
        ExcelSheet ruleSheet = wk.requireSheet("Rule");
        ExcelCell conditionCell = (ExcelCell) ruleSheet.getTable().getCell(3, 1);
        conditionCell.setValue("Fall");

        File outFile = new File("./target/duplicate-predicate.rule.xlsx");
        ExcelHelper.saveExcel(new FileResource(outFile), wk);

        ApiResponse<?> response = uploadResource("duplicate-predicate.rule.xlsx", new FileResource(outFile));
        String downloadPath = (String) BeanTool.getComplexProperty(response, "data.value");

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("ruleName", "test-dup");
        entity.put("ruleGroup", "default");
        entity.put("ruleVersion", 1);
        entity.put("displayName", "Dup Test");
        entity.put("status", 1);
        entity.put("importFile", downloadPath);

        Map<String, Object> saveData = new LinkedHashMap<>();
        saveData.put("data", entity);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(GraphQLOperationType.mutation,
                "NopRuleDefinition__save", ApiRequest.build(saveData));
        response = graphQLEngine.executeRpc(ctx);
        assertTrue(response.isOk(), "save should succeed: " + response);

        Map<String, Object> execData = new LinkedHashMap<>();
        execData.put("ruleName", "test-dup");
        execData.put("ruleVersion", 1);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("season", "Fall");
        inputs.put("guestCount", 3);
        execData.put("inputs", inputs);
        ctx = graphQLEngine.newRpcContext(GraphQLOperationType.mutation,
                "RuleService__executeRule", ApiRequest.build(execData));
        response = graphQLEngine.executeRpc(ctx);
        assertTrue(response.isOk(), "executeRule should succeed: " + response);
        assertEquals(Boolean.TRUE, BeanTool.getComplexProperty(response, "data.ruleMatch"));
        assertEquals("Spareribs", BeanTool.getComplexProperty(response, "data.outputs.dish"));
    }

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
        return uploadResource(fileName, inputResource(fileName));
    }

    ApiResponse<?> uploadResource(String fileName, IResource resource) {
        InputStream is = resource.getInputStream();

        try {
            UploadRequestBean request = new UploadRequestBean();
            request.setFileName(fileName);
            request.setBizObjName(NopRuleDefinition.class.getSimpleName());
            request.setFieldName("importFile");
            request.setLength(resource.length());
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
