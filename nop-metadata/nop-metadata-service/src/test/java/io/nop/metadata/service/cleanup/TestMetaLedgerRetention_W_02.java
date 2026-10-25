package io.nop.metadata.service.cleanup;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.commons.concurrent.executor.IPeriodicTaskListener;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.concurrent.executor.ThreadPoolStats;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaCatalog;
import io.nop.metadata.dao.entity.NopMetaModelChangedEvent;
import io.nop.metadata.dao.entity.NopMetaProfilingResult;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.dao.entity.NopMetaQualityScore;
import io.nop.metadata.dao.entity.NopMetaReconciliationResult;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5.5-W-02（plan 2502）回归钉：nop-metadata 六张 append-only 时序表保留期清理。
 * <p>
 * 断言面（ledger-retention-policy §3.4 成员 4 行）：
 * <ul>
 * <li>①六表逐面超期清理（锚列 createTime；mediumtext 双快照的 model_changed_event 表含内）</li>
 * <li>②未到期行保留——「历史事实语义」在保留窗口内不破坏（orm 注释钉死的 insert-only 语义）</li>
 * <li>③清理幂等（第二轮零删除）</li>
 * <li>接线验证（rule 23）：doStart 生产生命周期链真实调度 + 被调度命令在真实 DB 链上删除过期行
 * （防 C7-005 空壳）+ doStop 取消</li>
 * <li>显式禁用（retention-days=0）与 fail-fast 包络（负保留期/非正批删上限/周期低于 60s）</li>
 * </ul>
 * <p>
 * 测试⑤（第七面 = C7-101 衍生 qualityBreachApproval 告警工作流实例）：按设计裁定④=选③，
 * 该面显式边界声明归属 R3-05（nop-wf 侧非自有表，本批零产品 diff）——本测试类不设该面断言，
 * rule 25 No-new-test 声明落 plan 2502 Phase 5 Exit Criteria。
 * <p>
 * 红证据（E2 变异探针）：基线六表零清理路径（5.5 审计 grep 0 命中——回退论证断言全红）；
 * 实现面变异（purge 体置空）时清理断言全红，快照 _tmp/phase5-w02-red.log /
 * _tmp/phase5-w02-green.log。
 */
@NopTestConfig(
        localDb = true,
        initDatabaseSchema = OptionalBoolean.TRUE
)
public class TestMetaLedgerRetention_W_02 extends JunitAutoTestCase {

    private static final long DAY_MS = 24L * 3600_000L;
    /** mediumtext 双快照载荷（event 表为七面中最重账本行——含内验证）。 */
    private static final String SNAPSHOT_PAYLOAD = "{\"entity\":{\"fields\":" + "x".repeat(512) + "}}";

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    /**
     * ①+②：六表逐面超期清理（event mediumtext 表含内）；未到期行保留（历史事实语义窗内不破坏）。
     */
    @Test
    void testW_02_expiredRowsPurgedPerFaceUnexpiredRetained() {
        Timestamp now = new Timestamp(System.currentTimeMillis());

        String expiredResult = seedQualityResult("rule-w02-expired");
        String freshResult = seedQualityResult("rule-w02-fresh");
        String expiredScore = seedQualityScore("ent-w02-expired");
        String freshScore = seedQualityScore("ent-w02-fresh");
        String expiredCatalog = seedCatalog("ent-w02-expired");
        String freshCatalog = seedCatalog("ent-w02-fresh");
        String expiredProfiling = seedProfilingResult("ent-w02-expired");
        String freshProfiling = seedProfilingResult("ent-w02-fresh");
        String expiredRecon = seedReconciliationResult("ent-w02-expired");
        String freshRecon = seedReconciliationResult("ent-w02-fresh");
        String expiredEvent = seedModelChangedEvent("ent-w02-expired");
        String freshEvent = seedModelChangedEvent("ent-w02-fresh");

        // 回拨过期行的 createTime（锚列）：31 天前（保留窗 30 天之外）
        for (String[] face : new String[][]{
                {"NopMetaQualityResult", expiredResult}, {"NopMetaQualityScore", expiredScore},
                {"NopMetaCatalog", expiredCatalog}, {"NopMetaProfilingResult", expiredProfiling},
                {"NopMetaReconciliationResult", expiredRecon}, {"NopMetaModelChangedEvent", expiredEvent}}) {
            backdateCreateTime(face[0], face[1], 31 * DAY_MS);
        }

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        NopMetaLedgerCleaner cleaner = newCleaner();
        long deleted = cleaner.purgeExpiredLedgerRows(30 * DAY_MS, 1000);
        assertTrue(deleted >= 6, "one expired row per face must be purged, deleted=" + deleted);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        assertNull(qualityResultDao().getEntityById(expiredResult), "expired quality_result must be purged");
        assertNull(qualityScoreDao().getEntityById(expiredScore), "expired quality_score must be purged");
        assertNull(catalogDao().getEntityById(expiredCatalog), "expired catalog must be purged");
        assertNull(profilingResultDao().getEntityById(expiredProfiling), "expired profiling_result must be purged");
        assertNull(reconciliationResultDao().getEntityById(expiredRecon), "expired reconciliation_result must be purged");
        assertNull(modelChangedEventDao().getEntityById(expiredEvent),
                "expired model_changed_event (mediumtext double snapshot) must be purged");

        assertNotNull(qualityResultDao().getEntityById(freshResult),
                "unexpired quality_result retained (history-fact semantics within window)");
        assertNotNull(qualityScoreDao().getEntityById(freshScore), "unexpired quality_score retained");
        assertNotNull(catalogDao().getEntityById(freshCatalog), "unexpired catalog retained");
        assertNotNull(profilingResultDao().getEntityById(freshProfiling), "unexpired profiling_result retained");
        assertNotNull(reconciliationResultDao().getEntityById(freshRecon), "unexpired reconciliation_result retained");
        assertNotNull(modelChangedEventDao().getEntityById(freshEvent), "unexpired model_changed_event retained");
    }

