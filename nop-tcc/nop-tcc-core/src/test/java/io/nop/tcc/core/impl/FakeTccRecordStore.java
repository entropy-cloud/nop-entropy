package io.nop.tcc.core.impl;

import io.nop.api.core.beans.ApiRequest;
import io.nop.tcc.api.ITccBranchRecord;
import io.nop.tcc.api.ITccRecord;
import io.nop.tcc.api.ITccRecordStore;
import io.nop.tcc.api.TccBranchRequest;
import io.nop.tcc.api.TccStatus;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TCC 测试共用 fake：内存版 ITccRecordStore + 可变状态的 record/branchRecord。
 * 状态更新直接写回对象，并记录调用轨迹供测试断言。
 */
public class FakeTccRecordStore implements ITccRecordStore {

    public static class FakeTccRecord implements ITccRecord {
        final String txnGroup;
        final String txnId;
        volatile TccStatus status = TccStatus.CREATED;
        final Timestamp expireAt = new Timestamp(System.currentTimeMillis() + 60_000);

        FakeTccRecord(String txnGroup, String txnId) {
            this.txnGroup = txnGroup;
            this.txnId = txnId;
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
            return expireAt;
        }
    }

    public static class FakeBranchRecord implements ITccBranchRecord {
        final String txnGroup;
        final String txnId;
        final String branchId;
        final String serviceName;
        final String serviceMethod;
        final String confirmMethod;
        final String cancelMethod;
        final ApiRequest<?> request;
        volatile TccStatus status = TccStatus.CREATED;

        FakeBranchRecord(String txnGroup, String txnId, String branchId, String serviceName,
                         String serviceMethod, String confirmMethod, String cancelMethod, ApiRequest<?> request) {
            this.txnGroup = txnGroup;
            this.txnId = txnId;
            this.branchId = branchId;
            this.serviceName = serviceName;
            this.serviceMethod = serviceMethod;
            this.confirmMethod = confirmMethod;
            this.cancelMethod = cancelMethod;
            this.request = request;
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
        public String getBranchId() {
            return branchId;
        }

        @Override
        public Integer getBranchNo() {
            return 1;
        }

        @Override
        public String getParentBranchId() {
            return null;
        }

        @Override
        public String getServiceName() {
            return serviceName;
        }

        @Override
        public String getServiceMethod() {
            return serviceMethod;
        }

        @Override
        public ApiRequest<?> getRequest() {
            return request;
        }

        @Override
        public String getConfirmMethod() {
            return confirmMethod;
        }

        @Override
        public String getCancelMethod() {
            return cancelMethod;
        }

        @Override
        public TccStatus getBranchStatus() {
            return status;
        }

        @Override
        public Integer getMaxRetryTimes() {
            return 3;
        }

        @Override
        public Integer getRetryTimes() {
            return 0;
        }

        @Override
        public Timestamp getCreateTime() {
            return new Timestamp(0);
        }

        @Override
        public Timestamp getUpdateTime() {
            return new Timestamp(0);
        }
    }

    final Map<String, FakeTccRecord> records = new HashMap<>();
    final Map<String, List<FakeBranchRecord>> branches = new HashMap<>();
    final AtomicInteger txnSeq = new AtomicInteger();
    final AtomicInteger branchSeq = new AtomicInteger();

    final AtomicInteger saveTccCount = new AtomicInteger();
    final AtomicInteger updateTccCount = new AtomicInteger();
    final AtomicInteger saveBranchCount = new AtomicInteger();
    final AtomicInteger updateBranchCount = new AtomicInteger();
    volatile TccStatus lastGlobalStatus;
    volatile Boolean lastRemoveCompletedOnlyCompleted;
    List<? extends ITccRecord> expiredRecords = List.of();

    void addBranch(FakeBranchRecord branch) {
        branches.computeIfAbsent(branch.txnId, k -> new ArrayList<>()).add(branch);
    }

    @Override
    public ITccRecord newTccRecord(String txnGroup) {
        FakeTccRecord record = new FakeTccRecord(txnGroup, "txn-" + txnSeq.incrementAndGet());
        records.put(record.txnId, record);
        return record;
    }

    @Override
    public ITccBranchRecord newBranchRecord(ITccRecord record, TccBranchRequest request) {
        FakeBranchRecord branch = new FakeBranchRecord(record.getTxnGroup(), record.getTxnId(),
                "branch-" + branchSeq.incrementAndGet(), request.getServiceName(),
                request.getServiceMethod(), request.getConfirmMethod(), request.getCancelMethod(),
                request.getRequest() == null ? new ApiRequest<>() : request.getRequest());
        addBranch(branch);
        return branch;
    }

    @Override
    public CompletionStage<ITccRecord> getTccRecordAsync(String txnGroup, String txnId) {
        return CompletableFuture.completedFuture(records.get(txnId));
    }

    @Override
    public CompletionStage<List<ITccBranchRecord>> getBranchRecordsAsync(ITccRecord record) {
        return CompletableFuture.completedFuture(
                new ArrayList<>(branches.getOrDefault(record.getTxnId(), List.of())));
    }

    @Override
    public CompletionStage<Void> saveTccRecordAsync(ITccRecord record, TccStatus initStatus) {
        saveTccCount.incrementAndGet();
        ((FakeTccRecord) record).status = initStatus;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> updateTccStatusAsync(ITccRecord record, TccStatus status, Throwable error) {
        updateTccCount.incrementAndGet();
        lastGlobalStatus = status;
        ((FakeTccRecord) record).status = status;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> saveBranchRecordAsync(ITccBranchRecord branchRecord, TccStatus initStatus) {
        saveBranchCount.incrementAndGet();
        ((FakeBranchRecord) branchRecord).status = initStatus;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> updateTccBranchStatusAsync(ITccBranchRecord record, TccStatus status, Throwable error) {
        updateBranchCount.incrementAndGet();
        ((FakeBranchRecord) record).status = status;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public List<? extends ITccRecord> fetchExpiredRecords(int pageSize, long expireGap, long checkInterval, int maxRetryCount) {
        return expiredRecords;
    }

    @Override
    public void removeCompletedRecords(long retentionTime, boolean onlyCompleted) {
        lastRemoveCompletedOnlyCompleted = onlyCompleted;
    }

    public static void assertStatus(ITccBranchRecord branch, TccStatus expected) {
        assertEquals(expected, branch.getBranchStatus(), "branch status=" + branch.getBranchStatus());
    }
}
