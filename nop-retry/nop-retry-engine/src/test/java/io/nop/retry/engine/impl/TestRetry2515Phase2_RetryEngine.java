/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.engine.impl;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.dao.DaoErrors;
import io.nop.dao.api.IDaoProvider;
import io.nop.retry.api.IRetryTask;
import io.nop.retry.dao.entity.NopRetryAttempt;
import io.nop.retry.dao.entity.NopRetryDeadLetter;
import io.nop.retry.dao.entity.NopRetryPolicy;
import io.nop.retry.dao.entity.NopRetryRecord;
import io.nop.retry.engine.store.IRetryRecordStore;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.retry.api.NopRetryApiConstants.*;
import static io.nop.retry.dao._NopRetryDaoConstants.*;
import static io.nop.retry.dao.entity._gen._NopRetryRecord.*;
import static io.nop.retry.engine.NopRetryErrors.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * plan 2515 Phase 2 retry 组 focused 回归（先红后绿，C10 收口门随同 diff）：
 * <ul>
 * <li>C3-003（裁定④ / C10-006）：续租协议——扫描器接管后旧链续租失败败方显式中止，零幽灵副作用</li>
 * <li>C3-005（裁定⑤ / C10-005）：OVERWRITE 幽灵链——代际短路，旧链定时器不再发起 attempt</li>
 * <li>C6-003（裁定⑤ / C10-005 / R10）：OVERWRITE 原子替换失败 → 旧记录死信快照，零蒸发</li>
 * <li>C3-006（裁定⑥ / C10-001 真异常注入）：attempt 簿记失败不改判业务结论（ERROR+计数可见）</li>
 * <li>C8-012（裁定⑥ / C10-001）：T1/T2 同步簿记异常——返回 future 必有终态，join 冻结不可再现</li>
 * <li>C8-017（裁定⑥契约半边 / C10-008 / R21 同 diff）：提交面同步抛转 failedStage 单通道交付</li>
 * </ul>
 * 红绿证据快照：_tmp/red-green-2515-retry-c3-003|c3-005|c6-003|c3-006|c8-012|c8-017-{red,green}.log
 */
@NopTestConfig(
        localDb = true,
        initDatabaseSchema = OptionalBoolean.TRUE
)
public class TestRetry2515Phase2_RetryEngine extends JunitAutoTestCase {

    static {
        // 与 TestRetryEngineImpl 同理：隔离后台扫描器对断言的干扰
        System.setProperty("nop.retry.scanner.scan-interval-ms", String.valueOf(3600_000L));
    }

    @Inject
    RetryEngineImpl retryEngine;

    @Inject
    IRetryRecordStore recordStore;

    @Inject
    MockRpcServiceInvoker rpcInvoker;

    @Inject
    IDaoProvider daoProvider;

    private IRetryRecordStore baseStore;

    @BeforeEach
    void setUp() {
        rpcInvoker.reset();
        baseStore = recordStore;
    }

    @AfterEach
    void tearDown() {
        // 防故障注入 store 泄漏到其他用例
        retryEngine.setRecordStore(baseStore);
    }

    // ==================== C3-003 裁定④：续租协议（红④ / C10-006） ====================

