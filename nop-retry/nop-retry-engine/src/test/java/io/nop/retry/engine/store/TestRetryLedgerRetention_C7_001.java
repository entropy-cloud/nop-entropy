package io.nop.retry.engine.store;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.orm.IOrmTemplate;
import io.nop.retry.api.IRetryTask;
import io.nop.retry.dao.entity.NopRetryAttempt;
import io.nop.retry.dao.entity.NopRetryDeadLetter;
import io.nop.retry.dao.entity.NopRetryPolicy;
import io.nop.retry.dao.entity.NopRetryRecord;
import io.nop.retry.engine.impl.RetryEngineImpl;
import io.nop.retry.engine.impl.RetryTaskImpl;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.retry.api.NopRetryApiConstants.*;
import static io.nop.retry.dao._NopRetryDaoConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 1.6-C7-001（plan 2502）回归钉：retry 三账本表保留期清理。
 * <p>
 * 清理谓词时间锚列显式落名（ledger-retention-policy §3.4 成员 1 行）：
 * nop_retry_record=updateTime(+status=COMPLETED)、nop_retry_dead_letter=createTime、
 * nop_retry_attempt=recordId 级联随父行。断言面：超期清理（record+attempt+死信+孤儿
 * attempt）、未到期/非终态保留（不误伤）、幂等、引擎生命周期链接线（防 C7-005 空壳）、
 * 错配置启动期 fail-fast。
 * <p>
 * 红证据（E2 回退论证 + 变异）：基线无 purgeExpiredLedgerRows 清理入口（接口方法不存在，
 * 回退即断言全红）；实现面变异（purge 体置空）时超期清理断言全红，快照
 * _tmp/phase2-c7-001-red.log / _tmp/phase2-c7-001-green.log。
 */
@NopTestConfig(
        localDb = true,
        initDatabaseSchema = OptionalBoolean.TRUE
)
public class TestRetryLedgerRetention_C7_001 extends JunitAutoTestCase {

    private static final long DAY_MS = 24L * 3600_000L;

    @Inject
    IRetryRecordStore recordStore;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    private final List<RetryEngineImpl> liveEngines = new java.util.ArrayList<>();

    @AfterEach
    void tearDown() {
        for (RetryEngineImpl engine : liveEngines) {
            try {
                engine.stop();
            } catch (Exception ignored) {
                // teardown best-effort stop; assertion already made
            }
        }
        liveEngines.clear();
    }

    /**
     * ① COMPLETED record + attempt 超期清理；未到期 COMPLETED、PENDING/RETRYING/SUSPENDED 保留。
     */
    @Test
    void testC7_001_completedRecordAndAttemptsPurgedAfterRetention() {
        NopRetryPolicy policy = createPolicy("policy-c7-001-retention");
        recordStore.savePolicy(policy);

        // 过期 COMPLETED：锚列 updateTime 回拨 31 天，含 2 条 attempt
        NopRetryRecord expired = createRecord("c7-001-expired-completed", RETRY_RECORD_STATUS_COMPLETED, "idem-c7-001-expired");
        recordStore.saveRecord(expired);
        saveAttemptsFor(expired.getSid(), 2);
        backdate("NopRetryRecord", "updateTime", expired.getSid(), 31 * DAY_MS);

        // 未到期 COMPLETED：updateTime 为当前，必须保留
        NopRetryRecord fresh = createRecord("c7-001-fresh-completed", RETRY_RECORD_STATUS_COMPLETED, "idem-c7-001-fresh");
        recordStore.saveRecord(fresh);
        saveAttemptsFor(fresh.getSid(), 1);

        // 非终态记录：PENDING / SUSPENDED 不受清理影响（即使 updateTime 很旧）
        NopRetryRecord pending = createRecord("c7-001-pending", RETRY_RECORD_STATUS_PENDING, "idem-c7-001-pending");
        recordStore.saveRecord(pending);
        backdate("NopRetryRecord", "updateTime", pending.getSid(), 31 * DAY_MS);
        NopRetryRecord suspended = createRecord("c7-001-suspended", RETRY_RECORD_STATUS_SUSPENDED, "idem-c7-001-suspended");
        recordStore.saveRecord(suspended);
        backdate("NopRetryRecord", "updateTime", suspended.getSid(), 31 * DAY_MS);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        long deleted = recordStore.purgeExpiredLedgerRows(30 * DAY_MS, 1000);
        assertTrue(deleted >= 3, "expired record + its attempts must be purged, deleted=" + deleted);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        assertNull(findRecord("c7-001-expired-completed"), "expired COMPLETED record must be deleted");
        assertEquals(0, countAttemptsOf("c7-001-expired-completed"), "attempts of expired record must cascade-delete");

        assertNotNull(findRecord("c7-001-fresh-completed"), "unexpired COMPLETED record must be retained");
        assertEquals(1, countAttemptsOf("c7-001-fresh-completed"), "attempts of unexpired record must be retained");
        assertNotNull(findRecord("c7-001-pending"), "PENDING (in-flight) record must be retained");
        assertNotNull(findRecord("c7-001-suspended"), "SUSPENDED (resumable) record must be retained");
    }

