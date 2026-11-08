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
import io.nop.dao.api.IDaoProvider;
import io.nop.retry.dao.entity.NopRetryDeadLetter;
import io.nop.retry.dao.entity.NopRetryPolicy;
import io.nop.retry.dao.entity.NopRetryRecord;
import io.nop.retry.engine.NopRetryErrors;
import io.nop.retry.engine.scanner.RetryScannerImpl;
import io.nop.retry.engine.store.IRetryRecordStore;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.retry.api.NopRetryApiConstants.*;
import static io.nop.retry.dao._NopRetryDaoConstants.*;
import static io.nop.retry.dao.entity._gen._NopRetryRecord.PROP_NAME_idempotentId;
import static org.junit.jupiter.api.Assertions.*;

/**
 * plan 2543 R4-10 retry 侧红钉族（红①/红⑨/红⑪/红⑫/红⑬/红⑭）。
 * <p>
 * 红⑨（W-09 回调环）：见 {@link #pin09_callbackChainSelfLoopIsDepthCapped()}。
 * 红⑬（W-13 tcc 清理分页）在 nop-tcc-dao 侧，见 TestCraR410TccPins/TestTccRecordStore 扩展。
 */
@NopTestConfig(
        localDb = true,
        initDatabaseSchema = OptionalBoolean.TRUE
)
public class TestCraR410RetryPins extends JunitAutoTestCase {

    static {
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
        retryEngine.setRecordStore(baseStore);
        retryEngine.setMaxCallbackDepth(null);
    }

    // ==================== 红①（W-01）：OVERWRITE 输家分流 ====================

    /**
     * 红①(a)：并发 OVERWRITE 输家——键已被对端合法替换（重读 sid 不同）。
     * 缺陷态（2515 基线）：无条件对 stale 行造死信 + 裸约束错误暴露。
     * 修复后：不造死信、按幂等重放返回成功。
     */
    @Test
    void pin01a_overwriteLoserConcedesWithoutDeadLetter() throws Exception {
        NopRetryPolicy policy = createPolicy("policy-r410-ow-loser", 0);
        policy.setBlockStrategy(BLOCK_STRATEGY_OVERWRITE);
        savePolicyIfAbsent(policy);

        NopRetryRecord existing = createRecord("rec-r410-ow-loser", "idem-r410-ow-loser", "policy-r410-ow-loser");
        recordStore.saveRecord(existing);

        // 模拟并发赢家：键位被替换为另一条记录（不同 sid，同 idempotentId）
        recordStore.deleteRecord(recordStore.loadRecord(existing.getSid()));
        NopRetryRecord winner = createRecord("rec-r410-ow-winner", "idem-r410-ow-loser", "policy-r410-ow-loser");
        recordStore.saveRecord(winner);

        rpcInvoker.setResponse(ApiResponse.success("ok"));

        // 输家持有已被删除的 existing 实体，重提交 → replaceRecord UK 冲突 → 应让步
        ApiResponse<?> response = retryEngine.executeTask(
                retryEngine.newRetryTask("svc", "method")
                        .withPolicyId("policy-r410-ow-loser")
                        .withIdempotentId("idem-r410-ow-loser"),
                new ApiRequest<>(), null).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals(0, findDeadLetters("idem-r410-ow-loser").size(),
                "并发 OVERWRITE 输家不得对已被合法替换的 stale 行造死信");
        assertNotNull(response);
        assertTrue(response.isOk(), "输家应按幂等重放语义让步（成功），不以裸约束冲突暴露");
    }

    /**
     * 红①(c)：非 UK 异常（瞬时 DB 故障）→ 不造死信（禁止以猜测驱动破坏性删除）。
     */
    @Test
    void pin01c_nonUkReplaceFailureDoesNotDeadLetter() throws Exception {
        NopRetryPolicy policy = createPolicy("policy-r410-ow-nonuk", 0);
        policy.setBlockStrategy(BLOCK_STRATEGY_OVERWRITE);
        savePolicyIfAbsent(policy);

        NopRetryRecord existing = createRecord("rec-r410-ow-nonuk", "idem-r410-ow-nonuk", "policy-r410-ow-nonuk");
        recordStore.saveRecord(existing);

        IRetryRecordStore failing = new DelegatingStore(recordStore) {
            @Override
            public void replaceRecord(NopRetryRecord existingRecord, NopRetryRecord newRecord) {
                throw new IllegalStateException("transient db failure");
            }
        };
        retryEngine.setRecordStore(failing);

        ApiRequest<Object> request = new ApiRequest<>();
        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                retryEngine.executeTask(retryEngine.newRetryTask("svc", "method")
                                .withPolicyId("policy-r410-ow-nonuk")
                                .withIdempotentId("idem-r410-ow-nonuk"),
                        request, null).toCompletableFuture().get(10, TimeUnit.SECONDS));

