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
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.IoHelper;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.file.core.UploadRequestBean;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.rule.dao.entity.NopRuleDefinition;
import io.nop.rule.dao.entity.NopRuleNode;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.api.core.beans.FilterBeans.and;
import static io.nop.api.core.beans.FilterBeans.eq;
import static io.nop.rule.dao.entity._gen._NopRuleNode.PROP_NAME_label;
import static io.nop.rule.dao.entity._gen._NopRuleNode.PROP_NAME_ruleId;
import static io.nop.rule.dao.entity._gen._NopRuleDefinition.PROP_NAME_ruleName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-03 回归钉（plan 2528 红②，CRA 5.5 C7-R2-001）：规则热更新失效传播——钉定集扩展至 NopRuleNode 子行。
 *
 * <p>缺陷形态（pre-fix）：{@code DaoRuleModelLoader.loadRule} 的依赖钉定只有定义行一条，
 * 而判定树内容取自 {@code NopRuleNode} 子行；子行经一等标准 CRUD 写面独立编辑时不触定义行
 * updateTime，平台失效判据恒判无修改，旧编译规则无限期服务（「保存即生效」契约静默失效）。
 *
 * <p>钉形：装载并执行规则 → 经 {@code NopRuleNode__update} 一等 CRUD 改节点谓词（不触定义行）→
 * 再执行同一规则，断言判定行为随新节点变化。时间口径按设计裁定②与 plan 2500 新判据兼容：
 * 宽限关闭档（{@code change-grace-period=0}）+ 依赖检查百毫秒档（{@code dao-resource-check-interval=100}），
 * 不依赖真实时钟快进。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