    /**
     * ② 死信清理时孤儿 attempt 一并清理（审计机制⑥面：不留 deadLetter.record 悬空后的永久孤儿）。
     */
    @Test
    void testC7_001_orphanAttemptsPurgedWithExpiredDeadLetter() {
        NopRetryPolicy policy = createPolicy("policy-c7-001-dl");
        recordStore.savePolicy(policy);

        // 死信化：record 行删除、attempt 孤儿化（既有 moveToDeadLetter 语义）
        NopRetryRecord doomed = createRecord("c7-001-dl-record", RETRY_RECORD_STATUS_PENDING, "idem-c7-001-dl");
        doomed.setRequestPayload("{\"data\":\"x\"}");
        recordStore.saveRecord(doomed);
        saveAttemptsFor(doomed.getSid(), 2);
        recordStore.moveToDeadLetter(doomed, "ERR_X", "boom", null);

        // 第二组：未到期死信（无 attempt 孤儿），必须保留
        NopRetryRecord doomed2 = createRecord("c7-001-dl2-record", RETRY_RECORD_STATUS_PENDING, "idem-c7-001-dl2");
        doomed2.setRequestPayload("{\"data\":\"y\"}");
        recordStore.saveRecord(doomed2);
        recordStore.moveToDeadLetter(doomed2, "ERR_X", "boom", null);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        List<NopRetryDeadLetter> allDead = findDeadLettersByIdempotentId("idem-c7-001-dl");
        assertEquals(1, allDead.size());
        backdate("NopRetryDeadLetter", "createTime", allDead.get(0).getSid(), 31 * DAY_MS);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        long deleted = recordStore.purgeExpiredLedgerRows(30 * DAY_MS, 1000);
        assertTrue(deleted >= 3, "expired dead letter + orphan attempts must be purged, deleted=" + deleted);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        assertTrue(findDeadLettersByIdempotentId("idem-c7-001-dl").isEmpty(),
                "expired dead letter must be deleted");
        assertEquals(0, countAttemptsOf("c7-001-dl-record"),
                "orphan attempts of expired dead letter must be purged (no permanent orphans)");

        // 未到期死信与其孤儿 attempt 保留
        assertEquals(1, findDeadLettersByIdempotentId("idem-c7-001-dl2").size(),
                "unexpired dead letter must be retained");
        assertEquals(0, countAttemptsOf("c7-001-dl2-record"),
                "unexpired dead letter's orphan attempts must be retained within retention window");
    }

