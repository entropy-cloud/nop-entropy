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

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestAutoNopMetaAggregationCrud extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    /**
     * 验证实体展示名聚合：对 nop_meta_module 表做 count + groupBy status。
     * 种子数据包含一条 module 记录，测试执行查询并快照结果。
     */
    @Test
    @EnableSnapshot
    public void testModuleCountAggregation() {
        var request = request("request.json5", Map.class);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(null,
                "NopMetaModule__findPage", request);
        var response = graphQLEngine.executeRpc(ctx);
        output("response.json5", response);
    }
}