@NopTestProperty(name = "nop.file.store-dir", value = "./target")
@NopTestProperty(name = "nop.core.component.resource-cache.change-grace-period", value = "0")
@NopTestProperty(name = "nop.orm.dao-resource-check-interval", value = "100")
public class TestRuleNodeHotUpdate_W_03 extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    /**
     * 决策树分支次序：Fall(sortNo=1) → Winter(sortNo=2) → Spring → in[...] → Summer。
     * season=Winter/guestCount=3 首次执行走 Winter→"&lt;= 8" 输出 Roastbeef。
     * 把 Fall 节点谓词改为 eq(season,"Winter") 后，同一输入命中 Fall→"&lt;= 8" 输出 Spareribs——
     * 行为变化仅由节点行内容变更驱动（定义行零改动）。
     */
    @Test
    @EnableSnapshot(checkOutput = false)
    public void testNodeEditInvalidatesCompiledRule() throws InterruptedException {
        uploadAndSaveRule("test-w03");

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("season", "Winter");
        inputs.put("guestCount", 3);

        String firstDish = executeRule("test-w03", 1, inputs);
        assertEquals("Roastbeef", firstDish, "precondition: imported rule serves Winter branch");

        NopRuleDefinition definition = findDefinition("test-w03");
        NopRuleNode fallNode = findNodeByLabel(definition.getRuleId(), "Fall");
        assertNotNull(fallNode, "Fall branch node must exist in imported decision tree");

        // 一等标准 CRUD 写面编辑子行：定义行 updateTime 不被触碰
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("sid", fallNode.getSid());
        updateData.put("predicate", "{\"$type\":\"eq\",\"name\":\"season\",\"value\":\"Winter\"}");
        Map<String, Object> updateReq = new LinkedHashMap<>();
        updateReq.put("data", updateData);
        IGraphQLExecutionContext updateCtx = graphQLEngine.newRpcContext(GraphQLOperationType.mutation,
                "NopRuleNode__update", ApiRequest.build(updateReq));
        ApiResponse<?> updateResp = graphQLEngine.executeRpc(updateCtx);
        assertTrue(updateResp.isOk(), "node-level first-class CRUD must succeed: " + updateResp);

        NopRuleDefinition reloaded = findDefinition("test-w03");
        assertEquals(definition.getUpdateTime(), reloaded.getUpdateTime(),
                "node edit must not touch definition row updateTime (W-03 defect premise)");

        // 平台时间快进（设计裁定②「不依赖真实时钟快进」）：autotest JVM 的平台时钟
        // （CoreMetrics→TestClock）为单调高水位线，线程 sleep 不推进平台时间；refresh 门
        // （resource-cache.refresh-min-interval=100ms）与依赖检查节流（dao-resource-check-interval=100ms）
        // 均以平台时间为准，此处显式推进平台时钟 500ms 越过节流窗，使节点行变更在下一检查周期内被检出。
        for (int i = 0; i < 500; i++) {
            CoreMetrics.currentTimeMillis();
        }

        // R4-32（plan 2566，commit 794eab47ea）将 ResourceCacheEntry 的刷新门与 verdict 记忆化
        // 成对切换为单调时钟（CoreMetrics.nanoTime()，TestClock 不覆写 → 真实时间），平台时钟快进
        // 不再能穿越 refresh-min-interval=100ms 节流窗。依赖检查本身仍以平台时钟节流
        // （DaoEntityResource.checkLoad），此处等待真实时间 200ms 保证刷新门判定执行，随后依赖
        // 判据仍由「节点行 updateTime 变更 → DaoEntityResource mtime 变更」平台判据驱动。
        Thread.sleep(200);

        NopRuleNode afterNode = findNodeByLabel(definition.getRuleId(), "Fall");
        assertNotNull(afterNode, "Fall node must persist after node-level CRUD edit");
        String secondDish = executeRule("test-w03", 1, inputs);
        assertEquals("Spareribs", secondDish,
                "node-level edit must invalidate compiled rule: next execution follows the new predicate");
    }

    private void uploadAndSaveRule(String ruleName) {
        ApiResponse<?> uploadResp = uploadRuleFile("decision-tree.rule.xlsx");
        String downloadPath = (String) BeanTool.getComplexProperty(uploadResp, "data.value");
        assertNotNull(downloadPath, "upload must return download path");

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("ruleName", ruleName);
        entity.put("ruleGroup", "default");
        entity.put("ruleVersion", 1);
        entity.put("displayName", "W03 Hot Update Test");
        entity.put("status", 1);
        entity.put("importFile", downloadPath);

        Map<String, Object> saveData = new LinkedHashMap<>();
        saveData.put("data", entity);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(GraphQLOperationType.mutation,
                "NopRuleDefinition__save", ApiRequest.build(saveData));
        ApiResponse<?> response = graphQLEngine.executeRpc(ctx);
        assertTrue(response.isOk(), "save should succeed: " + response);
    }

    private ApiResponse<?> uploadRuleFile(String fileName) {
        IResource resource = inputResource(fileName);
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
            return graphQLEngine.executeRpc(ctx);
        } finally {
            IoHelper.safeCloseObject(is);
        }
    }

    @SuppressWarnings("unchecked")
    private String executeRule(String ruleName, int ruleVersion, Map<String, Object> inputs) {
        Map<String, Object> execData = new LinkedHashMap<>();
        execData.put("ruleName", ruleName);
        execData.put("ruleVersion", ruleVersion);
        execData.put("inputs", inputs);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(GraphQLOperationType.mutation,
                "RuleService__executeRule", ApiRequest.build(execData));
        ApiResponse<?> response = graphQLEngine.executeRpc(ctx);
        assertTrue(response.isOk(), "executeRule should succeed: " + response);
        return (String) BeanTool.getComplexProperty(response, "data.outputs.dish");
    }

    private NopRuleDefinition findDefinition(String ruleName) {
        IEntityDao<NopRuleDefinition> dao = daoProvider.daoFor(NopRuleDefinition.class);
        QueryBean query = new QueryBean();
        query.addFilter(eq(PROP_NAME_ruleName, ruleName));
        NopRuleDefinition definition = dao.findFirstByQuery(query);
        assertNotNull(definition, "rule definition must exist");
        return definition;
    }

    private NopRuleNode findNodeByLabel(String ruleId, String label) {
        IEntityDao<NopRuleNode> dao = daoProvider.daoFor(NopRuleNode.class);
        List<TreeBean> filters = List.of(eq(PROP_NAME_ruleId, ruleId), eq(PROP_NAME_label, label));
        QueryBean query = new QueryBean();
        query.addFilter(and(filters));
        return dao.findFirstByQuery(query);
    }
}