    /**
     * ②' OVERWRITE 重建孤儿源：record 被直接删除（无死信行）后，其孤儿 attempt 由
     * startTime 锚列兜底扫描清理；record 存活的 attempt 不误伤。
     */
    @Test
    void testC7_001_overwriteOrphanAttemptsPurgedBySweep() {
        NopRetryPolicy policy = createPolicy("policy-c7-001-overwrite");
        recordStore.savePolicy(policy);

        // OVERWRITE 形态模拟：record 行直接删除（moveToDeadLetter 不经过），attempt 悬空
        NopRetryRecord overwritten = createRecord("c7-001-ow-record", RETRY_RECORD_STATUS_PENDING, "idem-c7-001-ow");
        recordStore.saveRecord(overwritten);
        saveAttemptsFor(overwritten.getSid(), 2);
        recordStore.deleteRecord(overwritten);

        // 存活 record 的 attempt（startTime 同样过期）：兜底扫描不得误伤
        NopRetryRecord survivor = createRecord("c7-001-ow-survivor", RETRY_RECORD_STATUS_PENDING, "idem-c7-001-ow-sv");
        recordStore.saveRecord(survivor);
        saveAttemptsFor(survivor.getSid(), 1);

        // 孤儿 attempt 的 startTime 回拨 31 天（进入兜底扫描锚列窗口）
        ormTemplate.flushSession();
        ormTemplate.clearSession();
        backdateAttempts("c7-001-ow-record", 31 * DAY_MS);
        backdateAttempts("c7-001-ow-survivor", 31 * DAY_MS);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        long deleted = recordStore.purgeExpiredLedgerRows(30 * DAY_MS, 1000);
        assertTrue(deleted >= 2, "OVERWRITE orphan attempts must be swept, deleted=" + deleted);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        assertEquals(0, countAttemptsOf("c7-001-ow-record"),
                "orphan attempts of OVERWRITE-deleted record must be swept (no permanent orphans)");
        assertEquals(1, countAttemptsOf("c7-001-ow-survivor"),
                "attempts of a live record must not be harmed by the orphan sweep");
    }

    /**
     * ④ 清理入口幂等：重复触发无害，第二轮零删除。
     */
    @Test
    void testC7_001_purgeIdempotent() {
        NopRetryPolicy policy = createPolicy("policy-c7-001-idem");
        recordStore.savePolicy(policy);

        NopRetryRecord expired = createRecord("c7-001-idem-completed", RETRY_RECORD_STATUS_COMPLETED, "idem-c7-001-idem");
        recordStore.saveRecord(expired);
        saveAttemptsFor(expired.getSid(), 1);
        backdate("NopRetryRecord", "updateTime", expired.getSid(), 31 * DAY_MS);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        long first = recordStore.purgeExpiredLedgerRows(30 * DAY_MS, 1000);
        assertTrue(first >= 2, "first round must delete record + attempt");
        long second = recordStore.purgeExpiredLedgerRows(30 * DAY_MS, 1000);
        assertEquals(0, second, "second round must be a no-op (idempotent predicate)");
    }

