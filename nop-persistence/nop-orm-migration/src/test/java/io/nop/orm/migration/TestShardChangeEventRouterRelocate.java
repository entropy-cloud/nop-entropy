/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.ChangeEventMetadata;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.migration.TargetShardMapping.PhysicalTable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.orm.migration.MigrationErrors.ERR_MIG_CONFIG_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 红⑩（C3-009/C10-006，裁定⑩）：relocate 路由 querySpace 派生。
 * 缺陷态红判据：route relocate 分支硬编码空串 querySpace——增量链 filter 谓词恒 false
 * （100% 丢事件）、FULL upsert 落回源侧 default 表；修复后：目标 qs 从 mapping 正确派生
 * （单源取唯一目标；多源且事件无源 qs 显式失败不猜测）。
 */
public class TestShardChangeEventRouterRelocate {

    private TargetShardMapping relocateMapping(List<String> sourceQuerySpaces) {
        Map<String, Object> root = new HashMap<>();
        root.put("scenario", "relocate");
        root.put("entity", "mig.entity.MigOrder");
        root.put("source-query-spaces", sourceQuerySpaces);
        Map<String, String> mapping = new HashMap<>();
        for (String source : sourceQuerySpaces)
            mapping.put(source, source + "-target");
        root.put("target-query-space-mapping", mapping);
        MigrationJobConfig config = MigrationJobConfig.fromMap(root);
        IOrmSessionFactory facade = FakeEntityModelFacade.facade(
                "mig.entity.MigOrder", "MIG_ORDER", "sid", "shard_key");
        return TargetShardMapping.of(facade, config);
    }

    private ChangeEvent event() {
        ChangeEventMetadata meta = new ChangeEventMetadata("mysql", "mig", "db", null,
                "MIG_ORDER", null);
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("SID", "1");
        return new ChangeEvent(meta, "c", null, image, image, 0L);
    }

    @Test
    public void testRelocateRoute_CarriesDerivedTargetQuerySpace() {
        TargetShardMapping mapping = relocateMapping(List.of("ds_a"));
        ShardChangeEventRouter router = new ShardChangeEventRouter(mapping);
        PhysicalTable routed = router.route(event());
        // 缺陷态：new PhysicalTable("", baseTableName) —— querySpace 空串
        assertEquals("ds_a-target", routed.getQuerySpace(),
                "relocate route must derive the target querySpace from the mapping (not empty string)");
        assertEquals("MIG_ORDER", routed.getTableName());
        // filter 谓词回归：belongsTo 不再恒 false（增量链不再 100% 丢事件）
        assertTrue(router.belongsTo(event(), routed),
                "belongsTo must hold for the correctly routed target (filter predicate regression pin)");
        assertNotEquals("", routed.getQuerySpace(), "empty-string querySpace is the defect being fixed");
    }

    @Test
    public void testRelocateRouteRowAndShardValue_CarryDerivedQuerySpace() {
        TargetShardMapping mapping = relocateMapping(List.of("ds_a"));
        // FULL 侧同病同修（裁定⑩ FULL 侧核对面）：routeRow/routeShardValue 不再落回源侧 default 表
        PhysicalTable rowTarget = mapping.routeRow(Map.of("SID", "1"), "pk1", "MIG_ORDER");
        assertEquals("ds_a-target", rowTarget.getQuerySpace());
        PhysicalTable shardTarget = mapping.routeShardValue("1");
        assertEquals("ds_a-target", shardTarget.getQuerySpace());
    }

    @Test
    public void testRelocateMultiSourceWithoutQuerySpace_FailsExplicitly() {
        TargetShardMapping mapping = relocateMapping(List.of("ds_a", "ds_b"));
        ShardChangeEventRouter router = new ShardChangeEventRouter(mapping);
        // 事件元数据不携带源 querySpace：多源映射下不可猜测 → 类型化失败（非静默）
        NopException e = assertThrows(NopException.class, () -> router.route(event()));
        assertEquals(ERR_MIG_CONFIG_INVALID.getErrorCode(), e.getErrorCode());
        // 单源可得路径仍正常
        PhysicalTable viaSingle = mapping.relocateTarget("ds_b");
        assertEquals("ds_b-target", viaSingle.getQuerySpace());
    }
}
