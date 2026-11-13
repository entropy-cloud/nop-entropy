/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.DaoConfigs;
import io.nop.dao.shard.ConfigShardSelector;
import io.nop.dao.shard.IShardSelector;
import io.nop.dao.shard.MissingShardPolicy;
import io.nop.orm.OrmMigrationWriteGate;
import io.nop.orm.factory.SessionFactoryImpl;
import io.nop.orm.model.IOrmModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.orm.migration.MigrationErrors.ARG_GATE;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_GATE_NOT_SATISFIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * plan 2507 裁定⑤（2.5-C1-007）：shardSelector 热替换可见性与安全发布——
 * rebuildRouting 热替换后读回同一性确认（失配 = 可见性异常 → gateFailed 留痕 + 类型化失败，
 * 保持写门冻结）；真实宿主热替换对并发查询读者有界窗内可见（volatile 安全发布冒烟）；
 * ConfigShardSelector 策略表 init 完成后不可变化承载。
 *
 * <p>红绿形态（设计 §七）：修复前红 = 读回确认缺失，失配宿主 confirmSwitch 静默成功
 * （{@code testRebuildRoutingReadBackMismatchFailsTypedWithStateTrace}）；volatile 缺失在 JMM 下
 * 难确定性复现，按设计采信「读回失配注入 → gateFailed」可观测腿为红证据，跨线程腿为
 * 并发冒烟 + 变异论证（删除 volatile 后读者可无限期读旧 selector，非确定性，不做确定性红跑）。
 * e2e restart-required 降级断言（TestMigrationPipelineE2E :95）零回改。</p>
 */
@Timeout(300)
public class TestShardSelectorHotSwapVisibility extends AbstractMigrationE2ETestCase {

    /**
     * 真实 SessionFactoryImpl 宿主 + fake 实体模型（编排器元数据消费面走 FakeEntityModelFacade，
     * 数据面走 H2，与既有 E2E 同基座）。
     */
    static class RebuildTargetFactory extends SessionFactoryImpl {
        private final IOrmModel model;

        RebuildTargetFactory(IOrmModel model) {
            this.model = model;
        }

        @Override
        public IOrmModel getOrmModel() {
            return model;
        }
    }

    /**
     * 读回失配注入宿主：setShardSelector 静默丢弃写入——模拟 JMM 可见性失配
     * （写入者认为已替换、读回看到的仍是旧 selector）。真实 JMM 缺陷不可确定性复现，
     * 以注入失配把不可观测缺陷转化为可断言形态（设计裁定⑤读回确认的可测腿）。
     */
    static final class StalePublicationFactory extends RebuildTargetFactory {
        StalePublicationFactory(IOrmModel model) {
            super(model);
        }

        @Override
        public void setShardSelector(IShardSelector selector) {
            // 模拟可见性失配：丢弃写入（读回 ≠ 写入）
        }
    }

    private static IOrmModel fakeModel() {
        return (IOrmModel) FakeEntityModelFacade.facade(
                "mig.entity.MigOrder", "MIG_ORDER", "sid", "shard_key");
    }

    /** 源表种子行（基类 insertSourceRow 为 protected 面测试夹具）。 */
    private void seedSourceRows() {
        insertSourceRow("1", "10.50", "1");
        insertSourceRow("2", "20.00", "2");
        insertSourceRow("3", "30.25", "3");
        insertSourceRow("4", "40.00", "4");
    }

    /**
     * 正向腿：真实宿主 confirmSwitch → rebuildRouting 热替换新 ConfigShardSelector，
     * 读回确认通过 → routeRebuild=rebuild-done，且新 selector 携带翻转后的分片配置。
     */
    @Test
    void testConfirmSwitchPublishesRebuiltSelectorOnRealHost() {
        seedSourceRows();
        RebuildTargetFactory factory = new RebuildTargetFactory(fakeModel());
        OrmMigrationOrchestrator orchestrator = new OrmMigrationOrchestrator(
                rescaleConfig(), jdbcTemplate, factory, jdbcTemplate, "");
        orchestrator.start("hs1");
        orchestrator.runFull("hs1");
        OrmMigrationWriteGate.setClosed(true);
        try {
            orchestrator.runVerify("hs1");
            orchestrator.confirmSwitch("hs1");

            assertEquals("rebuild-done", orchestrator.getState("hs1").getRouteRebuild(),
                    "real host hot swap must record rebuild-done after read-back confirmation");
            IShardSelector selector = factory.getShardSelector();
            assertTrue(selector instanceof ConfigShardSelector,
                    "real host must receive the rebuilt ConfigShardSelector");
            assertTrue(((ConfigShardSelector) selector).hasStrategyConfig(),
                    "rebuilt selector must carry the flipped shard config");
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }
    }