        assertTrue(ex.getCause() instanceof NopException, "failure must be typed: " + ex.getCause());
        assertEquals(0, findDeadLetters("idem-r410-ow-nonuk").size(),
                "非 UK 异常不得造死信（并发赢家不可判定）");
        assertNotNull(recordStore.loadRecord(existing.getSid()), "旧记录必须存活");
    }

    // ==================== 红⑨（W-09）：回调环深度护栏 ====================

    /**
     * 红⑨：回调链自环（policy 的 callbackPolicyId 指向自身）。缺陷态：每环孵化新记录，
     * idempotentId 逐环追加 "_callback"，记录无界增长、副作用永续。
     * 修复后：深度达 max-depth(3) 拒孵 + 计数可见，无新增回调记录。
     */
    @Test
    void pin09_callbackChainSelfLoopIsDepthCapped() throws Exception {
        NopRetryPolicy loop = createPolicy("policy-r410-loop", 0);
        loop.setMaxRetryCount(1);
        loop.setCallbackEnabled(BOOL_YES);
        loop.setCallbackPolicyId("policy-r410-loop");   // 自环
        loop.setCallbackTriggerType(CALLBACK_TRIGGER_TYPE_ALWAYS);
        savePolicyIfAbsent(loop);

        retryEngine.setMaxCallbackDepth(2);

        // 一条深度已达上限的回调记录：终态时不得再孵化下一环
        NopRetryRecord deep = createRecord("rec-r410-loop", "idem-r410-loop_callback_callback",
                "policy-r410-loop");
        deep.setMaxRetryCount(1);
        deep.setRetryCount(0);
        recordStore.saveRecord(deep);

        rpcInvoker.setResponse(ApiResponse.success("ok"));

        long before = retryEngine.getCallbackDepthExceededCount();
        retryEngine.executeRetryFromScanner(recordStore.loadRecord(deep.getSid()), null)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertTrue(retryEngine.getCallbackDepthExceededCount() > before,
                "深度超限必须计数可见（拒孵不是静默）");
        assertNull(findRecordByIdempotentId("idem-r410-loop_callback_callback_callback"),
                "深度达上限后不得孵化下一环（无界自增殖）");
    }

    /** 红⑨b：`callbackDepth` 只统计紧贴末尾的重复后缀（业务键含 "_callback" 不误判）。 */
    @Test
    void pin09b_callbackDepthCountsOnlyTrailingSuffix() {
        assertEquals(0, RetryEngineImpl.callbackDepth(null));
        assertEquals(0, RetryEngineImpl.callbackDepth("order_callback_2026"));
        assertEquals(0, RetryEngineImpl.callbackDepth("cb_callback_x"));
        assertEquals(1, RetryEngineImpl.callbackDepth("k_callback"));
        assertEquals(3, RetryEngineImpl.callbackDepth("k_callback_callback_callback"));
    }

    // ==================== 红⑪（W-11）：扫描批内 per-record 隔离 ====================

    /**
     * 红⑪：processor 批内某条记录同步抛。缺陷态（2515 基线）：惰性流 map 在 toArray 时中断
     * → 后续记录零派发（确定性饿死）。修复后：后续记录必须仍被派发、失败计数可见。
     */
    @Test
    void pin11_dispatchFailureDoesNotStarveLaterRecords() {
        List<NopRetryRecord> records = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            records.add(createRecord("rec-r410-disp-" + i, "idem-r410-disp-" + i, "policy-x"));
        }

        AtomicInteger failing = new AtomicInteger();
        AtomicInteger dispatched = new AtomicInteger();

        RetryEngineImpl harness = new RetryEngineImpl() {
            @Override
            CompletionStage<ApiResponse<?>> executeRetryFromScanner(NopRetryRecord record,
                                                                    io.nop.api.core.util.ICancelToken cancelToken) {
                if (record.getSid().equals(records.get(0).getSid())) {
                    failing.incrementAndGet();
                    throw new IllegalStateException("synchronous dispatch failure");
                }
                dispatched.incrementAndGet();
                return CompletableFuture.completedFuture(ApiResponse.success(null));
            }
        };

        // 直驱生产派发助手（doStart 的真实接线体）
        harness.dispatchScannerBatch(records);

        assertEquals(1, failing.get());
        assertEquals(2, dispatched.get(),
                "批内单条同步抛不得中断本批其余记录（确定性饿死缺陷）");
        assertEquals(1, harness.getScannerDispatchFailureCount(),
                "派发失败必须计数可见（非静默）");
    }

    // ==================== 红⑫（W-12）：单轮批数/时长上界 ====================

    /**
     * 红⑫a：单轮非空批数达上限即结束本轮（余量交下一轮）。
     * 缺陷态：do-while 排水整个积压，单轮时长线性于积压深度。
     */
    @Test
    void pin12a_maxBatchesPerRoundTruncatesRound() {
        AtomicInteger processed = new AtomicInteger();
        // totalRecords=500 / perPage=100 → 5 个非空批，批数上界 2 成为唯一截断因素
        RoundScanner scanner = new RoundScanner(500, 100);
        scanner.setMaxBatchesPerRound(2);

        scanner.scan(records -> processed.incrementAndGet());

        assertEquals(2, processed.get(), "单轮最多处理 max-batches-per-round 个非空批");
        assertEquals(RetryScannerImpl.RoundEndReason.MAX_BATCHES, scanner.getLastRoundEndReason());
    }

    /** 红⑫b：锁竞争的空批不消耗批数预算（否则静默烧完预算而不处理任何记录）。 */
    @Test
    void pin12b_emptyLockedBatchDoesNotConsumeBudget() {
        AtomicInteger processed = new AtomicInteger();
        RoundScanner scanner = new RoundScanner(500, 100);
        scanner.setMaxBatchesPerRound(1);
        scanner.lockNothing = true;   // 始终锁不到（并发竞争），fetch 仍非空

        scanner.scan(records -> processed.incrementAndGet());

        assertEquals(0, processed.get());
        // fetch 永不为空 → 由批数/时长之外的条件终止；此处断言「空锁定批未计入批数」
        assertNotEquals(RetryScannerImpl.RoundEndReason.MAX_BATCHES, scanner.getLastRoundEndReason(),
                "空锁定批不得消耗批数预算");
    }

    /** 红⑫c：单轮时长上界（可覆写时钟 seam 确定性驱动）。 */
    @Test
    void pin12c_maxRoundDurationTruncatesRound() {
        AtomicInteger processed = new AtomicInteger();
        // totalRecords=1000 / perPage=100 → 10 批；时钟每步 +600ms 使时长上界在早期即触发
        RoundScanner scanner = new RoundScanner(1000, 100);
        scanner.setMaxBatchesPerRound(100);
        scanner.setMaxRoundDurationMs(1000);
        scanner.clockStepMs = 600;   // 每次取样推进 600ms → 第 2 批前即超 1000ms

        scanner.scan(records -> processed.incrementAndGet());

        assertTrue(processed.get() < 100, "时长上界必须截断本轮（实际 " + processed.get() + " 批）");
        assertEquals(RetryScannerImpl.RoundEndReason.MAX_DURATION, scanner.getLastRoundEndReason());
    }

    /**
     * 单轮直驱扫描器测试子类：走**生产 doScan**（不复制其逻辑），以 stub store 供数、
     * 覆写 `currentTimeMillis()` 确定性驱动时长上界。
     */
    static class RoundScanner extends RetryScannerImpl {
        private final int totalRecords;
        private final int perPage;
        private final List<NopRetryRecord> records = new ArrayList<>();
        private int cursor;
        boolean lockNothing;
        long clockStepMs;
        long clockBase = 1_000_000L;

        RoundScanner(int totalRecords, int perPage) {
            this.totalRecords = totalRecords;
            this.perPage = perPage;
            for (int i = 0; i < totalRecords; i++) {
                NopRetryRecord record = new NopRetryRecord();
                record.setSid("scan-rec-" + i);
                record.setIdempotentId("scan-idem-" + i);
                records.add(record);
            }
            setRecordStore(new ScanStore());
            setBatchSize(perPage);
        }

        void scan(java.util.function.Consumer<List<NopRetryRecord>> processor) {
            startScanning(processor);   // 置 running=true
            super.doScan(processor);
            stopScanning();
        }

        /** 不产生真实调度（否则 startScanning 会立刻跑一轮，重复计数）。 */
        @Override
        protected io.nop.commons.concurrent.executor.IScheduledExecutor getExecutor() {
            return (io.nop.commons.concurrent.executor.IScheduledExecutor) java.lang.reflect.Proxy.newProxyInstance(
                    io.nop.commons.concurrent.executor.IScheduledExecutor.class.getClassLoader(),
                    new Class[]{io.nop.commons.concurrent.executor.IScheduledExecutor.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("scheduleWithFixedDelay") || name.equals("scheduleAtFixedRate")
                                || name.equals("schedule") || name.equals("submit")) {
                            return new FutureTaskNoop();
                        }
                        if (name.equals("executeOn"))
                            return proxy;
                        return null;
                    });
        }

        @Override
        protected long currentTimeMillis() {
            clockBase += clockStepMs;
            return clockBase;
        }

        class ScanStore implements IRetryRecordStore {
            @Override
            public List<NopRetryRecord> fetchPendingRecords(int limit, io.nop.api.core.beans.IntRangeSet partitions) {
                if (cursor >= totalRecords)
                    return List.of();
                int end = Math.min(cursor + perPage, totalRecords);
                List<NopRetryRecord> page = new ArrayList<>(records.subList(cursor, end));
                cursor = end;
                return page;
            }

            @Override
            public List<NopRetryRecord> tryLockRecordsForProcess(List<NopRetryRecord> records,
                                                                 long retryingTimeoutMs) {
                return lockNothing ? List.of() : records;
            }

            @Override
            public long getCurrentTime() {
                return clockBase;
            }

            @Override
            public NopRetryRecord newRecord(io.nop.retry.api.IRetryTask task, ApiRequest<?> request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public io.nop.retry.dao.entity.NopRetryAttempt newAttempt(NopRetryRecord record) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void saveAttempt(io.nop.retry.dao.entity.NopRetryAttempt attempt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void reconcileStaleRunningAttempts(String recordId) {
            }

            @Override
            public NopRetryRecord loadRecord(String recordId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public NopRetryRecord findPendingRecordByIdempotentId(String namespaceId, String groupId,
                                                                  String idempotentId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public NopRetryRecord findRecordByIdempotentId(String namespaceId, String groupId,
                                                           String idempotentId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void deleteRecord(NopRetryRecord record) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void replaceRecord(NopRetryRecord existingRecord, NopRetryRecord newRecord) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean renewLease(NopRetryRecord record, long leaseTimeoutMs) {
                return true;
            }

            @Override
            public NopRetryDeadLetter loadDeadLetter(String deadLetterId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void saveRecord(NopRetryRecord record) {
            }

            @Override
            public void updateRecord(NopRetryRecord record) {
            }

            @Override
            public void moveToDeadLetter(NopRetryRecord record, String errorCode, String errorMessage,
                                         String errorStack) {
            }

            @Override
            public void saveDeadLetter(NopRetryDeadLetter deadLetter) {
            }

            @Override
            public NopRetryPolicy loadPolicy(String policyId) {
                return null;
            }

            @Override
            public void savePolicy(NopRetryPolicy policy) {
            }

            @Override
            public long purgeExpiredLedgerRows(long retentionTimeMillis, int batchSize) {
                return 0;
            }
        }
    }

    // ==================== 红⑭（W-14）：扫描参数 fail-fast ====================

    /** 红⑭：越界参数在 setter 即抛（装配期 fail-fast），不静默回落缺省。 */
    @Test
    void pin14_scannerParametersFailFast() {
        RetryScannerImpl scanner = new RetryScannerImpl();

        assertConfigInvalid(() -> scanner.setBatchSize(0));
        assertConfigInvalid(() -> scanner.setBatchSize(-1));
        assertConfigInvalid(() -> scanner.setBatchSize(1001));
        assertConfigInvalid(() -> scanner.setScanIntervalMs(0));
        assertConfigInvalid(() -> scanner.setScanIntervalMs(99));
        assertConfigInvalid(() -> scanner.setRetryingTimeoutMs(0));
        assertConfigInvalid(() -> scanner.setMaxBatchesPerRound(0));
        assertConfigInvalid(() -> scanner.setMaxRoundDurationMs(0));

        // 合法值不抛
        scanner.setBatchSize(1);
        scanner.setBatchSize(1000);
        scanner.setScanIntervalMs(100);
        scanner.setRetryingTimeoutMs(1);
        scanner.setMaxBatchesPerRound(1);
        scanner.setMaxRoundDurationMs(1);
    }

    /** 红⑭b：仓储层防御——limit<=0 直接拒绝，不透传给 SQL。 */
    @Test
    void pin14b_storeRejectsNonPositiveLimit() {
        NopException ex = assertThrows(NopException.class,
                () -> recordStore.fetchPendingRecords(0, null));
        assertEquals(NopRetryErrors.ERR_RETRY_SCANNER_CONFIG_INVALID.getErrorCode(), ex.getErrorCode());
    }

    // ==================== 测试辅助 ====================

    private static void assertConfigInvalid(Runnable action) {
        NopException ex = assertThrows(NopException.class, action::run);
        assertEquals(NopRetryErrors.ERR_RETRY_SCANNER_CONFIG_INVALID.getErrorCode(), ex.getErrorCode(),
                "越界参数必须以 ERR_RETRY_SCANNER_CONFIG_INVALID 失败");
    }

    static class FutureTaskNoop implements java.util.concurrent.Future<Object> {
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return true;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }

    private void savePolicyIfAbsent(NopRetryPolicy policy) {
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

    private NopRetryRecord findRecordByIdempotentId(String idempotentId) {
        QueryBean query = new QueryBean();
        query.addFilter(io.nop.api.core.beans.FilterBeans.eq(PROP_NAME_idempotentId, idempotentId));
        query.setLimit(1);
        return daoProvider.daoFor(NopRetryRecord.class).findFirstByQuery(query);
    }

    private List<NopRetryDeadLetter> findDeadLetters(String idempotentId) {
        QueryBean query = new QueryBean();
        query.addFilter(io.nop.api.core.beans.FilterBeans.eq("idempotentId", idempotentId));
        return daoProvider.daoFor(NopRetryDeadLetter.class).findAllByQuery(query);
    }

    /** 委托型 store（只覆写需要注入故障的方法）。 */
    static class DelegatingStore implements IRetryRecordStore {
        private final IRetryRecordStore delegate;

        DelegatingStore(IRetryRecordStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public NopRetryRecord newRecord(io.nop.retry.api.IRetryTask task, ApiRequest<?> request) {
            return delegate.newRecord(task, request);
        }

        @Override
        public io.nop.retry.dao.entity.NopRetryAttempt newAttempt(NopRetryRecord record) {
            return delegate.newAttempt(record);
        }

        @Override
        public void saveAttempt(io.nop.retry.dao.entity.NopRetryAttempt attempt) {
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
        public NopRetryRecord loadRecord(String recordId) {
            return delegate.loadRecord(recordId);
        }

        @Override
        public NopRetryRecord findPendingRecordByIdempotentId(String namespaceId, String groupId,
                                                              String idempotentId) {
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
        public void replaceRecord(NopRetryRecord existingRecord, NopRetryRecord newRecord) {
            delegate.replaceRecord(existingRecord, newRecord);
        }

        @Override
        public boolean renewLease(NopRetryRecord record, long leaseTimeoutMs) {
            return delegate.renewLease(record, leaseTimeoutMs);
        }

        @Override
        public NopRetryDeadLetter loadDeadLetter(String deadLetterId) {
            return delegate.loadDeadLetter(deadLetterId);
        }

        @Override
        public void saveRecord(NopRetryRecord record) {
            delegate.saveRecord(record);
        }

        @Override
        public void updateRecord(NopRetryRecord record) {
            delegate.updateRecord(record);
        }

        @Override
        public void moveToDeadLetter(NopRetryRecord record, String errorCode, String errorMessage,
                                     String errorStack) {
            delegate.moveToDeadLetter(record, errorCode, errorMessage, errorStack);
        }

        @Override
        public void saveDeadLetter(NopRetryDeadLetter deadLetter) {
            delegate.saveDeadLetter(deadLetter);
        }

        @Override
        public NopRetryPolicy loadPolicy(String policyId) {
            return delegate.loadPolicy(policyId);
        }

        @Override
        public void savePolicy(NopRetryPolicy policy) {
            delegate.savePolicy(policy);
        }

        @Override
        public long purgeExpiredLedgerRows(long retentionTimeMillis, int batchSize) {
            return delegate.purgeExpiredLedgerRows(retentionTimeMillis, batchSize);
        }

        @Override
        public long getCurrentTime() {
            return delegate.getCurrentTime();
        }
    }
}
