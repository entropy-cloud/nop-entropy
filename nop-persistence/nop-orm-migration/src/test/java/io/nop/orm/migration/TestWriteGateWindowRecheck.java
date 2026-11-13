/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.shard.IShardSelector;
import io.nop.orm.OrmMigrationWriteGate;
import io.nop.orm.factory.SessionFactoryImpl;
import io.nop.orm.model.IOrmModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static io.nop.orm.migration.MigrationErrors.ERR_MIG_GATE_NOT_SATISFIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * plan 2507 裁定⑤（2.5-C1-010）：写门保持机制——写门/门禁检查是瞬时读而冻结承诺贯穿
 * VERIFY/SWITCH 分钟级生命周期：窗口内门被翻开（外部翻转或租约 fail-open，两者在 isClosed()
 * 读面同型）时必须 gateFailed 类型化失败 + 状态行留痕，不得静默继续。
 *
 * <p>红绿形态（设计 §七）：修复前红 = VERIFY 窗口内开门仍以 passed 落账（失配被掩盖）、
 * SWITCH 窗口内开门静默完成确认。可见性半边随裁定④ holder 化承载（零独立代码，登记声明）。</p>
 */
@Timeout(300)
public class TestWriteGateWindowRecheck extends AbstractMigrationE2ETestCase {

    /**
     * verify 窗口开门校验器：三级校验执行中门被翻开，仍返回「总体一致」报告——
     * 复刻审计掩盖形态（VERIFY 在漂移窗口上通过落账）。
     */
    static final class GateFlipInVerifyStub extends ConsistencyVerifier {
        GateFlipInVerifyStub(TargetShardMapping mapping) {
            // stub 不触库：jdbcTemplate 传 null（verify 被覆写，永不使用）
            super(null, mapping, List.of("sid"));
        }

        @Override
        public VerifyReport verify(MigrationJobConfig config) {
            OrmMigrationWriteGate.setClosed(false);
            return new VerifyReport();
        }
    }

    /**
     * SWITCH 窗口开门宿主：rebuildRouting 热替换写入后立即开门——模拟翻转+路由重建
     * 时长窗内门被翻开（isClosed() 在 :270-271 门禁时仍为 closed，窗口内失配）。
     */
    static final class GateOpenInSwitchFactory extends TestShardSelectorHotSwapVisibility.RebuildTargetFactory {
        GateOpenInSwitchFactory(IOrmModel model) {
            super(model);
        }

        @Override
        public void setShardSelector(IShardSelector selector) {
            super.setShardSelector(selector);
            OrmMigrationWriteGate.setClosed(false);
        }
    }

    /**
     * 红腿（E2）：VERIFY 窗口内门被翻开 → runVerify 必须类型化失败且状态行 verifyResult=failed
     * （不得以 passed 落账）。修复前红形态：校验结束即 passed 落账，失配被掩盖。
     */
    @Test
    void testRunVerifyDetectsGateReopenedBeforeResultRecorded() {
        seedSourceRows();
        OrmMigrationOrchestrator orchestrator = new OrmMigrationOrchestrator(
                rescaleConfig(), jdbcTemplate, entityFacade, jdbcTemplate, "") {
            @Override
            public ConsistencyVerifier verifier() {
                return new GateFlipInVerifyStub(mapping());
            }
        };
        orchestrator.start("wr1");
        orchestrator.runFull("wr1");
        OrmMigrationWriteGate.setClosed(true);
        try {
            NopException e = assertThrows(NopException.class, () -> orchestrator.runVerify("wr1"),
                    "gate reopened during VERIFY window must fail typed instead of recording passed");
            assertEquals(ERR_MIG_GATE_NOT_SATISFIED.getErrorCode(), e.getErrorCode());
            assertEquals("failed", orchestrator.getState("wr1").getVerifyResult(),
                    "state row must record failed verify (no passed on the drifted window)");
            assertEquals("wr1", e.getParams().get(MigrationErrors.ARG_JOB_ID));
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }
    }

    /**
     * 红腿（E2）：SWITCH 翻转+路由重建时长窗内门被翻开 → confirmSwitch 必须类型化失败
     * （不静默继续开门完成确认）。修复前红形态：窗口失配无感知，confirmSwitch 静默成功。
     */
    @Test
    void testConfirmSwitchDetectsGateReopenedDuringSwitchWindow() {
        seedSourceRows();
        GateOpenInSwitchFactory factory = new GateOpenInSwitchFactory(fakeModel());
        OrmMigrationOrchestrator orchestrator = new OrmMigrationOrchestrator(
                rescaleConfig(), jdbcTemplate, factory, jdbcTemplate, "");
        orchestrator.start("wr2");
        orchestrator.runFull("wr2");
        OrmMigrationWriteGate.setClosed(true);
        try {
            orchestrator.runVerify("wr2");

            NopException e = assertThrows(NopException.class, () -> orchestrator.confirmSwitch("wr2"),
                    "gate reopened during SWITCH window must fail typed instead of silently continuing");
            assertEquals(ERR_MIG_GATE_NOT_SATISFIED.getErrorCode(), e.getErrorCode());
            assertEquals(MigrationPhase.SWITCH, orchestrator.getState("wr2").getPhase());
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }
    }

    /**
     * 对照腿（绿）：窗口内门保持关闭时复核零行为变更——既有 E2E（TestMigrationPipelineE2E）
     * 全链绿为同一契约的回归面，此处覆盖「lag 静默窗后复核」通过路径。
     */
    @Test
    void testGateStillClosedWindowRechecksPassThrough() {
        seedSourceRows();
        OrmMigrationOrchestrator orchestrator = new OrmMigrationOrchestrator(
                rescaleConfig(), jdbcTemplate, entityFacade, jdbcTemplate, "");
        orchestrator.start("wr3");
        orchestrator.runFull("wr3");
        OrmMigrationWriteGate.setClosed(true);
        try {
            orchestrator.runVerify("wr3");
            assertEquals("passed", orchestrator.getState("wr3").getVerifyResult(),
                    "gate closed through the whole window must record passed (zero behavior change)");
        } finally {
            OrmMigrationWriteGate.setClosed(false);
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
}