    /**
     * ③清理幂等：重复触发无害，第二轮零删除。
     */
    @Test
    void testW_02_purgeIdempotent() {
        String resultId = seedQualityResult("rule-w02-idem");
        backdateCreateTime("NopMetaQualityResult", resultId, 31 * DAY_MS);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        NopMetaLedgerCleaner cleaner = newCleaner();
        long first = cleaner.purgeExpiredLedgerRows(30 * DAY_MS, 1000);
        assertTrue(first >= 1, "first round must delete the expired row");
        long second = cleaner.purgeExpiredLedgerRows(30 * DAY_MS, 1000);
        assertEquals(0L, second, "second round must be a no-op (idempotent predicate)");
    }

    /**
     * 接线验证（rule 23）：doStart 生产生命周期链真实调度清理链（taskName/interval 断言），
     * 被调度命令运行时清理入口在真实 DB 链上生效（过期行经 scheduled command 删除——非仅
     * API/bean 存在）；doStop 取消。
     */
    @Test
    void testW_02_lifecycleChainInvokesLedgerCleanup() {
        String resultId = seedQualityResult("rule-w02-wiring");
        backdateCreateTime("NopMetaQualityResult", resultId, 31 * DAY_MS);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        CapturingTimer timer = new CapturingTimer();
        NopMetaLedgerCleaner cleaner = newCleaner();
        cleaner.setTimer(timer);
        cleaner.setWorker(Runnable::run);
        cleaner.setLedgerRetentionDays(30);
        cleaner.setLedgerCleanupInterval(java.time.Duration.ofHours(1));
        cleaner.setLedgerDeleteBatchSize(500);

        cleaner.start();

        assertEquals(NopMetaLedgerCleaner.TASK_NAME, timer.taskName.get(),
                "wiring: cleaner lifecycle chain must schedule the ledger cleanup chain");
        assertEquals(3600_000L, timer.delayMillis.get(), "cleanup interval passed to scheduler");

        // 触发被调度的清理命令本体：清理入口在真实 DB 链上生效
        timer.command.get().run();

        ormTemplate.flushSession();
        ormTemplate.clearSession();
        assertNull(qualityResultDao().getEntityById(resultId),
                "expired row must be purged through the scheduled cleanup command (wiring chain)");

        cleaner.stop();
        assertTrue(timer.cancelled.get(), "doStop must cancel the cleanup chain");
    }