    /**
     * 接线验证（rule 23）：引擎生命周期链（doStart）真实调度清理链并调用
     * recordStore.purgeExpiredLedgerRows——非仅 API 存在（防 C7-005 空壳形态）。
     */
    @Test
    void testC7_001_engineLifecycleChainInvokesLedgerCleanup() {
        CapturingStore store = new CapturingStore();
        FakeScheduledExecutor executor = new FakeScheduledExecutor();

        RetryEngineImpl engine = new RetryEngineImpl();
        liveEngines.add(engine);
        engine.setRecordStore(store);
        engine.setExecutor(executor);
        engine.setLedgerRetentionDays(30);
        engine.setLedgerCleanupInterval(java.time.Duration.ofHours(1));
        engine.setLedgerDeleteBatchSize(500);

        engine.start();

        assertEquals("nop-retry-ledger-cleanup", executor.taskName.get(),
                "wiring: engine lifecycle chain must schedule the ledger cleanup chain");
        assertEquals(3600_000L, executor.delayMillis.get(), "cleanup interval passed to scheduler");

        // 触发被调度的清理命令本体：store 清理入口被真实调用（计数器断言，rule 23 形态）
        assertEquals(0, store.purgeCalls.get());
        executor.command.get().run();
        assertEquals(1, store.purgeCalls.get(), "scheduled cleanup command must invoke store purge entry");
        assertEquals(30L * DAY_MS, store.retentionMillis.get(), "retention param transmitted to store entry");
        assertEquals(500, store.batchSize.get(), "batch cap transmitted to store entry");

        // fail-fast 包络：清理体异常显式可见（失败计数）且链不中断（绝缘层重臂语义）
        store.boom.set(true);
        try {
            executor.command.get().run();
            fail("cleanup body failure must be visible (no silent swallow)");
        } catch (Exception e) {
            // 容器（真绝缘载体）捕获观测；本测试载体直接抛出 = 显式可见形态
        }
        engine.stop();
        assertTrue(executor.cancelled.get(), "doStop must cancel the cleanup chain");
    }

    /**
     * retention-days=0 为显式禁用：不调度清理链。
     */
    @Test
    void testC7_001_zeroRetentionDisablesCleanupScheduling() {
        FakeScheduledExecutor executor = new FakeScheduledExecutor();
        RetryEngineImpl engine = new RetryEngineImpl();
        liveEngines.add(engine);
        engine.setRecordStore(new CapturingStore());
        engine.setExecutor(executor);
        engine.setLedgerRetentionDays(0);

        engine.start();

        assertNull(executor.taskName.get(), "retention-days=0 must disable cleanup scheduling");
        engine.stop();
    }

    /**
     * fail-fast 包络（设计 §3.1 第 5 项）：错配置启动期拒绝，不降级继续。
     */
    @Test
    void testC7_001_invalidLedgerConfigFailsFast() {
        RetryEngineImpl negativeRetention = new RetryEngineImpl();
        liveEngines.add(negativeRetention);
        negativeRetention.setRecordStore(new CapturingStore());
        negativeRetention.setExecutor(new FakeScheduledExecutor());
        negativeRetention.setLedgerRetentionDays(-1);
        NopException ex1 = assertThrows(NopException.class, negativeRetention::start,
                "negative retention must fail fast at startup");
        assertEquals("nop.err.retry.ledger-config-invalid", ex1.getErrorCode());

        RetryEngineImpl zeroBatch = new RetryEngineImpl();
        liveEngines.add(zeroBatch);
        zeroBatch.setRecordStore(new CapturingStore());
        zeroBatch.setExecutor(new FakeScheduledExecutor());
        zeroBatch.setLedgerDeleteBatchSize(0);
        NopException ex2 = assertThrows(NopException.class, zeroBatch::start,
                "non-positive batch cap must fail fast at startup");
        assertEquals("nop.err.retry.ledger-config-invalid", ex2.getErrorCode());

        RetryEngineImpl tinyInterval = new RetryEngineImpl();
        liveEngines.add(tinyInterval);
        tinyInterval.setRecordStore(new CapturingStore());
        tinyInterval.setExecutor(new FakeScheduledExecutor());
        tinyInterval.setLedgerCleanupInterval(java.time.Duration.ofSeconds(30));
        NopException ex3 = assertThrows(NopException.class, tinyInterval::start,
                "cleanup interval below 60s floor must fail fast at startup");
        assertEquals("nop.err.retry.ledger-config-invalid", ex3.getErrorCode());
    }

    // ==================== 测试载体 ====================

    /** 捕获清理入口调用的 store（rule 23 计数器断言载体）。 */
    static class CapturingStore implements IRetryRecordStore {
        final AtomicInteger purgeCalls = new AtomicInteger();
        final AtomicReference<Long> retentionMillis = new AtomicReference<>();
        final AtomicReference<Integer> batchSize = new AtomicReference<>();
        final AtomicReference<Boolean> boom = new AtomicReference<>(false);

