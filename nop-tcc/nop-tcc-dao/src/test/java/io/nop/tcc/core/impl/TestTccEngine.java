package io.nop.tcc.core.impl;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.time.CoreMetrics;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import io.nop.tcc.api.ITccTransaction;
import io.nop.tcc.api.TccBranchRequest;
import io.nop.tcc.api.TccStatus;
import io.nop.tcc.dao.entity.NopTccBranchRecord;
import io.nop.tcc.dao.entity.NopTccRecord;
import io.nop.tcc.dao.test.AbstractTccTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestTccEngine extends AbstractTccTest {

    @Inject
    protected IOrmTemplate ormTemplate;

    @Test
    public void testNewTransactionAndBegin() {
        ITccTransaction txn = tccEngine.newTransaction("test-group");
        assertNotNull(txn.getTxnId());
        assertEquals(TccStatus.CREATED, txn.getTccStatus());

        txn.beginAsync().toCompletableFuture().join();

        IEntityDao<NopTccRecord> dao = daoProvider.daoFor(NopTccRecord.class);
        NopTccRecord loaded = dao.getEntityById(txn.getTxnId());
        assertNotNull(loaded);
        assertEquals(TccStatus.TRYING.getCode(), loaded.getStatus());
    }

    @Test
    public void testConfirmPath() {
        String txnId = tccEngine.runInTransaction("test-confirm", txn -> {
            return txn.getTxnId();
        });

        IEntityDao<NopTccRecord> dao = daoProvider.daoFor(NopTccRecord.class);
        NopTccRecord loaded = dao.getEntityById(txnId);
        assertNotNull(loaded);
        assertEquals(TccStatus.CONFIRM_SUCCESS.getCode(), loaded.getStatus());
    }

    @Test
    public void testCancelPathWithException() {
        IEntityDao<NopTccRecord> dao = daoProvider.daoFor(NopTccRecord.class);

        String[] txnIdHolder = new String[1];
        assertThrows(Exception.class, () -> {
            tccEngine.runInTransaction("test-cancel-ex", txn -> {
                txnIdHolder[0] = txn.getTxnId();
                throw new RuntimeException("business error");
            });
        });

        NopTccRecord loaded = dao.getEntityById(txnIdHolder[0]);
        assertNotNull(loaded);
        assertEquals(TccStatus.CANCEL_SUCCESS.getCode(), loaded.getStatus());
    }

    @Test
    public void testCancelPathWithFailedResponse() {
        IEntityDao<NopTccRecord> dao = daoProvider.daoFor(NopTccRecord.class);

        tccEngine.runInTransaction("test-cancel-resp", txn -> {
            return ApiResponse.error(new ErrorBean("ERR").description("business failure"));
        });

        java.util.List<NopTccRecord> all = dao.findAll();
        for (NopTccRecord r : all) {
            if ("test-cancel-resp".equals(r.getTxnGroup())) {
                assertEquals(TccStatus.CANCEL_SUCCESS.getCode(), r.getStatus());
                return;
            }
        }
    }

    @Test
    public void testTimeoutWithoutBranches() {
        IEntityDao<NopTccRecord> dao = daoProvider.daoFor(NopTccRecord.class);
        NopTccRecord record = dao.newEntity();
        record.setTxnGroup("test-timeout-simple");
        record.setAppId("test");
        record.setStatus(TccStatus.TRYING.getCode());
        record.setBeginTime(CoreMetrics.currentTimestamp());
        record.setExpireTime(new Timestamp(System.currentTimeMillis() - 60000));
        record.setRetryTimes(0);
        dao.saveEntity(record);

        tccEngine.checkExpiredTransactions(30000, 5, null);

        NopTccRecord loaded = dao.getEntityById(record.getTxnId());
        assertEquals(TccStatus.CONFIRM_SUCCESS.getCode(), loaded.getStatus());
    }

    @Test
    public void testTimeoutWithRollbackBranch() {
        IEntityDao<NopTccRecord> recordDao = daoProvider.daoFor(NopTccRecord.class);
        NopTccRecord record = recordDao.newEntity();
        record.setTxnGroup("test-timeout-branch");
        record.setAppId("test");
        record.setStatus(TccStatus.TRYING.getCode());
        record.setBeginTime(CoreMetrics.currentTimestamp());
        record.setExpireTime(new Timestamp(System.currentTimeMillis() - 60000));
        record.setRetryTimes(0);
        recordDao.saveEntity(record);

        IEntityDao<NopTccBranchRecord> branchDao = daoProvider.daoFor(NopTccBranchRecord.class);
        NopTccBranchRecord branch = branchDao.newEntity();
        branch.setTxnId(record.getTxnId());
        branch.setBranchNo(1);
        branch.setStatus(TccStatus.TRY_FAILED.getCode());
        branch.setServiceName("testService");
        branch.setServiceMethod("tryMethod");
        branch.setCancelMethod("cancelMethod");
        branch.setExpireTime(new Timestamp(System.currentTimeMillis() - 60000));
        branch.setBeginTime(CoreMetrics.currentTimestamp());
        branch.setMaxRetryTimes(5);
        branchDao.saveEntity(branch);

        tccEngine.checkExpiredTransactions(30000, 5, null);

        NopTccRecord loaded = recordDao.getEntityById(record.getTxnId());
        // aggregateCancel with TIMEOUT_SUCCESS-only branches -> CANCEL_SUCCESS
        assertEquals(TccStatus.CANCEL_SUCCESS.getCode(), loaded.getStatus());

        NopTccBranchRecord loadedBranch = branchDao.getEntityById(branch.getBranchId());
        // TRY_FAILED branches are already in a cancelled-like state and don't need cancel
        assertEquals(TccStatus.TRY_FAILED.getCode(), loadedBranch.getStatus());
    }

    @Test
    public void testRunBranchTransaction() {
        IEntityDao<NopTccRecord> recordDao = daoProvider.daoFor(NopTccRecord.class);

        // runInSession keeps the ORM session open so lazy-loads (e.g. branch.getTccRecord()) work
        String txnId = ormTemplate.runInSession(session -> {
            return tccEngine.runInTransaction("test-branch", txn -> {
                TccBranchRequest request = new TccBranchRequest();
                request.setServiceName("testService");
                request.setServiceMethod("tryMethod");
                request.setConfirmMethod("confirmMethod");
                request.setCancelMethod("cancelMethod");
                request.setRequest(ApiRequest.build(new HashMap<>()));

                String branchResult = tccEngine.runBranchTransaction(txn, request, branch -> {
                    return "branch-ok";
                });
                assertEquals("branch-ok", branchResult);
                return txn.getTxnId();
            });
        });

        NopTccRecord loaded = recordDao.getEntityById(txnId);
        assertEquals(TccStatus.CONFIRM_SUCCESS.getCode(), loaded.getStatus());

        IEntityDao<NopTccBranchRecord> branchDao = daoProvider.daoFor(NopTccBranchRecord.class);
        NopTccBranchRecord example = new NopTccBranchRecord();
        example.setTxnId(txnId);
        java.util.List<NopTccBranchRecord> branches = branchDao.findAllByExample(example);
        assertEquals(1, branches.size());
        assertEquals(TccStatus.CONFIRM_SUCCESS.getCode(), branches.get(0).getStatus());
    }

    @Test
    public void testStateConflictProtection() {
        ITccTransaction txn = tccEngine.newTransaction("test-conflict");
        txn.beginAsync().toCompletableFuture().join();
        txn.endAsync(false, ApiResponse.success(null), null).toCompletableFuture().join();
        assertEquals(TccStatus.CONFIRM_SUCCESS, txn.getTccStatus());

        // state machine blocks the cancel, but endAsync still propagates the original exception
        assertThrows(Exception.class, () -> {
            txn.endAsync(false, null, new RuntimeException("late error")).toCompletableFuture().join();
        });
        assertEquals(TccStatus.CONFIRM_SUCCESS, txn.getTccStatus());
    }

    @Test
    public void testNestedRunInTransaction() {
        String[] outterTxnId = new String[1];

        tccEngine.runInTransaction("test-nested", txn1 -> {
            assertEquals(TccStatus.TRYING, txn1.getTccStatus());
            outterTxnId[0] = txn1.getTxnId();

            tccEngine.runInTransaction("test-nested", txn2 -> {
                // same txnGroup should reuse the same transaction
                assertEquals(txn1.getTxnId(), txn2.getTxnId());
                return null;
            });
            return null;
        });

        IEntityDao<NopTccRecord> dao = daoProvider.daoFor(NopTccRecord.class);
        NopTccRecord loaded = dao.getEntityById(outterTxnId[0]);
        assertEquals(TccStatus.CONFIRM_SUCCESS.getCode(), loaded.getStatus());
    }
}
