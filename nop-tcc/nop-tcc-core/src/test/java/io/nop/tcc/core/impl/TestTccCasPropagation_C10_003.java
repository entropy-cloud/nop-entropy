package io.nop.tcc.core.impl;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.tcc.api.ITccBranchRecord;
import io.nop.tcc.api.ITccRecord;
import io.nop.tcc.api.TccStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C10-003 收口门（plan 2515 红①②③）：
 * 红① C1-001（←C3-001）：恢复链认领 × 调用方 endAsync 双链对撞（真并发注入）——CAS 失败必须
 *     显式失败传播（裁定①），败方不得折叠终态（裁定③终态直写前置校验），补偿 RPC 恒单发；
 * 红② C3-002（←C3-016）：分支 CAS 失败（0 行被吞、内存不回写）后聚合必须读 DB 权威分支状态
 *     （裁定②），不得按陈旧内存副本折叠误报 CANCEL_SUCCESS；
 * 红③ C3-012：全局首跳 CAS 异常注入——挂点 err!=null 时不得执行终态折叠（裁定③挂点门控），
 *     全局保持中间态交恢复扫描续跑，异常向上传播。
 */
@Timeout(30)
public class TestTccCasPropagation_C10_003 {

    /**
     * 独立内存快照 record（恢复链从 DB 加载自己的副本，from 状态初值为快照时刻的 TRYING，
     * 后续随本驱动的 CAS 成功推进——与真实 entity 内存副本同形）。
     */
    static class SnapshotRecord implements ITccRecord {
        final String txnGroup;
        final String txnId;
        volatile TccStatus status;

        SnapshotRecord(String txnGroup, String txnId, TccStatus status) {
            this.txnGroup = txnGroup;
            this.txnId = txnId;
            this.status = status;
        }

        @Override
        public String getTxnGroup() {
            return txnGroup;
        }

        @Override
        public String getTxnId() {
            return txnId;
        }

        @Override
        public TccStatus getTccStatus() {
            return status;
        }

        @Override
        public Timestamp getExpireTime() {
            return new Timestamp(System.currentTimeMillis() - 1);
        }
    }

    static ITccRecord snapshotRecord(String txnGroup, String txnId, TccStatus status) {
        return new SnapshotRecord(txnGroup, txnId, status);
    }

    /**
     * 真 CAS 语义 fake：全局状态 CAS 以独立 DB 状态为权威（from 状态取自传入 record 内存副本，
     * 镜像 store updateByQuery 的 [txnId+fromStatus] 单语句 CAS），冲突时异常完成——
     * 与裁定①修复后的 TccRecordStore 传播形态一致。
     */
    static class TrueCasStore extends FakeTccRecordStore {
        final Map<String, TccStatus> dbGlobalStatus = new ConcurrentHashMap<>();
        final AtomicInteger globalCasCalls = new AtomicInteger();
        final AtomicInteger terminalWriteAttempts = new AtomicInteger();
        // 红②注入面：这些分支的 finish* CAS 以 0 行失败被吞（不回写内存、正常完成——缺陷态 store 形态）
        final Set<String> swallowBranchCasIds = new CopyOnWriteArraySet<>();
        // 红②注入面：DB 权威分支状态（getBranchRecordsAsync 重载返回的权威行）
        final Map<String, TccStatus> authoritativeBranchStatus = new ConcurrentHashMap<>();
        // 红③注入面：全局 CAS 一律异常完成（首跳异常注入）
        volatile boolean failGlobalCas = false;

        @Override
        public CompletionStage<Void> updateTccStatusAsync(ITccRecord record, TccStatus status, Throwable error) {
            globalCasCalls.incrementAndGet();
            if (status.isFinished())
                terminalWriteAttempts.incrementAndGet();
            if (failGlobalCas)
                return CompletableFuture.failedFuture(new IllegalStateException("global cas conflict"));
            synchronized (this) {
                TccStatus cur = dbGlobalStatus.get(record.getTxnId());
                if (cur == null)
                    cur = record.getTccStatus();
                TccStatus from = record.getTccStatus();
                if (!cur.equals(from))
                    return CompletableFuture.failedFuture(new IllegalStateException(
                            "global cas conflict: db=" + cur + ", from=" + from));
                dbGlobalStatus.put(record.getTxnId(), status);
                if (record instanceof FakeTccRecord)
                    ((FakeTccRecord) record).status = status;
                else if (record instanceof SnapshotRecord)
                    ((SnapshotRecord) record).status = status;
                return CompletableFuture.completedFuture(null);
            }
        }

