package io.nop.tcc.core.impl;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.BaseContext;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.tcc.api.ITccBranchRecord;
import io.nop.tcc.api.ITccBranchTransaction;
import io.nop.tcc.api.ITccRecord;
import io.nop.tcc.api.ITccRecordStore;
import io.nop.tcc.api.ITccTransaction;
import io.nop.tcc.api.TccBranchRequest;
import io.nop.tcc.api.TccStatus;
import io.nop.tcc.core.TccCoreErrors;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2543 R4-10 红钉族（红②/红③/红④/红⑤/红⑥/红⑦/红⑩ 的 tcc 侧）。
 * <p>
 * 每条钉对应设计文档 `retry-tcc-compensation-ring-guard-and-scan-bound-concurrency-contract.md`
 * §4.1 的一个缺陷面；缺陷态红判据与修复后绿判据在设计文档表内列明。
 */
public class TestCraR410TccPins {

    // ==================== 红②（W-02）：注册表原子身份 + 条件恢复 ====================

    /**
     * 红②a：同一 IContext 被 N 线程并发首次进入时，instance(ctx) 必须返回同一实例。
     * 缺陷态：get→null→new→setAttribute 无同步 → 多线程各 new 一个，后 set 者赢。
     */
    @Test
    public void pin02a_concurrentInstanceReturnsSameRegistry() throws Exception {
        IContext context = new BaseContext();
        int threads = 16;
        // 确定性交错：前 N 个线程都必须先读到 null（= 都过了快路径）再继续创建，
        // 使「非原子 get→new→set」必现身份分裂（否则纯竞速腿概率性未命中）
        int rendezvous = threads;
        CountDownLatch bothSawNull = new CountDownLatch(rendezvous);
        AtomicInteger hookCalls = new AtomicInteger();
        TccTransactionRegistry.raceProbeHook = () -> {
            if (hookCalls.incrementAndGet() <= rendezvous) {
                bothSawNull.countDown();
                try {
                    bothSawNull.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<TccTransactionRegistry> results = new ArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                CompletableFuture<Void> f = new CompletableFuture<>();
                futures.add(f);
                pool.submit(() -> {
                    try {
                        TccTransactionRegistry registry = TccTransactionRegistry.instance(context);
                        synchronized (results) {
                            results.add(registry);
                        }
                        f.complete(null);
                    } catch (Throwable t) {
                        f.completeExceptionally(t);
                    }
                });
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
        } finally {
            TccTransactionRegistry.raceProbeHook = null;
            pool.shutdownNow();
        }

        assertEquals(threads, results.size());
        TccTransactionRegistry first = results.get(0);
        for (TccTransactionRegistry registry : results) {
            assertSame(first, registry, "并发首次进入必须得到同一注册表实例（身份分裂缺陷）");
        }
    }

    /**
     * 红②b：条件恢复不误清对端注册；同时嵌套（外 A / 内 B）严格后进先出。
     */
    @Test
    public void pin02b_conditionalRestoreKeepsPeerAndNestsCorrectly() {
        TccTransactionRegistry registry = new TccTransactionRegistry();
        ITccTransaction outer = txn("outer");
        ITccTransaction inner = txn("inner");
        ITccTransaction peer = txn("peer");

        // 嵌套形态：外层 put(outer) → 内层 put(inner) → 内层完成恢复 outer
        registry.put("g", outer);
        registry.put("g", inner);
        registry.restore("g", inner, outer);
        assertSame(outer, registry.get("g"), "内层完成必须恢复外层事务");

        // 并发交错：内层仍在途时对端 put(peer)；内层完成时当前值已不是 inner → 不得回退对端
        registry.put("g", peer);
        registry.restore("g", inner, outer);
        assertSame(peer, registry.get("g"), "被对端覆盖后不得用陈旧值回退（叠放互踩）");

        // 外层完成：当前值仍为 outer 时才恢复 old(null)
        registry.put("g", outer);
        registry.restore("g", outer, null);
        assertEquals(null, registry.get("g"));
    }

    // ==================== 红③（W-03）：创建守卫 DB 复核 + 孤儿收敛 ====================

    /**
     * 红③a-1：父事务 DB 状态已非 TRYING（内存副本仍 TRYING）→ 必须拒绝创建分支。
     * 缺陷态：只读内存副本 → 放行（向已终态事务写入预留资源）。
     */
    @Test
    public void pin03a_guardRejectsWhenDbStatusNotTrying() {
        // DB 侧已回滚终态，调用方链持有的内存副本仍 TRYING（陈旧放行面）——
        // 必须是两个不同对象，否则内存副本与 DB 行同体，测不到「只读内存副本」的缺陷。
        FakeTccRecordStore.FakeTccRecord dbRow = new FakeTccRecordStore.FakeTccRecord("g", "txn-db-divergent");
        dbRow.status = TccStatus.CANCEL_SUCCESS;
        FakeTccRecordStore store = new FakeTccRecordStore() {
            @Override
            public CompletionStage<ITccRecord> getTccRecordAsync(String txnGroup, String txnId) {
                return CompletableFuture.completedFuture(dbRow);
            }
        };
        TccEngine engine = newEngine(store);
        FakeTccRecordStore.FakeTccRecord record = new FakeTccRecordStore.FakeTccRecord("g", "txn-db-divergent");
        record.status = TccStatus.TRYING;

        TccTransaction txn = new TccTransaction(true, record, engine, 0L);
        Throwable err = assertThrows(Throwable.class, () -> engine.runBranchTransactionAsync(txn,
                branchRequest("svc"), b -> CompletableFuture.completedFuture(null))
                .toCompletableFuture().join());
        assertTrue(errText(err).contains("not-allow-start-branch"), errText(err));
    }

    /** 红③a-2：父记录读失败（DB 故障）→ fail-close 拒绝（不是放行）。 */
    @Test
    public void pin03a_guardFailsClosedOnReadError() {
        FakeTccRecordStore store = new FakeTccRecordStore() {
            @Override
            public CompletionStage<ITccRecord> getTccRecordAsync(String txnGroup, String txnId) {
                return CompletableFuture.failedFuture(new IllegalStateException("db down"));
            }
        };
        TccEngine engine = newEngine(store);
        FakeTccRecordStore.FakeTccRecord record = (FakeTccRecordStore.FakeTccRecord) store.newTccRecord("g");
        record.status = TccStatus.TRYING;

        TccTransaction txn = new TccTransaction(true, record, engine, 0L);
        Throwable err = assertThrows(Throwable.class, () -> engine.runBranchTransactionAsync(txn,
                branchRequest("svc"), b -> CompletableFuture.completedFuture(null))
                .toCompletableFuture().join());
        assertTrue(errText(err).contains("branch-guard-check-failed"), errText(err));
    }

    /** 红③a-3：父记录不存在 → 明确拒绝。 */
    @Test
    public void pin03a_guardRejectsWhenRecordMissing() {
        FakeTccRecordStore store = new FakeTccRecordStore();
        TccEngine engine = newEngine(store);
        FakeTccRecordStore.FakeTccRecord record = (FakeTccRecordStore.FakeTccRecord) store.newTccRecord("g");
        record.status = TccStatus.TRYING;
        store.records.remove(record.getTxnId());

        TccTransaction txn = new TccTransaction(true, record, engine, 0L);
        Throwable err = assertThrows(Throwable.class, () -> engine.runBranchTransactionAsync(txn,
                branchRequest("svc"), b -> CompletableFuture.completedFuture(null))
                .toCompletableFuture().join());
        assertTrue(errText(err).contains("missing-transaction-record"), errText(err));
    }

    /** 红③a-4：DB 状态仍 TRYING 时正常放行（守卫不误杀）。 */
    @Test
    public void pin03a_guardAllowsWhenDbStatusTrying() {
        FakeTccRecordStore store = new FakeTccRecordStore();
        TccEngine engine = newEngine(store);
        FakeTccRecordStore.FakeTccRecord record = (FakeTccRecordStore.FakeTccRecord) store.newTccRecord("g");
        record.status = TccStatus.TRYING;

        TccTransaction txn = new TccTransaction(true, record, engine, 0L);
        AtomicReference<String> branchId = new AtomicReference<>();
        engine.runBranchTransactionAsync(txn, branchRequest("svc"), b -> {
            branchId.set(b.getBranchId());
            return CompletableFuture.completedFuture(null);
        }).toCompletableFuture().join();
        assertNotNull(branchId.get(), "DB 复核通过后分支应真实创建并驱动");
    }

    /**
     * 红③b：补偿期间新插入的孤儿分支必须被收敛驱动、且收敛后终态能正常折叠。
     * <p>
     * 构造要点（子 agent closure audit B-2 勘误）：孤儿必须在**入口快照装载之后**（第 2 次及以后的
     * `getBranchRecordsAsync`）才出现——若在第 1 次装载时注入，它就在入口快照里，收敛代码路径不被
     * 触发（旧钉即因此为伪绿）。同时断言终态为 CANCEL_SUCCESS（而非 CONVERGENCE_EXHAUSTED），
     * 以证明「收敛后能折叠」而非「永不收敛」。
     */
    @Test
    public void pin03b_orphanBranchIsConvergedThenTerminalFolded() {
        FakeTccRecordStore store = new FakeTccRecordStore() {
            private int loadCount;

            @Override
            public CompletionStage<List<ITccBranchRecord>> getBranchRecordsAsync(ITccRecord record) {
                // 第 1 次调用 = endAsync 入口快照（此时还没有孤儿）
                loadCount++;
                if (loadCount >= 2) {
                    boolean present = branches.getOrDefault(record.getTxnId(), List.of()).stream()
                            .anyMatch(b -> "orphan-branch".equals(b.getBranchId()));
                    if (!present) {
                        FakeBranchRecord orphan = new FakeBranchRecord(record.getTxnGroup(), record.getTxnId(),
                                "orphan-branch", "svc", "try", "confirm", "cancel", new ApiRequest<>());
                        orphan.status = TccStatus.TRY_SUCCESS;
                        addBranch(orphan);
                    }
                }
                return CompletableFuture.completedFuture(
                        new ArrayList<>(branches.getOrDefault(record.getTxnId(), List.of())));
            }
        };
        TccEngine engine = newEngine(store);
        FakeTccRecordStore.FakeTccRecord record = (FakeTccRecordStore.FakeTccRecord) store.newTccRecord("g");
        record.status = TccStatus.TRYING;

        TccTransaction txn = new TccTransaction(true, record, engine, 0L);
        // 业务失败 → cancel；孤儿分支（TRY_SUCCESS）必须被驱动到 CANCEL_SUCCESS 且终态折叠成功
        txn.endAsync(false, ApiResponse.error(new io.nop.api.core.beans.ErrorBean("E")), null)
                .toCompletableFuture().join();

        ITccBranchRecord orphan = store.branches.get(record.getTxnId()).stream()
                .filter(b -> "orphan-branch".equals(b.getBranchId())).findFirst().orElseThrow();
        assertEquals(TccStatus.CANCEL_SUCCESS, orphan.getBranchStatus(),
                "收敛轮必须驱动入口快照之外的孤儿分支");
        assertEquals(TccStatus.CANCEL_SUCCESS, record.status,
                "孤儿收敛后终态必须能折叠（不得以 CONVERGENCE_EXHAUSTED 拒绝）");
    }

    // ==================== 红④（W-04）：弃链写点闸 ====================

    /**
     * 红④：恢复链超时被放弃（纪元推进）后，旧链苏醒不得写终态。
     * 缺陷态：旧链照常折叠终态，与新链争写。
     */
    @Test
    public void pin04_abandonedChainCannotWriteTerminal() {
        FakeTccRecordStore store = new FakeTccRecordStore();
        TccEngine engine = newEngine(store);
        FakeTccRecordStore.FakeTccRecord record = (FakeTccRecordStore.FakeTccRecord) store.newTccRecord("g");
        record.status = TccStatus.TRYING;

        long abandonedEpoch = engine.beginDriving(record.getTxnId());
        // 超时：推进纪元使旧链令牌失效
        engine.beginDriving(record.getTxnId());

        TccTransaction abandoned = new TccTransaction(false, record, engine, abandonedEpoch);
        Throwable err = assertThrows(Throwable.class,
                () -> abandoned.endAsync(true, null, null).toCompletableFuture().join());
        assertTrue(errText(err).contains("driving-abandoned"), errText(err));

        // 新链（当前纪元）照常可写
        long activeEpoch = engine.beginDriving(record.getTxnId());
        TccTransaction active = new TccTransaction(false, record, engine, activeEpoch);
        active.endAsync(true, null, null).toCompletableFuture().join();
        assertEquals(TccStatus.CANCEL_SUCCESS, record.status);
    }

    /** 红④b：纪元条目缺失 = inactive（不误截合法链的显式语义）。 */
    @Test
    public void pin04_isActiveDrivingAbsentMeansInactive() {
        TccEngine engine = newEngine(new FakeTccRecordStore());
        assertFalse(engine.isActiveDriving("never-seen", 1L));
    }

    // ==================== 红⑤（W-05）：null response + 三通道兜底 ====================

    /** 红⑤a：finishCancelAsync 在 response==null && ex==null 时不 NPE，落可重试失败态。 */
    @Test
    public void pin05a_finishCancelNullResponseDoesNotThrow() {
        FakeTccRecordStore store = new FakeTccRecordStore();
        TccEngine engine = newEngine(store);
        FakeTccRecordStore.FakeTccRecord record = (FakeTccRecordStore.FakeTccRecord) store.newTccRecord("g");
        FakeTccRecordStore.FakeBranchRecord branch = newBranch(store, record);
        branch.status = TccStatus.CANCELLING;

        TccBranchTransaction txn = new TccBranchTransaction(branch, engine);
        txn.finishCancelAsync(false, null, null).toCompletableFuture().join();
        assertEquals(TccStatus.CANCEL_FAILED, branch.getBranchStatus());
    }

    /** 红⑤b：begin*Async() 同步抛（在 thenCompose lambda 之外）必须使返回 future 异常完成。 */
    @Test
    public void pin05b_beginSyncThrowCompletesFutureExceptionally() {
        FakeTccRecordStore store = new FakeTccRecordStore();
        TccEngine engine = newEngine(store);
        FakeTccRecordStore.FakeTccRecord record = (FakeTccRecordStore.FakeTccRecord) store.newTccRecord("g");
        FakeTccRecordStore.FakeBranchRecord branch = newBranch(store, record);
        branch.status = TccStatus.TRY_SUCCESS;

        ITccBranchTransaction throwing = new DelegatingBranchTxn(branch) {
            @Override
            public CompletionStage<Void> beginCancelAsync(boolean timeout) {
                throw new IllegalStateException("begin sync throw");
            }
        };

        CompletionStage<ApiResponse<?>> stage = TccRunner.runBranchCancelAsync(throwing, false, noopInvoker());
        Throwable err = assertThrows(Throwable.class, () -> stage.toCompletableFuture().join());
        assertTrue(errText(err).contains("begin sync throw"), errText(err));
    }

    /** 红⑤c：finish 同步抛（回调体内）必须使返回 future 异常完成，而非永不完成。 */
    @Test
    public void pin05c_finishSyncThrowCompletesFutureExceptionally() {
        FakeTccRecordStore store = new FakeTccRecordStore();
        TccEngine engine = newEngine(store);
        FakeTccRecordStore.FakeTccRecord record = (FakeTccRecordStore.FakeTccRecord) store.newTccRecord("g");
        FakeTccRecordStore.FakeBranchRecord branch = newBranch(store, record);
        branch.status = TccStatus.TRY_SUCCESS;

        ITccBranchTransaction throwing = new DelegatingBranchTxn(branch) {
            @Override
            public CompletionStage<Void> beginConfirmAsync() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> finishConfirmAsync(ApiResponse<?> response, Throwable ex) {
                throw new IllegalStateException("finish sync throw");
            }
        };

        CompletionStage<ApiResponse<?>> stage = TccRunner.runBranchConfirmAsync(throwing, OK_INVOKER);
        Throwable err = assertThrows(Throwable.class, () -> stage.toCompletableFuture().join());
        assertTrue(errText(err).contains("finish sync throw"), errText(err));
    }

    // ==================== 红⑥（W-06）：逐分支隔离 + 聚合结算表 ====================

    /**
     * 红⑥a：confirm 批次中某分支 begin 同步抛，后续分支必须仍被驱动；失败分支进 waitAll。
     * 缺陷态（2515 基线）：惰性/整批级中断 → 后续分支零派发。
     */
    @Test
    public void pin06a_oneBranchThrowDoesNotStarveLaterBranches() {
        AtomicInteger driven = new AtomicInteger();
        ITccBranchTransaction bad = new SimpleBranchTxn("bad", TccStatus.TRY_SUCCESS) {
            @Override
            public CompletionStage<Void> beginConfirmAsync() {
                throw new IllegalStateException("bad branch");
            }
        };
        ITccBranchTransaction good = new SimpleBranchTxn("good", TccStatus.TRY_SUCCESS) {
            @Override
            public CompletionStage<Void> beginConfirmAsync() {
                driven.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            }
        };

        CompletionStage<Void> stage = TccRunner.confirmAllAsync(List.of(bad, good), OK_INVOKER);
        assertThrows(Throwable.class, () -> stage.toCompletableFuture().join());
        assertEquals(1, driven.get(), "排在失败分支之后的同批分支必须仍被驱动（批内传染缺陷）");
    }

    /**
     * 红⑥b：聚合结算表——cancel 方向未驱动的 TRY_SUCCESS 分支不得计为成功。
     * 缺陷态：按「非失败即成功」折叠 CANCEL_SUCCESS（半个成功隐蔽变体）。
     */
    @Test
    public void pin06b_cancelAggregateTreatsUndrivenTrySuccessAsUnsettled() {
        assertEquals(TccStatus.CANCEL_FAILED,
                TccRunner.aggregateCancelBranchStatus(List.of(
                        new SimpleBranchTxn("b1", TccStatus.CANCEL_SUCCESS),
                        new SimpleBranchTxn("b2", TccStatus.TRY_SUCCESS))));
    }

    /**
     * 红⑥c：confirm 方向未驱动的 TRY_SUCCESS 分支不得计为成功。
     */
    @Test
    public void pin06c_confirmAggregateTreatsUndrivenBranchAsUnsettled() {
        assertEquals(TccStatus.CONFIRM_FAILED,
                TccRunner.aggregateConfirmBranchStatus(List.of(
                        new SimpleBranchTxn("b1", TccStatus.CONFIRM_SUCCESS),
                        new SimpleBranchTxn("b2", TccStatus.TRY_SUCCESS))));
    }

    /**
     * 红⑥d（B-1 回归门）：KILLED 与 CONFIRM_SUCCESS 分支必须计为**已结算**——
     * 否则含这两类分支的父事务永不进 getFinishedStatus()，反向摧毁 2515 裁定⑧ 清理链。
     */
    @Test
    public void pin06d_killedAndConfirmSuccessCountAsSettled() {
        assertEquals(TccStatus.CANCEL_SUCCESS,
                TccRunner.aggregateCancelBranchStatus(List.of(
                        new SimpleBranchTxn("b1", TccStatus.CANCEL_SUCCESS),
                        new SimpleBranchTxn("b2", TccStatus.KILLED),
                        new SimpleBranchTxn("b3", TccStatus.CONFIRM_SUCCESS))));
        assertEquals(TccStatus.CONFIRM_SUCCESS,
                TccRunner.aggregateConfirmBranchStatus(List.of(
                        new SimpleBranchTxn("b1", TccStatus.CONFIRM_SUCCESS),
                        new SimpleBranchTxn("b2", TccStatus.KILLED))));
    }

    // ==================== 红⑦（W-07）：证据持久化 + 恢复方向 ====================

    /**
     * 红⑦a：CANCELLING 标记首次落库瞬时失败（非 CAS）→ 有限重试后成功，补偿链继续。
     * 缺陷态：一次失败即短路，记录滞留 TRYING、分支零驱动。
     */
    @Test
    public void pin07a_statusWriteIsRetriedOnTransientFailure() {
        AtomicInteger failures = new AtomicInteger();
        FakeTccRecordStore store = new FakeTccRecordStore() {
            @Override
            public CompletionStage<Void> updateTccStatusAsync(ITccRecord record, TccStatus status, Throwable error) {
                if (status == TccStatus.CANCELLING && failures.getAndIncrement() < 2) {
                    return CompletableFuture.failedFuture(new IllegalStateException("transient db error"));
                }
                return super.updateTccStatusAsync(record, status, error);
            }
        };
        TccEngine engine = newEngine(store);
        FakeTccRecordStore.FakeTccRecord record = (FakeTccRecordStore.FakeTccRecord) store.newTccRecord("g");
        record.status = TccStatus.TRYING;
        FakeTccRecordStore.FakeBranchRecord branch = newBranch(store, record);
        branch.status = TccStatus.TRY_SUCCESS;

        TccTransaction txn = new TccTransaction(true, record, engine, 0L);
        txn.endAsync(false, ApiResponse.error(new io.nop.api.core.beans.ErrorBean("E")), null)
                .toCompletableFuture().join();

        assertEquals(3, failures.get(), "两次瞬时失败后第三次成功（最多 3 次尝试）");
        assertEquals(TccStatus.CANCEL_SUCCESS, branch.getBranchStatus(), "标记落库成功后分支必须被补偿");
    }

    /**
     * 红⑦a-2：CAS 冲突不重试（语义冲突，重试必然再失败）。
     */
    @Test
    public void pin07a_casConflictIsNotRetried() {
        AtomicInteger attempts = new AtomicInteger();
        FakeTccRecordStore store = new FakeTccRecordStore() {
            @Override
            public CompletionStage<Void> updateTccStatusAsync(ITccRecord record, TccStatus status, Throwable error) {
                if (status == TccStatus.CANCELLING) {
                    attempts.incrementAndGet();
                    return CompletableFuture.failedFuture(
                            new NopException(io.nop.tcc.api.TccErrors.ERR_TCC_STATUS_CAS_CONFLICT));
                }
                return super.updateTccStatusAsync(record, status, error);
            }
        };
        TccEngine engine = newEngine(store);
        FakeTccRecordStore.FakeTccRecord record = (FakeTccRecordStore.FakeTccRecord) store.newTccRecord("g");
        record.status = TccStatus.TRYING;

        TccTransaction txn = new TccTransaction(true, record, engine, 0L);
        assertThrows(Throwable.class, () -> txn.endAsync(false,
                ApiResponse.error(new io.nop.api.core.beans.ErrorBean("E")), null).toCompletableFuture().join());
        assertEquals(1, attempts.get(), "CAS 冲突必须一次即止（不重试）");
    }

    /**
     * 红⑦b：超时恢复的未定向态必须走 cancel 方向（不得 confirm 未完成的业务链）。
     */
    @Test
    public void pin07b_timeoutRecoveryDefaultsToCancel() {
        FakeTccRecordStore store = new FakeTccRecordStore();
        TccEngine engine = newEngine(store);
        FakeTccRecordStore.FakeTccRecord record = (FakeTccRecordStore.FakeTccRecord) store.newTccRecord("g");
        record.status = TccStatus.TRYING;
        FakeTccRecordStore.FakeBranchRecord branch = newBranch(store, record);
        branch.status = TccStatus.TRY_SUCCESS;

        TccTransaction txn = new TccTransaction(false, record, engine, 0L);
        txn.endAsync(true, null, null).toCompletableFuture().join();

        assertEquals(TccStatus.TIMEOUT_SUCCESS, branch.getBranchStatus(),
                "超时恢复应取消分支（timeout=true → TIMEOUT_SUCCESS），而非 confirm");
    }

    /**
     * 红⑦c：业务异常必须优先传播，补偿链异常以 suppressed 保留证据。
     */
    @Test
    public void pin07c_businessExceptionWinsWithSuppressedCompensationError() {
        FakeTccRecordStore store = new FakeTccRecordStore() {
            @Override
            public CompletionStage<Void> updateTccStatusAsync(ITccRecord record, TccStatus status, Throwable error) {
                if (status == TccStatus.CANCELLING) {
                    return CompletableFuture.failedFuture(new IllegalStateException("compensation write failed"));
                }
                return super.updateTccStatusAsync(record, status, error);
            }
        };
        TccEngine engine = newEngine(store);
        NopException businessError = new NopException(TccCoreErrors.ERR_TCC_NO_TXN_ID);

        Throwable err = assertThrows(Throwable.class, () -> engine.runInTransactionAsync("g", txn -> {
            throw businessError;
        }).toCompletableFuture().join());
        assertTrue(errText(err).contains("no-txn-id"), "业务异常必须优先传播到调用方: " + errText(err));

        Throwable current = err;
        boolean suppressedFound = false;
        while (current != null) {
            if (current.getSuppressed().length > 0) {
                suppressedFound = true;
                break;
            }
            current = current.getCause();
        }
        assertTrue(suppressedFound, "补偿链异常必须以 suppressed 形式保留证据");
    }

    // ==================== 红⑩（W-10）：恢复轮双上界 ====================

    /**
     * 红⑩a：连续超时达阈值即熔断结束本轮（不再逐条串行等待整个积压）。
     */
    @Test
    public void pin10a_consecutiveTimeoutsTripCircuitBreaker() {
        FakeTccRecordStore store = new FakeTccRecordStore();
        List<FakeTccRecordStore.FakeTccRecord> expired = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            FakeTccRecordStore.FakeTccRecord r = (FakeTccRecordStore.FakeTccRecord) store.newTccRecord("g");
            r.status = TccStatus.TRYING;
            expired.add(r);
        }
        store.expiredRecords = expired;

        TccEngine engine = new TccEngine() {
            @Override
            protected CompletionStage<Void> checkExpiredAsync(ITccRecord record) {
                return new CompletableFuture<>(); // 永不完成 = 挂死
            }
        };
        configureEngine(engine, store);

        long begin = System.currentTimeMillis();
        engine.checkExpiredTransactions(50, 100, null);
        long elapsed = System.currentTimeMillis() - begin;

        assertEquals(3, engine.getRecoveryTimeoutCount(),
                "连续超时 3 次即熔断（缺省 max-consecutive-timeouts=3）");
        assertEquals(1, engine.getRecoveryCircuitBreakCount());
        assertTrue(elapsed < 5000, "熔断后本轮必须快速结束，实际耗时 " + elapsed + "ms");
    }

    /** 红⑩b：fetchExpiredRecords 抛异常时本轮 ERROR 后结束（不静默终止）。 */
    @Test
    public void pin10b_fetchErrorEndsRoundExplicitly() {
        FakeTccRecordStore store = new FakeTccRecordStore() {
            @Override
            public List<? extends ITccRecord> fetchExpiredRecords(int pageSize, long expireGap,
                                                                  long checkInterval, int maxRetryCount) {
                throw new IllegalStateException("db down");
            }
        };
        TccEngine engine = newEngine(store);
        // 不抛即通过（异常被显式捕获并结束本轮）
        engine.checkExpiredTransactions(50, 100, null);
        assertEquals(0, engine.getRecoveryTimeoutCount());
    }

    /** 红⑩c：单轮总时长预算到即结束本轮（慢成功路径也受约束）。 */
    @Test
    public void pin10c_roundDurationBudgetEndsRound() {
        // 每次 fetch 只返回一条 → 处理完全部需 10 页，使时长预算成为唯一截断因素
        final List<FakeTccRecordStore.FakeTccRecord> expired = new ArrayList<>();
        final int[] cursor = {0};
        FakeTccRecordStore store = new FakeTccRecordStore() {
            @Override
            public List<? extends ITccRecord> fetchExpiredRecords(int pageSize, long expireGap,
                                                                  long checkInterval, int maxRetryCount) {
                if (cursor[0] >= expired.size())
                    return List.of();
                return List.of(expired.get(cursor[0]++));
            }
        };
        for (int i = 0; i < 10; i++) {
            FakeTccRecordStore.FakeTccRecord r = (FakeTccRecordStore.FakeTccRecord) store.newTccRecord("g");
            r.status = TccStatus.TRYING;
            expired.add(r);
        }

        AtomicInteger driven = new AtomicInteger();
        AtomicInteger clockTicks = new AtomicInteger();
        TccEngine engine = new TccEngine() {
            @Override
            protected CompletionStage<Void> checkExpiredAsync(ITccRecord record) {
                driven.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            }

            @Override
            protected long currentTimeMillis() {
                // 每次取样推进 40s：预算 60s → 第 2 次检查即超
                return 1_000_000L + clockTicks.incrementAndGet() * 40_000L;
            }
        };
        configureEngine(engine, store);
        engine.checkExpiredTransactions(50, 100, null);

        assertTrue(driven.get() < 10, "时长预算必须截断本轮（实际处理 " + driven.get() + " 条）");
        assertEquals(1, engine.getRecoveryCircuitBreakCount());
    }

    // ==================== 测试辅助 ====================

    static final IRpcServiceInvoker OK_INVOKER = (serviceName, method, request, cancelToken) ->
            CompletableFuture.completedFuture(ApiResponse.success(null));

    private static IRpcServiceInvoker noopInvoker() {
        return OK_INVOKER;
    }

    private static TccEngine newEngine(ITccRecordStore store) {
        TccEngine engine = new TccEngine();
        configureEngine(engine, store);
        return engine;
    }

    private static void configureEngine(TccEngine engine, ITccRecordStore store) {
        engine.setTccRecordStore(store);
        engine.setServiceInvoker(OK_INVOKER);
    }

    private static FakeTccRecordStore.FakeBranchRecord newBranch(FakeTccRecordStore store,
                                                                 FakeTccRecordStore.FakeTccRecord record) {
        FakeTccRecordStore.FakeBranchRecord branch = new FakeTccRecordStore.FakeBranchRecord(
                record.getTxnGroup(), record.getTxnId(), "b-" + record.getTxnId(),
                "svc", "try", "confirm", "cancel", new ApiRequest<>());
        store.addBranch(branch);
        return branch;
    }

    private static TccBranchRequest branchRequest(String serviceName) {
        TccBranchRequest request = new TccBranchRequest();
        request.setServiceName(serviceName);
        request.setServiceMethod("try");
        request.setConfirmMethod("confirm");
        request.setCancelMethod("cancel");
        request.setRequest(new ApiRequest<>());
        return request;
    }

    private static ITccTransaction txn(String id) {
        ITccRecord record = new ITccRecord() {
            @Override
            public String getTxnGroup() {
                return "g";
            }

            @Override
            public String getTxnId() {
                return id;
            }

            @Override
            public TccStatus getTccStatus() {
                return TccStatus.TRYING;
            }

            @Override
            public Timestamp getExpireTime() {
                return new Timestamp(System.currentTimeMillis() + 60_000);
            }
        };
        return new ITccTransaction() {
            @Override
            public ITccRecord getTccRecord() {
                return record;
            }

            @Override
            public boolean isInitiator() {
                return true;
            }

            @Override
            public CompletionStage<Void> beginAsync() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> endAsync(boolean timeout, ApiResponse<?> response, Throwable ex) {
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    private static String errText(Throwable err) {
        StringBuilder sb = new StringBuilder();
        Throwable current = err;
        while (current != null) {
            sb.append(current.getClass().getSimpleName()).append(':').append(current.getMessage()).append('|');
            current = current.getCause();
        }
        return sb.toString();
    }

    /** 可控分支事务基类（只实现被钉住的通道）。 */
    static class SimpleBranchTxn implements ITccBranchTransaction {
        private final String branchId;
        private final TccStatus status;

        SimpleBranchTxn(String branchId, TccStatus status) {
            this.branchId = branchId;
            this.status = status;
        }

        @Override
        public String getTxnGroup() {
            return "g";
        }

        @Override
        public String getTxnId() {
            return "t";
        }

        @Override
        public String getBranchId() {
            return branchId;
        }

        @Override
        public TccStatus getBranchStatus() {
            return status;
        }

        private ITccBranchRecord record;

        @Override
        public ITccBranchRecord getBranchRecord() {
            if (record == null) {
                record = new FakeTccRecordStore.FakeBranchRecord("g", "t", branchId,
                        "svc", "try", "confirm", "cancel", new ApiRequest<>());
                ((FakeTccRecordStore.FakeBranchRecord) record).status = status;
            }
            return record;
        }

        @Override
        public CompletionStage<Void> beginTryAsync() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> finishTryAsync(ApiResponse<?> response, Throwable ex) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> beginConfirmAsync() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> finishConfirmAsync(ApiResponse<?> response, Throwable ex) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> beginCancelAsync(boolean timeout) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> finishCancelAsync(boolean timeout, ApiResponse<?> response, Throwable ex) {
            return CompletableFuture.completedFuture(null);
        }
    }

    /** 委托型分支事务（只覆写需要注入异常的方法）。 */
    static class DelegatingBranchTxn implements ITccBranchTransaction {
        private final ITccBranchRecord record;

        DelegatingBranchTxn(ITccBranchRecord record) {
            this.record = record;
        }

        @Override
        public String getTxnGroup() {
            return record.getTxnGroup();
        }

        @Override
        public String getTxnId() {
            return record.getTxnId();
        }

        @Override
        public String getBranchId() {
            return record.getBranchId();
        }

        @Override
        public TccStatus getBranchStatus() {
            return record.getBranchStatus();
        }

        @Override
        public ITccBranchRecord getBranchRecord() {
            return record;
        }

        @Override
        public CompletionStage<Void> beginTryAsync() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> finishTryAsync(ApiResponse<?> response, Throwable ex) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> beginConfirmAsync() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> finishConfirmAsync(ApiResponse<?> response, Throwable ex) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> beginCancelAsync(boolean timeout) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> finishCancelAsync(boolean timeout, ApiResponse<?> response, Throwable ex) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