    /**
     * retention-days=0 为显式禁用：不调度清理链。
     */
    @Test
    void testW_02_zeroRetentionDisablesCleanupScheduling() {
        CapturingTimer timer = new CapturingTimer();
        NopMetaLedgerCleaner cleaner = newCleaner();
        cleaner.setTimer(timer);
        cleaner.setWorker(Runnable::run);
        cleaner.setLedgerRetentionDays(0);

        cleaner.start();

        assertNull(timer.taskName.get(), "retention-days=0 must disable cleanup scheduling");
        cleaner.stop();
    }

    /**
     * fail-fast 包络（设计 §3.1 第 5 项）：错配置启动期拒绝，不降级继续。
     */
    @Test
    void testW_02_invalidLedgerConfigFailsFast() {
        NopMetaLedgerCleaner negativeRetention = newCleaner();
        negativeRetention.setTimer(new CapturingTimer());
        negativeRetention.setWorker(Runnable::run);
        negativeRetention.setLedgerRetentionDays(-1);
        NopException ex1 = assertThrows(NopException.class, negativeRetention::start,
                "negative retention must fail fast at startup");
        assertEquals("nop.err.metadata.ledger-config-invalid", ex1.getErrorCode());

        NopMetaLedgerCleaner zeroBatch = newCleaner();
        zeroBatch.setTimer(new CapturingTimer());
        zeroBatch.setWorker(Runnable::run);
        zeroBatch.setLedgerDeleteBatchSize(0);
        NopException ex2 = assertThrows(NopException.class, zeroBatch::start,
                "non-positive batch cap must fail fast at startup");
        assertEquals("nop.err.metadata.ledger-config-invalid", ex2.getErrorCode());

        NopMetaLedgerCleaner tinyInterval = newCleaner();
        tinyInterval.setTimer(new CapturingTimer());
        tinyInterval.setWorker(Runnable::run);
        tinyInterval.setLedgerCleanupInterval(java.time.Duration.ofSeconds(30));
        NopException ex3 = assertThrows(NopException.class, tinyInterval::start,
                "cleanup interval below 60s floor must fail fast at startup");
        assertEquals("nop.err.metadata.ledger-config-invalid", ex3.getErrorCode());
    }

    // ==================== 测试载体 ====================

    /** 捕获调度参数的 timer（wiring 断言载体）。 */
    static class CapturingTimer implements IScheduledExecutor {
        final AtomicReference<String> taskName = new AtomicReference<>();
        final AtomicReference<Long> delayMillis = new AtomicReference<>();
        final AtomicReference<Runnable> command = new AtomicReference<>();
        final AtomicReference<Boolean> cancelled = new AtomicReference<>(false);