    /**
     * 红腿（E2）：读回失配注入 → confirmSwitch 必须类型化失败（ERR_MIG_GATE_NOT_SATISFIED），
     * 状态行留痕 rebuild-failed 且写门保持冻结。修复前红形态：无读回确认，失配静默成功
     * （assertThrows 不成立）。变异论证：删除读回确认代码即回到该红形态。
     */
    @Test
    void testRebuildRoutingReadBackMismatchFailsTypedWithStateTrace() {
        seedSourceRows();
        StalePublicationFactory factory = new StalePublicationFactory(fakeModel());
        OrmMigrationOrchestrator orchestrator = new OrmMigrationOrchestrator(
                rescaleConfig(), jdbcTemplate, factory, jdbcTemplate, "");
        orchestrator.start("hs2");
        orchestrator.runFull("hs2");
        OrmMigrationWriteGate.setClosed(true);
        try {
            orchestrator.runVerify("hs2");

            NopException e = assertThrows(NopException.class, () -> orchestrator.confirmSwitch("hs2"),
                    "publication mismatch must fail typed instead of silently continuing");
            assertEquals(ERR_MIG_GATE_NOT_SATISFIED.getErrorCode(), e.getErrorCode());
            assertTrue(String.valueOf(e.getParams().get(ARG_GATE)).contains("shard selector"),
                    "gate message must name the shard selector publication mismatch: " + e.getParams());
            assertTrue(String.valueOf(orchestrator.getState("hs2").getRouteRebuild())
                            .startsWith("rebuild-failed"),
                    "state row must keep the failure trace");
            assertEquals(MigrationPhase.SWITCH, orchestrator.getState("hs2").getPhase(),
                    "job must stay in SWITCH with write gate frozen (fix-forward or flip-back)");
            assertTrue(OrmMigrationWriteGate.isClosed(),
                    "publication mismatch must keep the write gate closed (never open with stale routing)");
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }
    }

    /**
     * 并发冒烟腿（绿形态；变异论证见类 javadoc）：热替换对并发查询读者有界窗内可见。
     * daemon 读者 + latch 对齐 + 有界观察窗（裁定⑦纪律），无无限 join。
     */
    @Test
    @Timeout(60)
    void testHotSwapSelectorVisibleToConcurrentReaderInBoundedWindow() throws Exception {
        RebuildTargetFactory factory = new RebuildTargetFactory(fakeModel());
        IShardSelector initial = factory.getShardSelector();
        // 未 init 的 ConfigShardSelector：行为同 EmptyShardSelector（hasConfig=false），但为独立实例可同一性比对
        ConfigShardSelector fresh = new ConfigShardSelector();

        CountDownLatch startGate = new CountDownLatch(1);
        AtomicReference<IShardSelector> observed = new AtomicReference<>(initial);
        Thread reader = new Thread(() -> {
            try {
                startGate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            // 有界轮询读者：读到新 selector 即退出，超时退出由 join+isAlive 断言判定
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                observed.set(factory.getShardSelector());
                if (observed.get() == fresh)
                    return;
            }
        }, "shard-selector-visibility-reader");
        reader.setDaemon(true);
        reader.start();
        startGate.countDown();

        factory.setShardSelector(fresh);
        reader.join(6000);
        assertFalse(reader.isAlive(), "reader must terminate within the bounded observation window");
        assertSame(fresh, observed.get(),
                "hot-swapped selector must become visible to query threads within bounded window");
    }

    /**
     * 策略表不可变断言（裁定⑤「init 完成后 final/不可变化承载」的行为面）：
     * init 后策略表拒绝结构变更；未配置时 hasConfig=false。
     */
    @Test
    void testConfigShardSelectorStrategyTableImmutableAfterInit() {
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD,
                "{\"strategies\":{\"order-mod\":{\"type\":\"mod\",\"shard-count\":2,"
                        + "\"entities\":[\"mig.entity.MigOrder\"]}}}");
        try {
            ConfigShardSelector selector = new ConfigShardSelector();
            selector.init();
            assertTrue(selector.hasStrategyConfig(), "init must load the configured strategy table");

            Map<?, ?> strategies = readStrategiesMap(selector);
            assertThrows(UnsupportedOperationException.class,
                    () -> ((Map<Object, Object>) strategies).put("mutated", null),
                    "strategy table must be immutable after init (safe publication)");
            assertNotNull(selector.selectShard("mig.entity.MigOrder", "shard_key", "1"),
                    "rebuilt selector must route by the configured strategy");
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
        }
    }

    /** 白盒读策略表（修复后状态载体字段；缺失即显式失败，不静默）。 */
    private static Map<?, ?> readStrategiesMap(ConfigShardSelector selector) {
        try {
            Field stateField = ConfigShardSelector.class.getDeclaredField("state");
            stateField.setAccessible(true);
            Object state = stateField.get(selector);
            Field strategiesField = state.getClass().getDeclaredField("strategies");
            strategiesField.setAccessible(true);
            return (Map<?, ?>) strategiesField.get(state);
        } catch (Exception e) {
            return fail("ConfigShardSelector must carry its strategy table in an immutable state field", e);
        }
    }

    /** 未配置分片时零行为变更（既有契约钉：无配置 init 后等同 EmptyShardSelector 行为）。 */
    @Test
    void testEmptyConfigSelectorStaysEmpty() {
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
        ConfigShardSelector selector = new ConfigShardSelector();
        selector.init();
        assertFalse(selector.hasStrategyConfig(), "no-config init must keep empty behavior");
        assertNull(selector.selectShard("mig.entity.MigOrder", "shard_key", "1"),
                "no-config selector must not route (zero behavior change)");
        assertEquals(MissingShardPolicy.NONE, selector.getMissingShardPolicy("mig.entity.MigOrder"));
    }
}
