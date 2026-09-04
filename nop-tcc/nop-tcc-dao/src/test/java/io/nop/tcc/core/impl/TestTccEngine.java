package io.nop.tcc.core.impl;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import io.nop.tcc.api.ITccTransaction;
import io.nop.tcc.api.TccBranchRequest;
import io.nop.tcc.api.TccStatus;
import io.nop.tcc.dao.entity.NopTccBranchRecord;
import io.nop.tcc.core.TccCoreErrors;
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
                throw new NopException(TccCoreErrors.ERR_TCC_NO_TXN_ID);
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
            txn.endAsync(false, null, new NopException(TccCoreErrors.ERR_TCC_NO_TXN_ID)).toCompletableFuture().join();
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

    /**
     * confirm RPC以异常完成（网络故障/超时）时，分支必须记为CONFIRM_FAILED、全局CONFIRM_FAILED，
     * 由恢复循环重试。此前ex参数被忽略，异常完成的confirm被误写入终态CONFIRM_SUCCESS
     */
    @Test
    public void testConfirmRpcExceptionMarksConfirmFailed() {
        IEntityDao<NopTccRecord> recordDao = daoProvider.daoFor(NopTccRecord.class);
        mockRpcServiceInvoker.setException(new NopException(TccCoreErrors.ERR_TCC_NO_TXN_ID));

        String[] txnIdHolder = new String[1];
        ormTemplate.runInSession(session -> {
            return tccEngine.runInTransaction("test-confirm-rpc-ex", txn -> {
                txnIdHolder[0] = txn.getTxnId();
                tccEngine.runBranchTransaction(txn, newBranchRequest("svcA", "confirmMethod", "cancelMethod"),
                        branch -> "ok");
                return null;
            });
        });

        NopTccRecord loaded = recordDao.getEntityById(txnIdHolder[0]);
        assertEquals(TccStatus.CONFIRM_FAILED.getCode(), loaded.getStatus());

        NopTccBranchRecord branch = findBranch(txnIdHolder[0]);
        assertEquals(TccStatus.CONFIRM_FAILED.getCode(), branch.getStatus());
        assertNotNull(branch.getCommitErrorMessage());
    }

    /**
     * cancel RPC以异常完成时，分支必须记为CANCEL_FAILED、全局CANCEL_FAILED以便重试。
     * 此前ex参数被忽略，异常完成的cancel被误写入终态CANCEL_SUCCESS
     */
    @Test
    public void testCancelRpcExceptionMarksCancelFailed() {
        IEntityDao<NopTccRecord> recordDao = daoProvider.daoFor(NopTccRecord.class);
        mockRpcServiceInvoker.setException(new NopException(TccCoreErrors.ERR_TCC_NO_TXN_ID));

        String[] txnIdHolder = new String[1];
        ormTemplate.runInSession(session -> {
            return assertThrows(Exception.class, () -> {
                tccEngine.runInTransaction("test-cancel-rpc-ex", txn -> {
                    txnIdHolder[0] = txn.getTxnId();
                    tccEngine.runBranchTransaction(txn, newBranchRequest("svcA", "confirmMethod", "cancelMethod"),
                            branch -> "ok");
                    throw new NopException(TccCoreErrors.ERR_TCC_NO_TXN_ID);
                });
            });
        });

        NopTccRecord loaded = recordDao.getEntityById(txnIdHolder[0]);
        assertEquals(TccStatus.CANCEL_FAILED.getCode(), loaded.getStatus());

        NopTccBranchRecord branch = findBranch(txnIdHolder[0]);
        assertEquals(TccStatus.CANCEL_FAILED.getCode(), branch.getStatus());
    }

    /**
     * confirm阶段崩溃恢复：全局与分支停在CONFIRMING时，超时扫描必须续跑confirm直到成功。
     * 此前CONFIRMING被分支rollbackOnly导向cancel路径又被doCancelAsync拦截为no-op，事务永久卡死
     */
    @Test
    public void testConfirmRecoveryAfterCrash() {
        IEntityDao<NopTccRecord> recordDao = daoProvider.daoFor(NopTccRecord.class);
        IEntityDao<NopTccBranchRecord> branchDao = daoProvider.daoFor(NopTccBranchRecord.class);

        NopTccRecord record = recordDao.newEntity();
        record.setTxnGroup("test-confirm-recovery");
        record.setAppId("test");
        record.setStatus(TccStatus.CONFIRMING.getCode());
        record.setBeginTime(CoreMetrics.currentTimestamp());
        record.setExpireTime(new Timestamp(System.currentTimeMillis() - 60000));
        record.setRetryTimes(0);
        recordDao.saveEntity(record);

        NopTccBranchRecord branch = branchDao.newEntity();
        branch.setTxnId(record.getTxnId());
        branch.setBranchNo(1);
        branch.setStatus(TccStatus.CONFIRMING.getCode());
        branch.setServiceName("testService");
        branch.setServiceMethod("tryMethod");
        branch.setConfirmMethod("confirmMethod");
        branch.setCancelMethod("cancelMethod");
        branch.setRequestData("{\"data\":{}}");
        branch.setExpireTime(new Timestamp(System.currentTimeMillis() - 60000));
        branch.setBeginTime(CoreMetrics.currentTimestamp());
        branch.setMaxRetryTimes(5);
        branchDao.saveEntity(branch);

        tccEngine.checkExpiredTransactions(30000, 5, null);

        assertEquals(TccStatus.CONFIRM_SUCCESS.getCode(),
                recordDao.getEntityById(record.getTxnId()).getStatus());
        assertEquals(TccStatus.CONFIRM_SUCCESS.getCode(),
                branchDao.getEntityById(branch.getBranchId()).getStatus());
    }

    /**
     * 混合分支的cancel补偿：无confirmMethod的分支try成功后自动CONFIRM_SUCCESS，
     * 全局回滚时必须跳过它（已确认，不可cancel），只cancel其余分支。
     * 此前cancelAllAsync只跳过isCancelled分支，CONFIRM_SUCCESS分支导致beginCancelAsync
     * 同步抛异常，中断后续分支的补偿且全局误报CANCEL_SUCCESS
     */
    @Test
    public void testCancelSkipsAutoConfirmedBranch() {
        IEntityDao<NopTccRecord> recordDao = daoProvider.daoFor(NopTccRecord.class);

        String[] txnIdHolder = new String[1];
        ormTemplate.runInSession(session -> {
            return assertThrows(Exception.class, () -> {
                tccEngine.runInTransaction("test-cancel-mixed", txn -> {
                    txnIdHolder[0] = txn.getTxnId();
                    // 分支A：无confirmMethod，try成功后自动CONFIRM_SUCCESS
                    tccEngine.runBranchTransaction(txn, newBranchRequest("svcA", null, "cancelA"),
                            branch -> "a-ok");
                    // 分支B：正常的confirm/cancel分支
                    tccEngine.runBranchTransaction(txn, newBranchRequest("svcB", "confirmB", "cancelB"),
                            branch -> "b-ok");
                    throw new NopException(TccCoreErrors.ERR_TCC_NO_TXN_ID);
                });
            });
        });

        NopTccRecord loaded = recordDao.getEntityById(txnIdHolder[0]);
        assertEquals(TccStatus.CANCEL_SUCCESS.getCode(), loaded.getStatus());

        NopTccBranchRecord branchA = null;
        NopTccBranchRecord branchB = null;
        for (NopTccBranchRecord b : findBranches(txnIdHolder[0])) {
            if ("svcA".equals(b.getServiceName())) {
                branchA = b;
            } else if ("svcB".equals(b.getServiceName())) {
                branchB = b;
            }
        }
        assertNotNull(branchA);
        assertNotNull(branchB);
        // 已自动确认的分支保持CONFIRM_SUCCESS，可取消分支被正常补偿
        assertEquals(TccStatus.CONFIRM_SUCCESS.getCode(), branchA.getStatus());
        assertEquals(TccStatus.CANCEL_SUCCESS.getCode(), branchB.getStatus());
    }

    /**
     * task同步抛异常（而非返回异常future）时也必须执行endAsync补偿。
     * 此前thenCompose内同步抛出会跳过endAsync，record残留TRYING只能等超时扫描兜底
     */
    @Test
    public void testTaskSyncThrowStillTriggersCompensation() {
        IEntityDao<NopTccRecord> recordDao = daoProvider.daoFor(NopTccRecord.class);

        String[] txnIdHolder = new String[1];
        assertThrows(Exception.class, () -> {
            tccEngine.runInTransactionAsync("test-sync-throw", txn -> {
                txnIdHolder[0] = txn.getTxnId();
                throw new NopException(TccCoreErrors.ERR_TCC_NO_TXN_ID);
            }).toCompletableFuture().join();
        });

        NopTccRecord loaded = recordDao.getEntityById(txnIdHolder[0]);
        // endAsync已经执行了cancel补偿，而不是残留TRYING
        assertEquals(TccStatus.CANCEL_SUCCESS.getCode(), loaded.getStatus());
    }

    private TccBranchRequest newBranchRequest(String serviceName, String confirmMethod, String cancelMethod) {
        TccBranchRequest request = new TccBranchRequest();
        request.setServiceName(serviceName);
        request.setServiceMethod("tryMethod");
        request.setConfirmMethod(confirmMethod);
        request.setCancelMethod(cancelMethod);
        request.setRequest(ApiRequest.build(new HashMap<>()));
        return request;
    }

    private NopTccBranchRecord findBranch(String txnId) {
        for (NopTccBranchRecord b : findBranches(txnId)) {
            return b;
        }
        throw new IllegalStateException("no branch record for txn " + txnId);
    }

    private java.util.List<NopTccBranchRecord> findBranches(String txnId) {
        IEntityDao<NopTccBranchRecord> branchDao = daoProvider.daoFor(NopTccBranchRecord.class);
        NopTccBranchRecord example = new NopTccBranchRecord();
        example.setTxnId(txnId);
        return branchDao.findAllByExample(example);
    }
}
