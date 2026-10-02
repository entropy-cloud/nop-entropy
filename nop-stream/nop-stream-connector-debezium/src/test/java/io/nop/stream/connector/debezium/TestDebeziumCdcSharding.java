/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.debezium;

import io.nop.message.debezium.DebeziumConfig;
import io.nop.stream.core.common.functions.source.SubtaskShardedSourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.operators.StreamSourceOperator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4（ai-dev/backlog/nop-stream-cdc-roadmap.md）：B 层 subtask 确定性表路由——
 * 部署期验证（空表清单 / 单表 / hash 空分片 fail-fast）、路由确定性、实例名隔离、
 * 有效配置替换、StreamSourceOperator 分片拷贝接线。
 */
public class TestDebeziumCdcSharding {

    private static final String FOUR_TABLES = "db.t1,db.t2,db.t3,db.t4";

    private DebeziumCdcSourceFunction newSource(String includeList) {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("cdc");
        config.setConnectorType("mysql");
        config.setDatabaseHost("localhost");
        config.setSchemaHistoryStore("file");
        config.setSchemaHistoryPath("/tmp/cdc/schema-history.dat");
        config.setTableIncludeList(includeList);
        return new DebeziumCdcSourceFunction(config);
    }

    @Test
    void testSingleInstanceIdentity() {
        DebeziumCdcSourceFunction source = newSource(FOUR_TABLES);
        source.validateParallelism(1);
        assertEquals(1, source.getTotalParallelismForTest());
        assertEquals(0, source.getSubtaskIndexForTest());
        assertEquals("cdc", source.instanceConnectorNameForTest());
        assertEquals(FOUR_TABLES, source.routedIncludeListForTest());
    }

    @Test
    void testValidateRequiresIncludeList() {
        DebeziumCdcSourceFunction source = newSource(null);
        StreamException err = assertThrows(StreamException.class, () -> source.validateParallelism(2));
        assertTrue(err.getMessage().contains("tableIncludeList"));
    }

    @Test
    void testSingleTableMustUseLayerA() {
        DebeziumCdcSourceFunction source = newSource("db.t1");
        StreamException err = assertThrows(StreamException.class, () -> source.validateParallelism(2));
        assertTrue(err.getMessage().contains("snapshotMaxThreads"),
                "single-table parallel snapshot must be directed to layer A");
    }

    @Test
    void testZeroTableShardFailsAtDeployment() {
        // "a" 与 "c" 的 hashCode 均为奇数：parallelism=2 时偶数分片为空
        DebeziumCdcSourceFunction source = newSource("a,c");
        StreamException err = assertThrows(StreamException.class, () -> source.validateParallelism(2));
        assertTrue(err.getMessage().contains("zero tables"),
                "hash-collision empty shard must fail at deployment time, not at runtime");
    }

    @Test
    void testShardOfIsDeterministicAndNegativeSafe() {
        assertEquals(DebeziumCdcSourceFunction.shardOf("db.t1", 4),
                DebeziumCdcSourceFunction.shardOf("db.t1", 4));
        for (String table : FOUR_TABLES.split(",")) {
            int shard = DebeziumCdcSourceFunction.shardOf(table, 4);
            assertTrue(shard >= 0 && shard < 4, "floorMod must yield a valid shard");
        }
        // 负 hashCode（多数中文/长字符串）经 floorMod 仍落入合法分片
        assertTrue(DebeziumCdcSourceFunction.shardOf("中文表名", 4) >= 0);
    }


    @Test
    void testCopiesRouteAndRenameDeterministically() {
        DebeziumCdcSourceFunction template = newSource("db.even,db.odd");
        template.validateParallelism(2);

        SubtaskShardedSourceFunction<io.nop.message.debezium.ChangeEvent> c0 = template.copyForSubtask(0);
        SubtaskShardedSourceFunction<io.nop.message.debezium.ChangeEvent> c1 = template.copyForSubtask(1);
        DebeziumCdcSourceFunction s0 = (DebeziumCdcSourceFunction) c0;
        DebeziumCdcSourceFunction s1 = (DebeziumCdcSourceFunction) c1;

        assertEquals(0, s0.getSubtaskIndexForTest());
        assertEquals(1, s1.getSubtaskIndexForTest());
        assertEquals("cdc-0", s0.instanceConnectorNameForTest());
        assertEquals("cdc-1", s1.instanceConnectorNameForTest());

        String list0 = s0.routedIncludeListForTest();
        String list1 = s1.routedIncludeListForTest();
        assertEquals("db.even,db.odd", list0 + "," + list1, "routing must be a partition of the input");
        assertNotEquals(list0, list1);

        // 路由确定性：同一 template 再拷贝一次结果一致
        assertEquals(list0, ((DebeziumCdcSourceFunction) template.copyForSubtask(0)).routedIncludeListForTest());
    }

    @Test
    void testEffectiveEngineConfigCarriesInstanceIdentity() {
        DebeziumCdcSourceFunction template = newSource("db.even,db.odd");
        template.validateParallelism(2);
        DebeziumCdcSourceFunction s1 = (DebeziumCdcSourceFunction) template.copyForSubtask(1);

        DebeziumConfig effective = s1.effectiveEngineConfigForTest();
        assertEquals("cdc-1", effective.getName());
        assertEquals(s1.routedIncludeListForTest(), effective.getTableIncludeList());

        // 原始 config 不被路由污染（模板实例与序列化路径共享它）
        assertEquals("cdc", template.getConfigForTest().getName());
        assertEquals("db.even,db.odd", template.getConfigForTest().getTableIncludeList());
    }

    @Test
    void testStreamSourceOperatorCopiesShardFunction() {
        DebeziumCdcSourceFunction template = newSource("db.even,db.odd");
        template.validateParallelism(2);
        StreamSourceOperator<io.nop.message.debezium.ChangeEvent> op = new StreamSourceOperator<>(template);

        StreamSourceOperator<io.nop.message.debezium.ChangeEvent> op0 = op.copyForSubtask(0);
        StreamSourceOperator<io.nop.message.debezium.ChangeEvent> op1 = op.copyForSubtask(1);

        DebeziumCdcSourceFunction s0 = (DebeziumCdcSourceFunction) op0.getSourceFunction();
        DebeziumCdcSourceFunction s1 = (DebeziumCdcSourceFunction) op1.getSourceFunction();
        assertEquals("cdc-0", s0.instanceConnectorNameForTest());
        assertEquals("cdc-1", s1.instanceConnectorNameForTest());
        assertNotEquals(s0, s1, "each subtask must own an independent function copy");
    }
}
