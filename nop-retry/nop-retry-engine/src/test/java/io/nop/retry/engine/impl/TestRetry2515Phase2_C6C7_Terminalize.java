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
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.retry.dao.entity.NopRetryAttempt;
import io.nop.retry.dao.entity.NopRetryDeadLetter;
import io.nop.retry.dao.entity.NopRetryPolicy;
import io.nop.retry.dao.entity.NopRetryRecord;
import io.nop.retry.engine.store.IRetryRecordStore;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static io.nop.retry.api.NopRetryApiConstants.*;
import static io.nop.retry.dao._NopRetryDaoConstants.*;
import static io.nop.retry.dao.entity._gen._NopRetryAttempt.*;
import static io.nop.retry.dao.entity._gen._NopRetryRecord.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * plan 2515 Phase 2 裁定⑦ focused 回归（先红后绿，C10-005 / C10-007 retry 半随本体同 diff）：
 * <ul>
 * <li>C6-002（红⑨ / C10-005）：崩溃遗留 RUNNING attempt#1 在场时 scanner 重拾——对账置 STOPPED
 *     + attemptNo 取 max+1，UK_RETRY_ATTEMPT_RECORD_NO 碰撞砖化链消除</li>
 * <li>C6-002 防御兜底：saveAttempt 首插 duplicate-key → 对账关闭遗留 RUNNING 行 + max+1 重插
 *     （resolveInsertRaceWinner 对称形态，WARN 显式留痕）</li>
 * <li>C7-003（红⑩ / C10-007）：损坏 payload——解析/策略装载异常首败即死信化，僵尸记录
 *     （每租约周期 144 条 ERROR/天无限重拾）不可再现</li>
 * <li>C7-003（C10-007）：invokeAsync 同步抛——attempt 终态化落账（FAILED + endTime），
 *     业务链 failedStage（whenComplete 未挂接面闭合）</li>
 * </ul>
 * 红绿证据快照：_tmp/red-green-2515-retry-c6-002-c7-003-{red,green}.log
 */
@NopTestConfig(
        localDb = true,
        initDatabaseSchema = OptionalBoolean.TRUE
)
public class TestRetry2515Phase2_C6C7_Terminalize extends JunitAutoTestCase {