        @Override
        public CompletionStage<Void> updateTccBranchStatusAsync(ITccBranchRecord record, TccStatus status, Throwable error) {
            if (swallowBranchCasIds.contains(record.getBranchId())) {
                return CompletableFuture.completedFuture(null);
            }
            ((FakeBranchRecord) record).status = status;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<List<ITccBranchRecord>> getBranchRecordsAsync(ITccRecord record) {
            List<ITccBranchRecord> rows = new ArrayList<>();
            for (FakeBranchRecord b : branches.getOrDefault(record.getTxnId(), List.of())) {
                if (authoritativeBranchStatus.containsKey(b.branchId)) {
                    // 返回重载副本：DB 权威状态可能与分支事务内存副本分歧
                    FakeBranchRecord copy = new FakeBranchRecord(b.txnGroup, b.txnId, b.branchId, b.serviceName,
                            b.serviceMethod, b.confirmMethod, b.cancelMethod, b.request);
                    copy.status = authoritativeBranchStatus.get(b.branchId);
                    rows.add(copy);
                } else {
                    rows.add(b);
                }
            }
            return CompletableFuture.completedFuture(rows);
        }
    }

    static class RecordingInvoker implements io.nop.api.core.rpc.IRpcServiceInvoker {
        final List<String> methods = new CopyOnWriteArrayList<>();

        @Override
        public CompletionStage<ApiResponse<?>> invokeAsync(String serviceName, String serviceMethod,
                                                           ApiRequest<?> request, io.nop.api.core.util.ICancelToken cancelToken) {
            methods.add(serviceMethod);
            return CompletableFuture.completedFuture(ApiResponse.success("ok"));
        }
    }

    static class Fixture {
        final TrueCasStore store = new TrueCasStore();
        final RecordingInvoker invoker = new RecordingInvoker();
        final TccEngine engine = new TccEngine();

        Fixture() {
            engine.setTccRecordStore(store);
            engine.setServiceInvoker(invoker);
        }

        FakeTccRecordStore.FakeTccRecord newRecord(TccStatus status) {
            FakeTccRecordStore.FakeTccRecord record =
                    (FakeTccRecordStore.FakeTccRecord) store.newTccRecord("default");
            record.status = status;
            return record;
        }

        FakeTccRecordStore.FakeBranchRecord addBranch(ITccRecord record, TccStatus status) {
            FakeTccRecordStore.FakeBranchRecord branch = new FakeTccRecordStore.FakeBranchRecord(
                    record.getTxnGroup(), record.getTxnId(), "b1", "svc",
                    "tryMethod", "svc__confirmMethod", "svc__cancelMethod", new ApiRequest<>());
            branch.status = status;
            store.addBranch(branch);
            return branch;
        }
    }

