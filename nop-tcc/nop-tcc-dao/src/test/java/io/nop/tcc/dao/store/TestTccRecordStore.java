package io.nop.tcc.dao.store;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.beans.ErrorBean;
import io.nop.dao.api.IEntityDao;
import io.nop.tcc.api.ITccBranchRecord;
import io.nop.tcc.api.ITccRecord;
import io.nop.tcc.api.TccBranchRequest;
import io.nop.tcc.api.TccStatus;
import io.nop.tcc.dao.entity.NopTccBranchRecord;
import io.nop.tcc.dao.entity.NopTccRecord;
import io.nop.tcc.dao.test.AbstractTccTest;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestTccRecordStore extends AbstractTccTest {

    @Test
    public void testNewTccRecord() {
        ITccRecord record = tccRecordStore.newTccRecord("test-group");
        assertNotNull(record.getTxnId());
        assertEquals("test-group", record.getTxnGroup());
        assertEquals(TccStatus.CREATED, record.getTccStatus());
        assertNotNull(record.getExpireTime());
    }

    @Test
    public void testSaveAndGetTccRecord() {
        ITccRecord record = tccRecordStore.newTccRecord("test-group");
        tccRecordStore.saveTccRecordAsync(record, TccStatus.TRYING).toCompletableFuture().join();

        ITccRecord loaded = tccRecordStore.getTccRecordAsync("test-group", record.getTxnId())
                .toCompletableFuture().join();
        assertNotNull(loaded);
        assertEquals(record.getTxnId(), loaded.getTxnId());
        assertEquals(TccStatus.TRYING, loaded.getTccStatus());
    }

    @Test
    public void testUpdateTccStatus() {
        ITccRecord record = tccRecordStore.newTccRecord("test-group");
        tccRecordStore.saveTccRecordAsync(record, TccStatus.TRYING).toCompletableFuture().join();

        RuntimeException error = new RuntimeException("test error");
        tccRecordStore.updateTccStatusAsync(record, TccStatus.CONFIRM_SUCCESS, error)
                .toCompletableFuture().join();

        ITccRecord loaded = tccRecordStore.getTccRecordAsync("test-group", record.getTxnId())
                .toCompletableFuture().join();
        assertEquals(TccStatus.CONFIRM_SUCCESS, loaded.getTccStatus());
    }

    @Test
    public void testSaveAndGetBranchRecord() {
        ITccRecord record = tccRecordStore.newTccRecord("test-group");
        tccRecordStore.saveTccRecordAsync(record, TccStatus.TRYING).toCompletableFuture().join();

        TccBranchRequest request = new TccBranchRequest();
        request.setServiceName("testService");
        request.setServiceMethod("tryMethod");
        request.setConfirmMethod("confirmMethod");
        request.setCancelMethod("cancelMethod");

        ITccBranchRecord branch = tccRecordStore.newBranchRecord(record, request);
        tccRecordStore.saveBranchRecordAsync(branch, TccStatus.TRYING).toCompletableFuture().join();

        List<ITccBranchRecord> branches = tccRecordStore.getBranchRecordsAsync(record)
                .toCompletableFuture().join();
        assertEquals(1, branches.size());
        assertEquals("testService", branches.get(0).getServiceName());
        assertEquals(TccStatus.TRYING, branches.get(0).getBranchStatus());
    }

    @Test
    public void testUpdateTccBranchStatus() {
        ITccRecord record = tccRecordStore.newTccRecord("test-group");
        tccRecordStore.saveTccRecordAsync(record, TccStatus.TRYING).toCompletableFuture().join();

        TccBranchRequest request = new TccBranchRequest();
        request.setServiceName("testService");
        request.setServiceMethod("tryMethod");
        request.setConfirmMethod("confirmMethod");
        request.setCancelMethod("cancelMethod");

        ITccBranchRecord branch = tccRecordStore.newBranchRecord(record, request);
        tccRecordStore.saveBranchRecordAsync(branch, TccStatus.TRYING).toCompletableFuture().join();

        RuntimeException commitError = new RuntimeException("commit failed");
        tccRecordStore.updateTccBranchStatusAsync(branch, TccStatus.CONFIRM_FAILED, commitError)
                .toCompletableFuture().join();

        List<ITccBranchRecord> branches = tccRecordStore.getBranchRecordsAsync(record)
                .toCompletableFuture().join();
        assertEquals(TccStatus.CONFIRM_FAILED, branches.get(0).getBranchStatus());

        NopTccBranchRecord entity = (NopTccBranchRecord) branches.get(0);
        assertNotNull(entity.getCommitErrorCode());
        assertNotNull(entity.getCommitErrorMessage());

        RuntimeException cancelError = new RuntimeException("cancel failed");
        tccRecordStore.updateTccBranchStatusAsync(branch, TccStatus.CANCEL_FAILED, cancelError)
                .toCompletableFuture().join();
        branches = tccRecordStore.getBranchRecordsAsync(record).toCompletableFuture().join();
        NopTccBranchRecord updated = (NopTccBranchRecord) branches.get(0);
        assertNotNull(updated.getCancelErrorCode());
        assertNotNull(updated.getCancelErrorMessage());
    }

    @Test
    public void testFetchExpiredRecords() {
        ITccRecord expired = tccRecordStore.newTccRecord("test-expire");
        NopTccRecord expiredEntity = (NopTccRecord) expired;
        tccRecordStore.saveTccRecordAsync(expired, TccStatus.TRYING).toCompletableFuture().join();
        expiredEntity.setExpireTime(new Timestamp(System.currentTimeMillis() - 60000));
        expiredEntity.setRetryTimes(0);
        tccRecordStore.updateTccStatusAsync(expired, TccStatus.TRYING, null).toCompletableFuture().join();

        List<? extends ITccRecord> expiredList = tccRecordStore.fetchExpiredRecords(100, 30000, 60000, 5);
        assertFalse(expiredList.isEmpty());
        boolean found = expiredList.stream().anyMatch(r -> r.getTxnId().equals(expired.getTxnId()));
        assertTrue(found, "Expired record should be fetched");
    }

    @Test
    public void testRemoveCompletedRecords() {
        ITccRecord completed = tccRecordStore.newTccRecord("test-cleanup");
        NopTccRecord completedEntity = (NopTccRecord) completed;
        tccRecordStore.saveTccRecordAsync(completed, TccStatus.TRYING).toCompletableFuture().join();
        completedEntity.setBeginTime(new Timestamp(System.currentTimeMillis() - 86400000));
        tccRecordStore.updateTccStatusAsync(completed, TccStatus.CONFIRM_SUCCESS, null).toCompletableFuture().join();

        ITccRecord unfinished = tccRecordStore.newTccRecord("test-cleanup");
        tccRecordStore.saveTccRecordAsync(unfinished, TccStatus.TRYING).toCompletableFuture().join();

        tccRecordStore.removeCompletedRecords(3600000, true);

        IEntityDao<NopTccRecord> dao = daoProvider.daoFor(NopTccRecord.class);

        assertNull(dao.getEntityById(completed.getTxnId()));
        assertNotNull(dao.getEntityById(unfinished.getTxnId()));
    }

    /**
     * 按组加载事务记录时txnGroup必须匹配：错误的txnGroup不得静默加载到别组的事务记录
     */
    @Test
    public void testGetTccRecordWithWrongGroupReturnsNull() {
        ITccRecord record = tccRecordStore.newTccRecord("group-a");
        tccRecordStore.saveTccRecordAsync(record, TccStatus.TRYING).toCompletableFuture().join();

        ITccRecord wrongGroup = tccRecordStore.getTccRecordAsync("group-b", record.getTxnId())
                .toCompletableFuture().join();
        assertNull(wrongGroup);

        ITccRecord rightGroup = tccRecordStore.getTccRecordAsync("group-a", record.getTxnId())
                .toCompletableFuture().join();
        assertNotNull(rightGroup);
    }

    /**
     * 分支记录的请求快照必须携带TCC事务上下文头：confirm/cancel重放基于该快照发起调用，
     * 参与者需要通过txnId/branchId头定位预留记录实现幂等控制
     */
    @Test
    public void testNewBranchRecordSnapshotContainsTccHeaders() {
        ITccRecord record = tccRecordStore.newTccRecord("test-header-group");
        tccRecordStore.saveTccRecordAsync(record, TccStatus.TRYING).toCompletableFuture().join();

        TccBranchRequest request = new TccBranchRequest();
        request.setServiceName("testService");
        request.setServiceMethod("tryMethod");
        request.setConfirmMethod("confirmMethod");
        request.setCancelMethod("cancelMethod");
        request.setRequest(ApiRequest.build(java.util.Collections.singletonMap("k", "v")));

        ITccBranchRecord branch = tccRecordStore.newBranchRecord(record, request);

        ApiRequest<?> snapshot = branch.getRequest();
        assertNotNull(snapshot);
        assertEquals(record.getTxnId(), ApiHeaders.getTxnId(snapshot));
        assertEquals(branch.getBranchId(), ApiHeaders.getTxnBranchId(snapshot));
        assertEquals("test-header-group", ApiHeaders.getTxnGroup(snapshot));
        assertEquals(branch.getBranchNo(), ApiHeaders.getTxnBranchNo(snapshot, -1));
    }
}