    static {
        // 隔离后台扫描器对断言的干扰（与 TestRetry2515Phase2_RetryEngine 同理）
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

    @BeforeEach
    void setUp() {
        rpcInvoker.reset();
    }

    // ==================== C6-002 红⑨（C10-005）：RUNNING 遗留 × scanner 重拾 ====================

    /**
     * 红⑨：崩溃/挂死后遗留 RUNNING attempt#1（retryCount 未递增）。缺陷态：scanner 重拾产出
     * 同号 attempt → saveAttempt 同步抛 UK → 记录永久砖化（不可执行不可死信化，每租约周期循环）。
     * 修复后：执行入口对账置 STOPPED + endTime，attemptNo 取 max+1=2，重拾正常推进。
     */
    @Test
    void testC6_002_staleRunningAttempt_scannerResume_recoversNotBricked() throws Exception {
        NopRetryPolicy policy = createPolicy("policy-2515-c6002a");
        savePolicyIfAbsent(policy);

        NopRetryRecord record = createRecord("rec-2515-c6002a", "idem-2515-c6002a", "policy-2515-c6002a");
        recordStore.saveRecord(record);

        // 崩溃残留注入：attempt#1 已落 RUNNING（doExecute 前置落账后进程崩溃的遗痕）
        NopRetryAttempt stale = recordStore.newAttempt(record);
        stale.setStatus(RETRY_ATTEMPT_STATUS_RUNNING);
        recordStore.saveAttempt(stale);
        assertEquals(1, stale.getAttemptNo(), "precondition: seeded stale attempt is attemptNo=1");

        rpcInvoker.setResponse(ApiResponse.success("ok"));

        // scanner 租约到期重拾：必须推进（不砖化），且对账遗留 RUNNING 行
        CompletableFuture<ApiResponse<?>> future =
                retryEngine.executeRetryFromScanner(record, null).toCompletableFuture();
        ApiResponse<?> response = future.get(10, TimeUnit.SECONDS);
        assertTrue(response.isOk(), "scanner resume must progress past a stale RUNNING attempt");

        // 对账：遗留 RUNNING attempt#1 终态化为 STOPPED + endTime 落账（僵尸 RUNNING 不可残留）
        NopRetryAttempt reloaded = daoProvider.daoFor(NopRetryAttempt.class).getEntityById(stale.getSid());
        assertNotNull(reloaded);
        assertEquals(RETRY_ATTEMPT_STATUS_STOPPED, reloaded.getStatus(),
                "stale RUNNING attempt must be reconciled to STOPPED (C6-002)");
        assertNotNull(reloaded.getEndTime(), "reconciled attempt must have endTime recorded");

        // attemptNo 取 max+1：新 attempt 行 attemptNo=2 落盘（UK 碰撞从根消除）
        List<NopRetryAttempt> attempts = findAttempts(record.getSid());
        assertEquals(2, attempts.size(), "resume must persist a new attempt row");
        NopRetryAttempt fresh = attempts.stream()
                .filter(a -> a.getAttemptNo() != null && a.getAttemptNo() == 2)
                .findFirst().orElse(null);
        assertNotNull(fresh, "new attempt must take attemptNo=max+1=2 (not retryCount+1=1)");
        assertEquals(RETRY_ATTEMPT_STATUS_SUCCESS, fresh.getStatus(),
                "new attempt must be terminalized after business success");
        assertEquals(1, rpcInvoker.getInvocationCount());
    }

    /**
     * C6-002 防御兜底：saveAttempt 首插撞 UK（与遗留 RUNNING attempt#1 同号）——对账关闭
     * 遗留行 + max+1 重插一次，WARN 显式留痕（不静默）。缺陷态：UK 同步抛直接穿出。
     */
    @Test
    void testC6_002_saveAttemptDuplicateKey_defensiveFallbackReconcilesAndReinserts() throws Exception {
        NopRetryPolicy policy = createPolicy("policy-2515-c6002b");
        savePolicyIfAbsent(policy);

        NopRetryRecord record = createRecord("rec-2515-c6002b", "idem-2515-c6002b", "policy-2515-c6002b");
        recordStore.saveRecord(record);

        NopRetryAttempt stale = recordStore.newAttempt(record);
        stale.setStatus(RETRY_ATTEMPT_STATUS_RUNNING);
        recordStore.saveAttempt(stale);

        // 构造同号冲突的 TRANSIENT attempt（模拟对账窗口外的竞争残留）
        NopRetryAttempt conflicting = recordStore.newAttempt(record);
        conflicting.setAttemptNo(1);
        conflicting.setStatus(RETRY_ATTEMPT_STATUS_RUNNING);

        // 不得抛 UK：兜底对账 + max+1 重插
        assertDoesNotThrow(() -> recordStore.saveAttempt(conflicting),
                "duplicate-key fallback must reconcile and reinsert, not surface the UK error");

        NopRetryAttempt reloadedStale = daoProvider.daoFor(NopRetryAttempt.class)
                .getEntityById(stale.getSid());
        assertEquals(RETRY_ATTEMPT_STATUS_STOPPED, reloadedStale.getStatus(),
                "fallback must reconcile the stale RUNNING row to STOPPED");
        assertNotNull(reloadedStale.getEndTime());

        NopRetryAttempt reloadedConflict = daoProvider.daoFor(NopRetryAttempt.class)
                .getEntityById(conflicting.getSid());
        assertNotNull(reloadedConflict, "conflicting attempt must be reinserted");
        assertEquals(2, reloadedConflict.getAttemptNo(),
                "reinserted attempt must take attemptNo=max+1 after reconciliation");
    }

    // ==================== C7-003 红⑩（C10-007）：损坏 payload 首败即死信化 ====================

    /**
     * 红⑩：损坏 JSON payload。缺陷态：裸 parseMap 同步抛止于 scanner catch——记录状态/
     * retryCount 零推进，每租约周期（144 条全栈 ERROR/天）无限循环重拾（僵尸记录）。
     * 修复后：首败即 moveToDeadLetter（全量快照）+ failedStage，record 行删除。
     */
    @Test
    void testC7_003_corruptPayload_firstFailureDeadLetters_noZombie() throws Exception {
        NopRetryPolicy policy = createPolicy("policy-2515-c7003a");
        savePolicyIfAbsent(policy);

        NopRetryRecord record = createRecord("rec-2515-c7003a", "idem-2515-c7003a", "policy-2515-c7003a");
        record.setRequestPayload("{corrupt-json");
        recordStore.saveRecord(record);

        rpcInvoker.setResponse(ApiResponse.success("ok"));

        CompletableFuture<ApiResponse<?>> future = invokeFromScanner(record);
        assertThrows(ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS),
                "parse failure must complete the future exceptionally");

        // 首败即死信化：全量快照入死信，原 record 行删除——僵尸循环不可再现
        List<NopRetryDeadLetter> deadLetters = findDeadLetters(record.getSid());
        assertEquals(1, deadLetters.size(),
                "corrupt-payload record must be dead-lettered on first failure (C7-003)");
        assertEquals("rec-2515-c7003a", deadLetters.get(0).getRecordId());
        assertEquals("{corrupt-json", deadLetters.get(0).getRequestPayload(),
                "dead letter must keep the full payload snapshot");
        assertNull(daoProvider.daoFor(NopRetryRecord.class).getEntityById(record.getSid()),
                "record row must be removed after dead-lettering (no periodic re-pickup)");
        assertEquals(0, rpcInvoker.getInvocationCount(),
                "no business side-effect may run for an unparseable payload");
    }