        class ObservedFuture extends CompletableFuture<Object> {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                cancelled.set(true);
                return super.cancel(mayInterruptIfRunning);
            }
        }

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit,
                                                String taskName, IPeriodicTaskListener listener) {
            this.taskName.set(taskName);
            this.delayMillis.set(unit.toMillis(delay));
            this.command.set(command);
            return new ObservedFuture();
        }

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            this.command.set(command);
            this.delayMillis.set(unit.toMillis(delay));
            return new ObservedFuture();
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        /**
         * 生产形态为 BindScheduledExecutor 包装（timer 只触发、任务体 hop worker）；本捕获载体
         * 原样返回 this，使六参绝缘入口的调度参数（taskName/interval/command）可被直接观测。
         */
        @Override
        public IScheduledExecutor executeOn(java.util.concurrent.Executor executor) {
            return this;
        }

        @Override
        public <V> CompletableFuture<V> schedule(java.util.concurrent.Callable<V> callable, long delay, TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public <V> CompletableFuture<V> submit(java.util.concurrent.Callable<V> callable) {
            return new CompletableFuture<>();
        }

        @Override
        public <V> CompletableFuture<V> submit(Runnable task, V result) {
            return new CompletableFuture<>();
        }

        @Override
        public void destroy() {
            // 测试载体：无可释放资源
        }

        @Override
        public void refreshConfig() {
            // 测试载体：无热更面
        }

        @Override
        public String getName() {
            return "capturing-timer";
        }

        @Override
        public ThreadPoolConfig getConfig() {
            return null;
        }

        @Override
        public ThreadPoolStats stats() {
            return null;
        }
    }

    // ==================== Helpers ====================

    private NopMetaLedgerCleaner newCleaner() {
        NopMetaLedgerCleaner cleaner = new NopMetaLedgerCleaner();
        cleaner.setDaoProvider(daoProvider);
        return cleaner;
    }

    private static final Map<String, String> PK_PROP_BY_ENTITY = Map.of(
            "NopMetaQualityResult", "qualityResultId",
            "NopMetaQualityScore", "qualityScoreId",
            "NopMetaCatalog", "metaCatalogId",
            "NopMetaProfilingResult", "profilingResultId",
            "NopMetaReconciliationResult", "resultId",
            "NopMetaModelChangedEvent", "modelChangedEventId");

    private void backdateCreateTime(String entityName, String pk, long ageMillis) {
        SQL sql = SQL.begin()
                .sql("update " + entityName + " o set o.createTime = ? where o." + PK_PROP_BY_ENTITY.get(entityName) + " = ?",
                        new Timestamp(System.currentTimeMillis() - ageMillis), pk)
                .end();
        ormTemplate.executeUpdate(sql);
    }

    /** 最小化仿写 QualityResultWriter.append（seq PK 自动生成，返回生成 PK）。 */
    private String seedQualityResult(String ruleId) {
        NopMetaQualityResult row = qualityResultDao().newEntity();
        row.setQualityRuleId(ruleId);
        row.setExecuteTime(new Timestamp(System.currentTimeMillis()));
        row.setStatus("PASS");
        qualityResultDao().saveEntity(row);
        return row.getQualityResultId();
    }

    private String seedQualityScore(String entityId) {
        NopMetaQualityScore row = qualityScoreDao().newEntity();
        row.setMetaEntityId(entityId);
        row.setScoreTime(new Timestamp(System.currentTimeMillis()));
        row.setOverallScore(90.0);
        qualityScoreDao().saveEntity(row);
        return row.getQualityScoreId();
    }

    private String seedCatalog(String entityId) {
        NopMetaCatalog row = catalogDao().newEntity();
        row.setMetaEntityId(entityId);
        row.setRowCount(1L);
        row.setCollectedAt(new Timestamp(System.currentTimeMillis()));
        catalogDao().saveEntity(row);
        return row.getMetaCatalogId();
    }

    private String seedProfilingResult(String entityId) {
        NopMetaProfilingResult row = profilingResultDao().newEntity();
        row.setMetaEntityId(entityId);
        row.setSnapshotTime(new Timestamp(System.currentTimeMillis()));
        profilingResultDao().saveEntity(row);
        return row.getProfilingResultId();
    }

    private String seedReconciliationResult(String entityId) {
        NopMetaReconciliationResult row = reconciliationResultDao().newEntity();
        row.setConfigId("cfg-w02");
        row.setMetaEntityId(entityId);
        row.setExecuteTime(new Timestamp(System.currentTimeMillis()));
        reconciliationResultDao().saveEntity(row);
        return row.getResultId();
    }

    private String seedModelChangedEvent(String entityId) {
        NopMetaModelChangedEvent row = modelChangedEventDao().newEntity();
        row.setEventType("save");
        row.setEntityType("NopMetaEntity");
        row.setEntityId(entityId);
        row.setChangeSource("test");
        row.setChangeTime(new Timestamp(System.currentTimeMillis()));
        row.setBeforeSnapshot(SNAPSHOT_PAYLOAD);
        row.setAfterSnapshot(SNAPSHOT_PAYLOAD);
        modelChangedEventDao().saveEntity(row);
        return row.getModelChangedEventId();
    }

    private IEntityDao<NopMetaQualityResult> qualityResultDao() {
        return daoProvider.daoFor(NopMetaQualityResult.class);
    }

    private IEntityDao<NopMetaQualityScore> qualityScoreDao() {
        return daoProvider.daoFor(NopMetaQualityScore.class);
    }

    private IEntityDao<NopMetaCatalog> catalogDao() {
        return daoProvider.daoFor(NopMetaCatalog.class);
    }

    private IEntityDao<NopMetaProfilingResult> profilingResultDao() {
        return daoProvider.daoFor(NopMetaProfilingResult.class);
    }

    private IEntityDao<NopMetaReconciliationResult> reconciliationResultDao() {
        return daoProvider.daoFor(NopMetaReconciliationResult.class);
    }

    private IEntityDao<NopMetaModelChangedEvent> modelChangedEventDao() {
        return daoProvider.daoFor(NopMetaModelChangedEvent.class);
    }
}