    /**
     * 红①（C1-001←C3-001）：恢复链认领 × 调用方 endAsync 双链对撞（真并发注入）。
     * 两个驱动各持独立 record 内存快照（恢复链从 DB 加载自己的副本），共享同一 DB 行。
     * 裁定①③绿判据：恰好一个赢方正常完成；败方 CAS 冲突显式失败传播（异常完成，非 warn 后吞）；
     * 败方不得执行终态折叠（终态直写前置校验）；confirm RPC 恒单发（参与方互斥）。
     */
    @Test
    public void C1_001_doubleDriveCollision_casLoserMustFailFast_notFoldFalseTerminal_singleRpc() throws Exception {
        Fixture f = new Fixture();
        FakeTccRecordStore.FakeTccRecord dbRecord = f.newRecord(TccStatus.TRYING);
        f.addBranch(dbRecord, TccStatus.TRY_SUCCESS);

        String txnId = dbRecord.getTxnId();
        String txnGroup = dbRecord.getTxnGroup();
        // 两个驱动各持独立内存快照（from 状态均 TRYING，与双驱动源的陈旧副本形态一致）
        ITccRecord snapshot1 = snapshotRecord(txnGroup, txnId, TccStatus.TRYING);
        ITccRecord snapshot2 = snapshotRecord(txnGroup, txnId, TccStatus.TRYING);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Throwable> driver1 = () -> {
                start.await();
                try {
                    new TccTransaction(false, snapshot1, f.engine)
                            .endAsync(false, ApiResponse.success("ok"), null)
                            .toCompletableFuture().get(20, TimeUnit.SECONDS);
                    return null;
                } catch (Throwable e) {
                    return e;
                }
            };
            Callable<Throwable> driver2 = () -> {
                start.await();
                try {
                    new TccTransaction(false, snapshot2, f.engine)
                            .endAsync(false, ApiResponse.success("ok"), null)
                            .toCompletableFuture().get(20, TimeUnit.SECONDS);
                    return null;
                } catch (Throwable e) {
                    return e;
                }
            };
            Future<Throwable> fut1 = pool.submit(driver1);
            Future<Throwable> fut2 = pool.submit(driver2);
            start.countDown();

            Throwable err1 = fut1.get(25, TimeUnit.SECONDS);
            Throwable err2 = fut2.get(25, TimeUnit.SECONDS);

            // 恰好一个赢方正常完成，败方显式失败传播（裁定①：异常完成，非 warn 后吞）
            int failures = (err1 == null ? 0 : 1) + (err2 == null ? 0 : 1);
            assertEquals(1, failures, "exactly one driver must win; loser must fail explicitly"
                    + ", err1=" + err1 + ", err2=" + err2);

            // 败方 CAS 失败后不得执行终态折叠（裁定③终态直写前置校验）：
            // 全局终态 CAS 只允许赢方一次（首跳 CAS 非终态不计入）
            assertEquals(1, f.store.terminalWriteAttempts.get(),
                    "terminal status CAS must be attempted exactly once (winner only)");

            // 补偿 RPC 恒单发（参与方互斥）：confirm 方法只允许被调用一次
            assertEquals(1, f.invoker.methods.size(),
                    "confirm rpc must be issued exactly once, got=" + f.invoker.methods);

            // 全局终态与分支权威状态一致（裁定②终裁）
            assertEquals(TccStatus.CONFIRM_SUCCESS, f.store.dbGlobalStatus.get(txnId),
                    "winner must fold global terminal from confirmed branch");
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 红②（C3-002←C3-016）：分支 B 的补偿阶段 CAS 以 0 行失败被吞（内存不回写），
     * DB 权威状态为 CANCEL_FAILED。聚合必须读 DB 权威状态（裁定②）——
     * 缺陷态按陈旧内存折叠误报 CANCEL_SUCCESS（全局≥11 被恢复扫描永久排除，预留资源悬挂）。
     */
    @Test
    public void C3_002_branchCasLost_aggregateMustReadDbAuthority_notFoldFalseCancelSuccess() throws Exception {
        Fixture f = new Fixture();
        ITccRecord record = f.newRecord(TccStatus.TRYING);
        FakeTccRecordStore.FakeBranchRecord branchA = f.addBranch(record, TccStatus.TRY_SUCCESS);
        FakeTccRecordStore.FakeBranchRecord branchB = f.addBranch(record, TccStatus.TRY_SUCCESS);

        // 分支 B 的补偿阶段 CAS 全部 0 行失败被吞（内存保持旧值，正常完成——缺陷态 store 形态）
        f.store.swallowBranchCasIds.add(branchB.getBranchId());
        // DB 权威状态：A 已成功取消，B 取消失败（可重试）
        f.store.authoritativeBranchStatus.put(branchA.getBranchId(), TccStatus.CANCEL_SUCCESS);
        f.store.authoritativeBranchStatus.put(branchB.getBranchId(), TccStatus.CANCEL_FAILED);

        // endAsync 契约：业务异常原样重抛给调用方（err 传播断言针对补偿失败面）
        try {
            new TccTransaction(false, record, f.engine)
                    .endAsync(false, null, new IllegalStateException("biz failure"))
                    .toCompletableFuture().get(20, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            // 预期路径：业务异常被重抛
        }

        assertEquals(TccStatus.CANCEL_FAILED, f.store.dbGlobalStatus.get(record.getTxnId()),
                "global final status must be folded from DB authoritative branch states"
                        + " (branch B is CANCEL_FAILED), false CANCEL_SUCCESS from stale memory is unreachable");
    }

    /**
     * 红③（C3-012）：全局首跳 CAS 异常注入。挂点 err!=null 时不得执行终态折叠
     * （裁定③：零驱动不可直写终态），全局保持中间态交恢复扫描续跑，异常向上传播。
     */
    @Test
    public void C3_012_firstHopCasException_mustNotWriteFalseTerminal_mustPropagate() throws Exception {
        Fixture f = new Fixture();
        ITccRecord record = f.newRecord(TccStatus.TRYING);
        f.addBranch(record, TccStatus.TRY_SUCCESS);
        f.store.failGlobalCas = true;

        Throwable err = null;
        try {
            new TccTransaction(false, record, f.engine)
                    .endAsync(false, null, new IllegalStateException("biz failure"))
                    .toCompletableFuture().get(20, TimeUnit.SECONDS);
        } catch (Throwable e) {
            err = e;
        }

        assertTrue(err != null, "endAsync must propagate the compensation failure exceptionally");
        // 终态直写前置校验：首跳 CAS 失败后终态 CAS 一次都不得尝试（含聚合后的终态写入）
        assertEquals(0, f.store.terminalWriteAttempts.get(),
                "no terminal status CAS may be attempted when the first-hop CAS failed");
        // 结构性短路：首跳 CAS 失败即分支零驱动即零补偿 RPC
        assertTrue(f.invoker.methods.isEmpty(),
                "no compensation rpc may be issued when the first-hop CAS failed, got=" + f.invoker.methods);
        // 全局 DB 状态未被误写终态（保持 TRYING 交恢复扫描续跑）
        assertNull(f.store.dbGlobalStatus.get(record.getTxnId()),
                "global db status must stay non-terminal for recovery resumption");
    }
}