    // ==================== C7-003（C10-007）：invokeAsync 同步抛 attempt 终态化 ====================

    /**
     * invokeAsync 同步抛：whenComplete 未挂接面。缺陷态：attempt 永久残留 RUNNING（僵尸），
     * 异常同步穿出或 future 悬置。修复后：attempt 终态化落账（FAILED + endTime + 错误三元组）
     * + 业务链 failedStage。
     */
    @Test
    void testC7_003_invokeAsyncSyncThrow_attemptFinalizedAndFutureFailed() throws Exception {
        NopRetryPolicy policy = createPolicy("policy-2515-c7003b");
        savePolicyIfAbsent(policy);

        NopRetryRecord record = createRecord("rec-2515-c7003b", "idem-2515-c7003b", "policy-2515-c7003b");
        recordStore.saveRecord(record);

        IRpcThrowingInvoker throwingInvoker = new IRpcThrowingInvoker();
        retryEngine.setRpcServiceInvoker(throwingInvoker);
        try {
            CompletableFuture<ApiResponse<?>> future = invokeFromScanner(record);
            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> future.get(10, TimeUnit.SECONDS),
                    "sync throw must be delivered as future failure (never suspended)");
            assertTrue(ex.getCause() instanceof IllegalStateException,
                    "cause should be the injected sync throw but was: " + ex.getCause());

            // attempt 终态化：FAILED + endTime（RUNNING 僵尸不可残留）
            List<NopRetryAttempt> attempts = findAttempts(record.getSid());
            assertEquals(1, attempts.size());
            assertEquals(RETRY_ATTEMPT_STATUS_FAILED, attempts.get(0).getStatus(),
                    "attempt must be terminalized to FAILED on invokeAsync sync throw (C7-003)");
            assertNotNull(attempts.get(0).getEndTime());
            assertNotNull(attempts.get(0).getErrorCode());
        } finally {
            retryEngine.setRpcServiceInvoker(rpcInvoker);
        }
    }

    // ==================== Helpers ====================

    /**
     * scanner 重拾入口。缺陷态解析异常会同步穿出 executeRetryFromScanner——统一包装为
     * failed future，使红态判据落在状态断言（非调用形态）上。
     */
    private CompletableFuture<ApiResponse<?>> invokeFromScanner(NopRetryRecord record) {
        try {
            CompletionStage<ApiResponse<?>> stage = retryEngine.executeRetryFromScanner(record, null);
            return stage.toCompletableFuture();
        } catch (Throwable direct) {
            CompletableFuture<ApiResponse<?>> failed = new CompletableFuture<>();
            failed.completeExceptionally(direct);
            return failed;
        }
    }

    private List<NopRetryAttempt> findAttempts(String recordId) {
        QueryBean query = new QueryBean();
        query.addFilter(io.nop.api.core.beans.FilterBeans.eq(PROP_NAME_recordId, recordId));
        query.addOrderField(PROP_NAME_attemptNo, false);
        return daoProvider.daoFor(NopRetryAttempt.class).findAllByQuery(query);
    }

    private List<NopRetryDeadLetter> findDeadLetters(String recordId) {
        QueryBean query = new QueryBean();
        query.addFilter(io.nop.api.core.beans.FilterBeans.eq("recordId", recordId));
        return daoProvider.daoFor(NopRetryDeadLetter.class).findAllByQuery(query);
    }

    private void savePolicyIfAbsent(NopRetryPolicy policy) {
        if (daoProvider.daoFor(NopRetryPolicy.class).getEntityById(policy.getSid()) == null) {
            recordStore.savePolicy(policy);
        }
    }

    private NopRetryPolicy createPolicy(String sid) {
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
        policy.setImmediateRetryCount(0);
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

    /** invokeAsync 同步抛注入载体（C7-003 whenComplete 未挂接面）。 */
    static class IRpcThrowingInvoker implements io.nop.api.core.rpc.IRpcServiceInvoker {
        @Override
        public CompletionStage<ApiResponse<?>> invokeAsync(String serviceName, String serviceMethod,
                io.nop.api.core.beans.ApiRequest<?> request, io.nop.api.core.util.ICancelToken cancelToken) {
            throw new IllegalStateException("injected sync throw from invoker");
        }
    }
}
