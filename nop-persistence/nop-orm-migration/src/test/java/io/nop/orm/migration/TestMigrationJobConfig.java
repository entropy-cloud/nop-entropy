/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.orm.migration.MigrationErrors.ERR_MIG_CONFIG_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编排配置解析（设计 §4.8）：合法 JSON 双场景 + 缺字段/场景与字段矛盾/坏 JSON/未知写门取值
 * 逐形态显式失败（无静默缺省）。
 */
public class TestMigrationJobConfig {

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static final String RESCALE_JSON = "{"
            + "\"scenario\":\"rescale\","
            + "\"entity\":\"mig.entity.MigOrder\","
            + "\"source-query-spaces\":[\"\"],"
            + "\"target-strategy\":{\"strategies\":{\"order-mod\":{\"type\":\"mod\",\"shard-count\":2,"
            + "\"entities\":[\"mig.entity.MigOrder\"]}}},"
            + "\"write-gate\":\"open\","
            + "\"incremental\":{\"enabled\":true,\"connector-name\":\"mig-order\","
            + "\"debezium\":{\"connector-type\":\"mysql\",\"database-host\":\"localhost\","
            + "\"database-port\":3306,\"database-user\":\"u\",\"database-password\":\"p\","
            + "\"database-name\":\"db\"},\"quiesce-seconds\":5},"
            + "\"verify\":{\"count\":true,\"sample-ratio\":0.5,\"full-checksum\":false},"
            + "\"throttle\":{\"rate-limit\":100,\"batch-size\":50,\"concurrency\":2}"
            + "}";

    @Test
    public void testParseRescaleConfig() {
        MigrationJobConfig config = MigrationJobConfig.parse(RESCALE_JSON);
        assertTrue(config.isRescale());
        assertEquals("mig.entity.MigOrder", config.getEntityName());
        assertEquals(List.of(""), config.getSourceQuerySpaces());
        assertTrue(config.isIncrementalEnabled());
        assertEquals("mig-order", config.getIncrementalConnectorName());
        assertEquals("mysql", config.getIncrementalDebezium().get("connector-type"));
        assertEquals(5, config.getQuiesceSeconds());
        assertTrue(config.isVerifyCount());
        assertEquals(0.5, config.getVerifySampleRatio(), 1e-9);
        assertFalse(config.isVerifyFullChecksum());
        assertEquals(100, config.getThrottleRateLimit(), 1e-9);
        assertEquals(50, config.getThrottleBatchSize());
        assertEquals(2, config.getThrottleConcurrency());
        // batch-task-name 缺省派生
        assertEquals("orm-migration-MigOrder", config.getBatchTaskName());
        // SWITCH 翻转文本：target-strategy 可序列化回 JSON（含 strategies 键）
        assertTrue(config.getTargetShardConfigText().contains("order-mod"));
        assertEquals("open", config.getInitialWriteGate());
    }

    @Test
    public void testParseRelocateConfig() {
        Map<String, Object> root = new HashMap<>();
        root.put("scenario", "relocate");
        root.put("entity", "mig.entity.MigOrder");
        root.put("source-query-spaces", List.of("ds_a"));
        Map<String, String> mapping = new HashMap<>();
        mapping.put("ds_a", "ds_b");
        root.put("target-query-space-mapping", mapping);
        MigrationJobConfig config = MigrationJobConfig.fromMap(root);
        assertFalse(config.isRescale());
        assertEquals("ds_b", config.getTargetQuerySpaceMapping().get("ds_a"));
        assertTrue(config.isVerifyCount(), "count verification defaults to true");
    }

    @Test
    public void testMissingScenarioFails() {
        NopException e = assertThrows(NopException.class,
                () -> MigrationJobConfig.parse("{\"entity\":\"a\"}"));
        assertEquals(ERR_MIG_CONFIG_INVALID.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testUnknownScenarioFails() {
        assertThrows(NopException.class, () -> MigrationJobConfig.parse(
                "{\"scenario\":\"copy\",\"entity\":\"a\",\"source-query-spaces\":[\"x\"]}"));
    }

    @Test
    public void testRescaleWithoutTargetStrategyFails() {
        assertThrows(NopException.class, () -> MigrationJobConfig.parse(
                "{\"scenario\":\"rescale\",\"entity\":\"a\",\"source-query-spaces\":[\"x\"]}"));
    }

    @Test
    public void testRelocateWithoutMappingFails() {
        assertThrows(NopException.class, () -> MigrationJobConfig.parse(
                "{\"scenario\":\"relocate\",\"entity\":\"a\",\"source-query-spaces\":[\"x\"]}"));
    }

    @Test
    public void testBadJsonFails() {
        assertThrows(NopException.class, () -> MigrationJobConfig.parse("{not-json"));
        assertThrows(NopException.class, () -> MigrationJobConfig.parse("[1,2]"));
        assertThrows(NopException.class, () -> MigrationJobConfig.parse(null));
    }

    @Test
    public void testIncrementalWithoutConnectorNameFails() {
        assertThrows(NopException.class, () -> MigrationJobConfig.parse(
                "{\"scenario\":\"rescale\",\"entity\":\"a\",\"source-query-spaces\":[\"x\"],"
                        + "\"target-strategy\":{\"strategies\":{}},"
                        + "\"incremental\":{\"enabled\":true}}"));
    }

    @Test
    public void testUnknownWriteGateValueFails() {
        assertThrows(NopException.class, () -> MigrationJobConfig.parse(
                "{\"scenario\":\"relocate\",\"entity\":\"a\",\"source-query-spaces\":[\"x\"],"
                        + "\"target-query-space-mapping\":{\"a\":\"b\"},\"write-gate\":\"half\"}"));
    }

    @Test
    public void testInvalidSampleRatioFails() {
        assertThrows(NopException.class, () -> MigrationJobConfig.parse(
                "{\"scenario\":\"relocate\",\"entity\":\"a\",\"source-query-spaces\":[\"x\"],"
                        + "\"target-query-space-mapping\":{\"a\":\"b\"},"
                        + "\"verify\":{\"sample-ratio\":1.5}}"));
    }
}