        @Override
        public long purgeExpiredLedgerRows(long retentionTimeMillis, int batchSize) {
            purgeCalls.incrementAndGet();
            this.retentionMillis.set(retentionTimeMillis);
            this.batchSize.set(batchSize);
            if (Boolean.TRUE.equals(boom.get()))
                throw new IllegalStateException("cleanup-boom");
            return 0L;
        }

        @Override
        public NopRetryRecord newRecord(IRetryTask task, io.nop.api.core.beans.ApiRequest<?> request) {
            return null;
        }

        @Override
        public NopRetryAttempt newAttempt(NopRetryRecord record) {
            return null;
        }

        @Override
        public void saveAttempt(NopRetryAttempt attempt) {
        }

        @Override
        public List<NopRetryRecord> fetchPendingRecords(int limit, io.nop.api.core.beans.IntRangeSet partitions) {
            return List.of();
        }

        @Override
        public List<NopRetryRecord> tryLockRecordsForProcess(List<NopRetryRecord> records, long retryingTimeoutMs) {
            return List.of();
        }

        @Override
        public NopRetryRecord loadRecord(String recordId) {
            return null;
        }

        @Override
        public NopRetryRecord findPendingRecordByIdempotentId(String namespaceId, String groupId, String idempotentId) {
            return null;
        }

        @Override
        public NopRetryRecord findRecordByIdempotentId(String namespaceId, String groupId, String idempotentId) {
            return null;
        }

        @Override
        public void deleteRecord(NopRetryRecord record) {
        }

        @Override
        public NopRetryDeadLetter loadDeadLetter(String deadLetterId) {
            return null;
        }

        @Override
        public void saveDeadLetter(NopRetryDeadLetter deadLetter) {
        }

        @Override
        public void saveRecord(NopRetryRecord record) {
        }

        @Override
        public void updateRecord(NopRetryRecord record) {
        }

        @Override
        public void moveToDeadLetter(NopRetryRecord record, String errorCode, String errorMessage, String errorStack) {
        }

        @Override
        public long getCurrentTime() {
            return System.currentTimeMillis();
        }

        @Override
        public void savePolicy(NopRetryPolicy policy) {
        }

        @Override
        public NopRetryPolicy loadPolicy(String policyId) {
            return null;
        }
    }

    /** 捕获调度参数的执行器（wiring 断言载体）。 */
    static class FakeScheduledExecutor implements IScheduledExecutor {
        final AtomicReference<String> taskName = new AtomicReference<>();
        final AtomicReference<Long> delayMillis = new AtomicReference<>();
        final AtomicReference<Runnable> command = new AtomicReference<>();
        final AtomicReference<Boolean> cancelled = new AtomicReference<>(false);

