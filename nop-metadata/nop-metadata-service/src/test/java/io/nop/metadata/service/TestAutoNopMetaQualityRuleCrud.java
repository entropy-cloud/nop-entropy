package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * AutoTest 16-01 增量（MR3 R3.18）：质量规则保存+查询快照测试。
 *
 * <p>录制方式（docs-for-ai/02-core-guides/testing.md §快照录制）：首次以
 * {@code snapshotTest = RECORDING} 录制（本类已录完，日常校验用默认 CHECKING）。
 * CHECKING 模式下框架从 {@code _cases/} 恢复录制时的 H2 快照并校验输出。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestAutoNopMetaQualityRuleCrud extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Test
    @EnableSnapshot
    public void testSaveAndQueryQualityRule() {
        var saveReq = request("saveQualityRule.json5", Map.class);
        IGraphQLExecutionContext saveCtx = graphQLEngine.newRpcContext(null,
                "NopMetaQualityRule__save", saveReq);
        var saveResp = graphQLEngine.executeRpc(saveCtx);
        output("saveQualityRuleResponse.json5", saveResp);

        var queryReq = request("queryQualityRule.json5", String.class);
        IGraphQLExecutionContext queryCtx = graphQLEngine.newRpcContext(null,
                "NopMetaQualityRule__get", queryReq);
        var queryResp = graphQLEngine.executeRpc(queryCtx);
        output("queryQualityRuleResponse.json5", queryResp);
    }
}