    /**
     * 红④：扫描器 tryLock 接管（版本 CAS 抢持租约）后，旧调用链每轮 attempt 前的续租 CAS
     * 必须失败——败方显式中止（ERR_RETRY_LEASE_LOST + failedStage），不发起任何 attempt、
     * 不双执行业务副作用。缺陷态（无续租检查）：旧链照常执行（接管双执行红）。
     */
    @Test
    void testC3_003_leaseLostAfterScannerTakeover_chainAbortsWithoutSideEffect() throws Exception {
        NopRetryPolicy policy = createPolicy("policy-2515-lease", 1);
        savePolicyIfAbsent(policy);

        NopRetryRecord record = createRecord("rec-2515-lease", "idem-2515-lease", "policy-2515-lease");
        recordStore.saveRecord(record);

        // 模拟租约到期扫描器接管：tryLock 版本 CAS 抢持（PENDING→RETRYING + 推进 nextTriggerTime）
        NopRetryRecord takenOver = recordStore.loadRecord(record.getSid());
        List<NopRetryRecord> locked =
                recordStore.tryLockRecordsForProcess(List.of(takenOver), DEFAULT_RETRYING_TIMEOUT_MS);
        assertEquals(1, locked.size(), "scanner takeover must win the version CAS");

        rpcInvoker.setResponse(ApiResponse.success("ok"));

        // 旧调用链（持有接管前的记录实体）续跑：续租 CAS 0 行 → 败方显式中止
        CompletableFuture<ApiResponse<?>> future =
                retryEngine.executeRetryFromScanner(record, null).toCompletableFuture();

        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof NopException,
                "cause should be NopException but was: " + ex.getCause());
        assertEquals(ERR_RETRY_LEASE_LOST.getErrorCode(), ((NopException) ex.getCause()).getErrorCode(),
                "loser chain must abort explicitly with ERR_RETRY_LEASE_LOST");
        assertEquals(0, rpcInvoker.getInvocationCount(),
                "lease-loser chain must not fire any attempt (no double execution)");
    }

    // ==================== C3-005 裁定⑤：OVERWRITE 幽灵链代际短路（红⑤ / C10-005） ====================

    /**
     * 红⑤：OVERWRITE 重提 × 旧链在途（已调度续跑定时器）。缺陷态：旧链定时器到期照常发起
     * attempt（幽灵任务双执行，RPC 3 次）；修复后：代际失配短路，旧链 future 以
     * ERR_RETRY_RECORD_OVERWRITTEN 异常完成，业务副作用只执行 2 次（旧链首败 + 新链）。
     */
    @Test
    void testC3_005_overwriteResubmit_staleChainTimerShortCircuited() throws Exception {
        NopRetryPolicy policy = createPolicy("policy-2515-ghost", 2);
        policy.setBlockStrategy(BLOCK_STRATEGY_OVERWRITE);
        policy.setImmediateRetryIntervalMs(300L);
        savePolicyIfAbsent(policy);

        rpcInvoker.setResponses(
                errorResponse("e1"),             // 旧链 attempt1：失败 → 调度 +300ms 续跑定时器
                ApiResponse.success("new-chain") // OVERWRITE 新链 attempt：成功
        );

        ApiRequest<Object> request = new ApiRequest<>();
        request.setData(java.util.Map.of("k", "v"));

        IRetryTask task1 = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-2515-ghost")
                .withIdempotentId("idem-2515-ghost");
        CompletableFuture<ApiResponse<?>> oldChain =
                retryEngine.executeTask(task1, request, null).toCompletableFuture();

        // 等旧链 attempt1 回调完成（已调度续跑定时器、代际捕获完毕）后 OVERWRITE 重提
        Thread.sleep(100);
        IRetryTask task2 = retryEngine.newRetryTask("svc", "method")
                .withPolicyId("policy-2515-ghost")
                .withIdempotentId("idem-2515-ghost");
        ApiResponse<?> newChainResult = retryEngine.executeTask(task2, request, null)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertTrue(newChainResult.isOk());

        // 旧链续跑定时器到期：代际失配必须短路（不发起 attempt），future 异常完成（终态完备）
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> oldChain.get(5, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof NopException,
                "cause should be NopException but was: " + ex.getCause());
        assertEquals(ERR_RETRY_RECORD_OVERWRITTEN.getErrorCode(),
                ((NopException) ex.getCause()).getErrorCode(),
                "stale chain must abort with ERR_RETRY_RECORD_OVERWRITTEN");

        assertEquals(2, rpcInvoker.getInvocationCount(),
                "exactly old-chain first attempt + new chain must execute; stale timer must not fire an attempt");
    }

    // ==================== C6-003 裁定⑤：OVERWRITE 原子替换失败死信快照（红⑥ / C10-005 / R10） ====================

    /**
     * 红⑥：OVERWRITE 替换失败（约束类失败确定性注入）。缺陷态：delete 已提交 + save 失败 →
     * 任务整体蒸发且无死信快照（不可恢复丢失）；修复后：replaceRecord 抛出 → 旧记录死信快照
     * （failureCode=OVERWRITE_REPLACE_FAILED）+ failedStage 显式失败。
     */
    @Test
    void testC6_003_overwriteReplaceFailed_deadLetterSnapshot_noEvaporation() throws Exception {
        NopRetryPolicy policy = createPolicy("policy-2515-evap", 0);
        policy.setBlockStrategy(BLOCK_STRATEGY_OVERWRITE);
        savePolicyIfAbsent(policy);

        NopRetryRecord existing = createRecord("rec-2515-evap", "idem-2515-evap", "policy-2515-evap");
        recordStore.saveRecord(existing);

        // 故障注入：新行保存/原子替换确定性失败（约束类失败模拟）
        AtomicBoolean boom = new AtomicBoolean(true);
        IRetryRecordStore failingStore = new FailingOverwriteStore(baseStore, boom);
        retryEngine.setRecordStore(failingStore);
        try {
            rpcInvoker.setResponse(ApiResponse.success("ok"));
            IRetryTask task = retryEngine.newRetryTask("svc", "method")
                    .withPolicyId("policy-2515-evap")
                    .withIdempotentId("idem-2515-evap");

            CompletableFuture<ApiResponse<?>> future =
                    task.callAsync(new ApiRequest<>(), null).toCompletableFuture();
            assertThrows(ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS),
                    "replace failure must surface explicitly, not vanish");
        } finally {
            boom.set(false);
            retryEngine.setRecordStore(baseStore);
        }

        // 零蒸发：旧任务留下死信全量快照（可恢复），而非整体消失
        List<NopRetryDeadLetter> deadLetters = findDeadLetters("idem-2515-evap");
        assertEquals(1, deadLetters.size(),
                "C6-003/R10: replace failure must leave a dead-letter snapshot of the old task");
        assertEquals("OVERWRITE_REPLACE_FAILED", deadLetters.get(0).getFailureCode());
        assertEquals("svc", deadLetters.get(0).getServiceName());
        assertEquals(0, rpcInvoker.getInvocationCount(),
                "no business side-effect may run for an evaporated replacement");
    }

    // ==================== C3-006 裁定⑥：簿记失败不改判业务结论（红⑦ / C10-001） ====================

    /**
     * 红⑦（C10-001 真异常注入，非串行假保护）：二次 saveAttempt（attempt 终态落账）瞬时 DB
     * 故障注入。缺陷态：簿记异常内联完成 stage → 已成功的业务调用被改判失败并进入重试；
     * 修复后：业务结论保持成功、簿记失败 ERROR+计数显式可见。
     */
    @Test
    void testC3_006_attemptBookkeepingFailure_businessSuccessPreserved() throws Exception {
        NopRetryPolicy policy = createPolicy("policy-2515-bookkeep", 0);
        savePolicyIfAbsent(policy);

        // 真异常注入：attempt 终态落账（非首次插入）确定性抛出
        AtomicBoolean boom = new AtomicBoolean(true);
        IRetryRecordStore failingStore = new FailingAttemptSaveStore(baseStore, boom);
        retryEngine.setRecordStore(failingStore);
        try {
            rpcInvoker.setResponse(ApiResponse.success("business-ok"));
            IRetryTask task = retryEngine.newRetryTask("svc", "method")
                    .withPolicyId("policy-2515-bookkeep")
                    .withIdempotentId("idem-2515-bookkeep");

            ApiResponse<?> response = retryEngine.executeTask(task, new ApiRequest<>(), null)
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);

            assertTrue(response.isOk(), "business success must not be re-judged as failure by"
                    + " a transient bookkeeping DB error (C3-006, the most dangerous wrong direction)");
            assertEquals(1, rpcInvoker.getInvocationCount(),
                    "successful business call must not be retried");
            assertTrue(engineCounter("getAttemptBookkeepFailureCount") >= 1,
                    "bookkeeping failure must be explicitly visible (ERROR + counter)");
        } finally {
            boom.set(false);
            retryEngine.setRecordStore(baseStore);
        }

        NopRetryRecord record = findRecordByIdempotentId("idem-2515-bookkeep");
        assertNotNull(record);
        assertEquals(RETRY_RECORD_STATUS_COMPLETED, record.getStatus());
    }

    // ==================== C8-012 裁定⑥：T1/T2 同步异常 future 必有终态（红⑧ / C10-001） ====================

    /**
     * 红⑧ T1（doExecute 前置持久化同步 DB 异常）：缺陷态 executeRetryFromScanner 同步抛/
     * 返回 future 永不完成 → 扫描器 allOf().join() 永久冻结；修复后：簿记隔离 + Throwable 兜底，
     * 返回 future 异常完成（终态完备），扫描循环存活。
     */
    @Test
    void testC8_012_T1_prePersistSyncFailure_futureAlwaysCompletes() throws Exception {
        NopRetryPolicy policy = createPolicy("policy-2515-t1", 0);
        savePolicyIfAbsent(policy);

        NopRetryRecord record = createRecord("rec-2515-t1", "idem-2515-t1", "policy-2515-t1");
        recordStore.saveRecord(record);

        AtomicBoolean boom = new AtomicBoolean(true);
        IRetryRecordStore failingStore = new FailingAttemptSaveStore(baseStore, boom);
        retryEngine.setRecordStore(failingStore);
        try {
            rpcInvoker.setResponse(ApiResponse.success("ok"));
            CompletableFuture<ApiResponse<?>> future =
                    retryEngine.executeRetryFromScanner(record, null).toCompletableFuture();
            // 关键断言：future 有终态（有界返回），且不被簿记异常改判业务结果
            ApiResponse<?> response = future.get(10, TimeUnit.SECONDS);
            assertTrue(response.isOk());
            assertEquals(1, rpcInvoker.getInvocationCount());
            assertTrue(engineCounter("getAttemptBookkeepFailureCount") >= 1,
                    "T1 bookkeeping failure must be visible, not swallowed silently");
        } finally {
            boom.set(false);
            retryEngine.setRecordStore(baseStore);
        }
    }

    /**
     * 红⑧ T2（record 层回调体同步 DB 异常，重试续排路径）：缺陷态手工 promise 永不完成
     * （join 冻结、部分形态零日志）；修复后：簿记隔离 + 拓扑单一化，返回 future 异常完成
     * （业务原始异常原样传导，终态完备）。
     */
    @Test
    void testC8_012_T2_recordWriteSyncFailure_retryFutureAlwaysCompletes() throws Exception {
        NopRetryPolicy policy = createPolicy("policy-2515-t2", 1);
        savePolicyIfAbsent(policy);
        savePolicyIfAbsent(policy);

        // 真异常注入：record 层 updateRecord（终态结算路径）同步抛
        AtomicBoolean boom = new AtomicBoolean(true);
        IRetryRecordStore failingStore = new FailingRecordUpdateStore(baseStore, boom);
        retryEngine.setRecordStore(failingStore);
        try {
            rpcInvoker.setResponse(errorResponse("biz-e1"));
            IRetryTask task = retryEngine.newRetryTask("svc", "method")
                    .withPolicyId("policy-2515-t2")
                    .withIdempotentId("idem-2515-t2");

            CompletableFuture<ApiResponse<?>> future =
                    retryEngine.executeTask(task, new ApiRequest<>(), null).toCompletableFuture();

            // 关键断言：future 有界完成（业务原始失败结果原样交付），绝不冻结扫描器 join；
            // 簿记失败显式可见（ERROR + 计数），且不吞掉、不悬置
            ApiResponse<?> response = future.get(10, TimeUnit.SECONDS);
            assertNotNull(response);
            assertFalse(response.isOk(),
                    "original business failure result must be delivered (not re-judged by bookkeeping)");
            assertEquals(1, rpcInvoker.getInvocationCount());
            assertTrue(engineCounter("getRecordBookkeepFailureCount") >= 1,
                    "record bookkeeping failure must be explicitly visible (ERROR + counter)");
        } finally {
            boom.set(false);
            retryEngine.setRecordStore(baseStore);
        }
    }

    // ==================== C8-017 裁定⑥契约半边：提交面失败交付单通道（C10-008 / R21） ====================

    /**
     * C8-017 三通道不对称红：executeTask 同步抛（loadPolicy DB 读故障真异常注入）时，
     * callAsync 必须返回 failedStage future（同步抛通道收敛为 future 单通道），
     * fire-and-forget 调用方可经 future 感知失败。缺陷态：同步抛直接穿出 callAsync。
     */
    @Test
    void testC8_017_callAsync_syncThrowDeliveredAsFailedFuture() {
        NopRetryPolicy policy = createPolicy("policy-2515-c817", 0);
        savePolicyIfAbsent(policy);

        AtomicBoolean boom = new AtomicBoolean(true);
        IRetryRecordStore failingStore = new FailingLoadPolicyStore(baseStore, boom);
        retryEngine.setRecordStore(failingStore);
        try {
            IRetryTask task = retryEngine.newRetryTask("svc", "method")
                    .withPolicyId("policy-2515-c817")
                    .withIdempotentId("idem-2515-c817");

            // 关键契约：callAsync 不同步抛，失败经 future 单通道交付
            CompletableFuture<ApiResponse<?>> future = task.callAsync(new ApiRequest<>(), null)
                    .toCompletableFuture();
            ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS));
            assertTrue(ex.getCause() instanceof NopException,
                    "injected submit failure must be delivered via the future channel: " + ex.getCause());
        } finally {
            boom.set(false);
            retryEngine.setRecordStore(baseStore);
        }
    }

    // ==================== 故障注入 store（C10-001 真异常注入载体） ====================

    /** 纯委托基类：故障注入 store 只覆盖各自注入点，其余全部透传。 */
    static class DelegatingStore implements IRetryRecordStore {
        protected final IRetryRecordStore delegate;

        DelegatingStore(IRetryRecordStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public NopRetryRecord newRecord(IRetryTask task, ApiRequest<?> request) {
            return delegate.newRecord(task, request);
        }

        @Override
        public void saveRecord(NopRetryRecord record) {
            delegate.saveRecord(record);
        }

        @Override
        public void replaceRecord(NopRetryRecord existingRecord, NopRetryRecord newRecord) {
            delegate.replaceRecord(existingRecord, newRecord);
        }

        @Override
        public NopRetryAttempt newAttempt(NopRetryRecord record) {
            return delegate.newAttempt(record);
        }

        @Override
        public void saveAttempt(NopRetryAttempt attempt) {
            delegate.saveAttempt(attempt);
        }

        @Override
        public void reconcileStaleRunningAttempts(String recordId) {
            delegate.reconcileStaleRunningAttempts(recordId);
        }

        @Override
        public List<NopRetryRecord> fetchPendingRecords(int limit, io.nop.api.core.beans.IntRangeSet partitions) {
            return delegate.fetchPendingRecords(limit, partitions);
        }

        @Override
        public List<NopRetryRecord> tryLockRecordsForProcess(List<NopRetryRecord> records, long retryingTimeoutMs) {
            return delegate.tryLockRecordsForProcess(records, retryingTimeoutMs);
        }

        @Override
        public boolean renewLease(NopRetryRecord record, long leaseTimeoutMs) {
            return delegate.renewLease(record, leaseTimeoutMs);
        }

        @Override
        public NopRetryRecord loadRecord(String recordId) {
            return delegate.loadRecord(recordId);
        }

        @Override
        public NopRetryRecord findPendingRecordByIdempotentId(String namespaceId, String groupId, String idempotentId) {
            return delegate.findPendingRecordByIdempotentId(namespaceId, groupId, idempotentId);
        }

        @Override
        public NopRetryRecord findRecordByIdempotentId(String namespaceId, String groupId, String idempotentId) {
            return delegate.findRecordByIdempotentId(namespaceId, groupId, idempotentId);
        }

        @Override
        public void deleteRecord(NopRetryRecord record) {
            delegate.deleteRecord(record);
        }

        @Override
        public NopRetryDeadLetter loadDeadLetter(String deadLetterId) {
            return delegate.loadDeadLetter(deadLetterId);
        }

        @Override
        public void saveDeadLetter(NopRetryDeadLetter deadLetter) {
            delegate.saveDeadLetter(deadLetter);
        }

        @Override
        public void updateRecord(NopRetryRecord record) {
            delegate.updateRecord(record);
        }

        @Override
        public void moveToDeadLetter(NopRetryRecord record, String errorCode, String errorMessage, String errorStack) {
            delegate.moveToDeadLetter(record, errorCode, errorMessage, errorStack);
        }

        @Override
        public long getCurrentTime() {
            return delegate.getCurrentTime();
        }

        @Override
        public long purgeExpiredLedgerRows(long retentionTimeMillis, int batchSize) {
            return delegate.purgeExpiredLedgerRows(retentionTimeMillis, batchSize);
        }

        @Override
        public void savePolicy(NopRetryPolicy policy) {
            delegate.savePolicy(policy);
        }

        @Override
        public NopRetryPolicy loadPolicy(String policyId) {
            return delegate.loadPolicy(policyId);
        }
    }

    /** OVERWRITE 原子替换/新行保存确定性失败（红⑥注入点）。 */
    static class FailingOverwriteStore extends DelegatingStore {
        private final AtomicBoolean boom;

        FailingOverwriteStore(IRetryRecordStore delegate, AtomicBoolean boom) {
            super(delegate);
            this.boom = boom;
        }

        private NopException duplicateKeyBoom() {
            return new NopException(DaoErrors.ERR_SQL_DUPLICATE_KEY);
        }

        @Override
        public void saveRecord(NopRetryRecord record) {
            if (boom.get())
                throw duplicateKeyBoom();
            delegate.saveRecord(record);
        }

        @Override
        public void replaceRecord(NopRetryRecord existingRecord, NopRetryRecord newRecord) {
            if (boom.get())
                throw duplicateKeyBoom();
            delegate.replaceRecord(existingRecord, newRecord);
        }
    }

    /** attempt 落账（saveAttempt）确定性失败（红⑦/T1 注入点）。 */
    static class FailingAttemptSaveStore extends DelegatingStore {
        private final AtomicBoolean boom;

        FailingAttemptSaveStore(IRetryRecordStore delegate, AtomicBoolean boom) {
            super(delegate);
            this.boom = boom;
        }

        @Override
        public void saveAttempt(NopRetryAttempt attempt) {
            if (boom.get())
                throw new NopException(ERR_RETRY_EXECUTION_FAILED)
                    .param(ARG_RECORD_ID, "injected-transient-db-failure-on-saveAttempt");
            delegate.saveAttempt(attempt);
        }
    }

    /** record 层 updateRecord 确定性失败（红⑧ T2 注入点）。 */
    static class FailingRecordUpdateStore extends DelegatingStore {
        private final AtomicBoolean boom;

        FailingRecordUpdateStore(IRetryRecordStore delegate, AtomicBoolean boom) {
            super(delegate);
            this.boom = boom;
        }

        @Override
        public void updateRecord(NopRetryRecord record) {
            if (boom.get())
                throw new NopException(ERR_RETRY_EXECUTION_FAILED)
                    .param(ARG_RECORD_ID, "injected-transient-db-failure-on-updateRecord");
            delegate.updateRecord(record);
        }
    }

    /** loadPolicy 确定性失败（C8-017 提交面同步抛注入点）。 */
    static class FailingLoadPolicyStore extends DelegatingStore {
        private final AtomicBoolean boom;

        FailingLoadPolicyStore(IRetryRecordStore delegate, AtomicBoolean boom) {
            super(delegate);
            this.boom = boom;
        }

        @Override
        public NopRetryPolicy loadPolicy(String policyId) {
            if (boom.get())
                throw new NopException(ERR_RETRY_EXECUTION_FAILED)
                    .param(ARG_RECORD_ID, "injected-db-failure-on-loadPolicy");
            return delegate.loadPolicy(policyId);
        }
    }

    // ==================== Helpers ====================

    /**
     * 反射读取引擎簿记失败计数（红态基线引擎无该计数器方法时返回 -1，使断言红且红态可编译）。
     */
    private long engineCounter(String getter) {
        try {
            return (Long) RetryEngineImpl.class.getMethod(getter).invoke(retryEngine);
        } catch (NoSuchMethodException e) {
            return -1L;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("failed to read engine counter: " + getter, e);
        }
    }

    private void savePolicyIfAbsent(NopRetryPolicy policy) {
        // 重跑安全：同 sid 策略已存在（ORM session 内非 TRANSIENT）时跳过重复保存；
        // 用 getEntityById（缺失返回 null）而非 loadPolicy（requireEntityById 缺失即抛）
        if (daoProvider.daoFor(NopRetryPolicy.class).getEntityById(policy.getSid()) == null) {
            recordStore.savePolicy(policy);
        }
    }

    private NopRetryPolicy createPolicy(String sid, int immediateRetryCount) {
        NopRetryPolicy policy = new NopRetryPolicy();
        policy.setSid(sid);
        policy.setName("Policy " + sid);
        policy.setNamespaceId("default");
        policy.setGroupId("default");
        policy.setMaxRetryCount(5);
        policy.setInitialIntervalMs(DEFAULT_INITIAL_INTERVAL_MS);
        policy.setMaxIntervalMs(DEFAULT_MAX_INTERVAL_MS);
        policy.setJitterRatio(0d);
        policy.setDeadlineTimeoutMs(DEFAULT_DEADLINE_TIMEOUT_MS);
        policy.setBackoffStrategy(BACKOFF_STRATEGY_EXPONENTIAL_BACKOFF);
        policy.setBlockStrategy(BLOCK_STRATEGY_DISCARD);
        policy.setImmediateRetryCount(immediateRetryCount);
        policy.setImmediateRetryIntervalMs(10L);
        return policy;
    }

    private NopRetryRecord createRecord(String sid, String idempotentId, String policyId) {
        NopRetryRecord record = new NopRetryRecord();
        record.setSid(sid);
        record.setNamespaceId("default");
        record.setGroupId("default");
        record.setPolicyId(policyId);
        record.setIdempotentId(idempotentId);
        record.setStatus(RETRY_RECORD_STATUS_PENDING);
        record.setRetryCount(0);
        record.setMaxRetryCount(5);
        record.setServiceName("svc");
        record.setServiceMethod("method");
        record.setRequestPayload("{\"data\":\"test\"}");
        record.setCreateTime(new Timestamp(System.currentTimeMillis()));
        return record;
    }

    private ApiResponse<?> errorResponse(String message) {
        io.nop.api.core.beans.ErrorBean error = new io.nop.api.core.beans.ErrorBean();
        error.setErrorCode("TEST_ERROR");
        error.setDescription(message);
        return ApiResponse.error(error);
    }

    private NopRetryRecord findRecordByIdempotentId(String idempotentId) {
        QueryBean query = new QueryBean();
        query.addFilter(io.nop.api.core.beans.FilterBeans.eq(PROP_NAME_idempotentId, idempotentId));
        query.setLimit(1);
        return daoProvider.daoFor(NopRetryRecord.class).findFirstByQuery(query);
    }

    private List<NopRetryDeadLetter> findDeadLetters(String idempotentId) {
        QueryBean query = new QueryBean();
        query.addFilter(io.nop.api.core.beans.FilterBeans.eq("idempotentId", idempotentId));
        query.addOrderField("createTime", false);
        return daoProvider.daoFor(NopRetryDeadLetter.class).findAllByQuery(query);
    }
}