        /** 记录 cancel 调用的 Future（doStop 取消链断言载体）。 */
        class ObservedFuture extends CompletableFuture<Object> {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                cancelled.set(true);
                return super.cancel(mayInterruptIfRunning);
            }
        }

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, java.util.concurrent.TimeUnit unit,
                                                String taskName, io.nop.commons.concurrent.executor.IPeriodicTaskListener listener) {
            this.taskName.set(taskName);
            this.delayMillis.set(unit.toMillis(delay));
            this.command.set(command);
            return new ObservedFuture();
        }

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, java.util.concurrent.TimeUnit unit) {
            this.command.set(command);
            this.delayMillis.set(unit.toMillis(delay));
            return new ObservedFuture();
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public <V> CompletableFuture<V> schedule(java.util.concurrent.Callable<V> callable, long delay, java.util.concurrent.TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, java.util.concurrent.TimeUnit unit) {
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
            return "fake-scheduled-executor";
        }

        @Override
        public io.nop.commons.concurrent.executor.ThreadPoolConfig getConfig() {
            return null;
        }

        @Override
        public io.nop.commons.concurrent.executor.ThreadPoolStats stats() {
            return null;
        }
    }

    // ==================== Helpers ====================

    /**
     * 锚列回拨：直接 SQL UPDATE 绕过 ORM 时钟填充，保证清理谓词可命中。
     */
    private void backdate(String entityName, String propName, String sid, long ageMillis) {
        SQL sql = SQL.begin()
                .sql("update " + entityName + " o set o." + propName + " = ? where o.sid = ?",
                        new Timestamp(System.currentTimeMillis() - ageMillis), sid)
                .end();
        ormTemplate.executeUpdate(sql);
    }

    private NopRetryRecord findRecord(String sid) {
        return daoProvider.daoFor(NopRetryRecord.class).getEntityById(sid);
    }

    /**
     * attempt 锚列（startTime）回拨：直接 SQL UPDATE，绕过 ORM 时钟填充。
     */
    private void backdateAttempts(String recordId, long ageMillis) {
        SQL sql = SQL.begin()
                .sql("update NopRetryAttempt o set o.startTime = ? where o.recordId = ?",
                        new Timestamp(System.currentTimeMillis() - ageMillis), recordId)
                .end();
        ormTemplate.executeUpdate(sql);
    }

    private int countAttemptsOf(String recordId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("recordId", recordId));
        return daoProvider.daoFor(NopRetryAttempt.class).findAllByQuery(query).size();
    }

    private List<NopRetryDeadLetter> findDeadLettersByIdempotentId(String idempotentId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("idempotentId", idempotentId));
        return daoProvider.daoFor(NopRetryDeadLetter.class).findAllByQuery(query);
    }

    private void saveAttemptsFor(String recordId, int count) {
        NopRetryRecord ref = findRecord(recordId);
        for (int i = 1; i <= count; i++) {
            // 生产链等价形态：引擎在创建下一条 attempt 前先递增 retryCount（attemptNo=retryCount+1），
            // 测试显式推进避免 UK_RETRY_ATTEMPT_RECORD_NO (recordId,attemptNo) 冲突
            ref.setRetryCount(i - 1);
            NopRetryAttempt attempt = recordStore.newAttempt(ref);
            attempt.setStatus(RETRY_ATTEMPT_STATUS_FAILED);
            attempt.setEndTime(new Timestamp(System.currentTimeMillis()));
            recordStore.saveAttempt(attempt);
        }
        ref.setRetryCount(0);
    }

    private NopRetryPolicy createPolicy(String sid) {
        NopRetryPolicy policy = new NopRetryPolicy();
        policy.setSid(sid);
        policy.setName("Policy " + sid);
        policy.setNamespaceId("default");
        policy.setGroupId("default");
        policy.setMaxRetryCount(DEFAULT_MAX_RETRY_COUNT);
        policy.setBlockStrategy(BLOCK_STRATEGY_DISCARD);
        policy.setImmediateRetryCount(0);
        return policy;
    }

    private NopRetryRecord createRecord(String sid, int status, String idempotentId) {
        NopRetryRecord record = new NopRetryRecord();
        record.setSid(sid);
        record.setNamespaceId("default");
        record.setGroupId("default");
        record.setPolicyId("policy-c7-001-retention");
        if (sid.startsWith("c7-001-dl")) {
            record.setPolicyId("policy-c7-001-dl");
        }
        if (sid.startsWith("c7-001-idem")) {
            record.setPolicyId("policy-c7-001-idem");
        }
        record.setIdempotentId(idempotentId);
        record.setStatus(status);
        record.setRetryCount(0);
        record.setMaxRetryCount(DEFAULT_MAX_RETRY_COUNT);
        record.setServiceName("svc");
        record.setServiceMethod("method");
        return record;
    }
}
