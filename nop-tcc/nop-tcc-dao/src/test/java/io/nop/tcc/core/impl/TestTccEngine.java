package io.nop.tcc.core.impl;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import io.nop.tcc.api.ITccRecord;
import io.nop.tcc.api.ITccTransaction;
import io.nop.tcc.api.TccBranchRequest;
import io.nop.tcc.api.TccStatus;
import io.nop.tcc.dao.entity.NopTccBranchRecord;
import io.nop.tcc.core.TccCoreErrors;
import io.nop.tcc.dao.entity.NopTccRecord;
import io.nop.tcc.dao.test.AbstractTccTest;
import io.nop.tcc.dao.store.TccRecordStore;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        NopTccBranchRecord recovered = branchDao.getEntityById(branch.getBranchId());
        assertEquals(TccStatus.CONFIRM_SUCCESS.getCode(), recovered.getStatus());
        // 续跑confirm时分支补偿尝试计数递增
        assertEquals(Integer.valueOf(1), recovered.getRetryTimes());
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

    /**
     * 单条补偿挂死时checkExpiredTransactions必须受超时保护继续运行，
     * 此前join()无超时会永久阻塞整个调度循环
     */
    @Test
    @org.junit.jupiter.api.Timeout(20)
    public void testCheckExpiredNotBlockedByHangingCompensation() {
        IEntityDao<NopTccRecord> recordDao = daoProvider.daoFor(NopTccRecord.class);
        IEntityDao<NopTccBranchRecord> branchDao = daoProvider.daoFor(NopTccBranchRecord.class);

        NopTccRecord record = recordDao.newEntity();
        record.setTxnGroup("test-hang");
        record.setAppId("test");
        record.setStatus(TccStatus.TRYING.getCode());
        record.setBeginTime(CoreMetrics.currentTimestamp());
        record.setExpireTime(new Timestamp(System.currentTimeMillis() - 60000));
        record.setRetryTimes(0);
        recordDao.saveEntity(record);

        NopTccBranchRecord branch = branchDao.newEntity();
        branch.setTxnId(record.getTxnId());
        branch.setBranchNo(1);
        branch.setStatus(TccStatus.TRY_SUCCESS.getCode());
        branch.setServiceName("testService");
        branch.setServiceMethod("tryMethod");
        branch.setConfirmMethod("confirmMethod");
        branch.setCancelMethod("cancelMethod");
        branch.setRequestData("{\"data\":{}}");
        branch.setExpireTime(new Timestamp(System.currentTimeMillis() - 60000));
        branch.setBeginTime(CoreMetrics.currentTimestamp());
        branch.setMaxRetryTimes(5);
        branchDao.saveEntity(branch);

        mockRpcServiceInvoker.setHang(true);
        long begin = System.currentTimeMillis();
        // maxRetryCount=1：第一轮超时后retryTimes=1，下一轮过滤排除，循环结束
        tccEngine.checkExpiredTransactions(500, 1, null);
        long elapsed = System.currentTimeMillis() - begin;
        // 500ms超时后放行，而不是永久阻塞
        assertTrue(elapsed < 15000, "checkExpiredTransactions should not block on hanging compensation");

        // 补偿超时停在中间态，分支retryTimes已计一次补偿尝试
        NopTccBranchRecord loadedBranch = branchDao.getEntityById(branch.getBranchId());
        assertEquals(TccStatus.CONFIRMING.getCode(), loadedBranch.getStatus());
        assertEquals(Integer.valueOf(1), loadedBranch.getRetryTimes());
    }

    @Test
    public void testFromCodeBounds() {
        assertNull(TccStatus.fromCode(-1));
        assertNull(TccStatus.fromCode(99));
        assertNull(TccStatus.fromCode((Integer) null));
        assertEquals(TccStatus.TRYING, TccStatus.fromCode(1));
    }

    /**
     * status列为null的脏数据全局记录走endAsync不得NPE：
     * null状态不命中confirm/cancel拦截条件，按普通路径处理（CAS因from=null不落库，仅告警）
     */
    @Test
    public void testEndAsyncWithNullStatusRecordNotCrash() {
        IEntityDao<NopTccRecord> recordDao = daoProvider.daoFor(NopTccRecord.class);
        NopTccRecord record = recordDao.newEntity();
        record.setTxnGroup("test-null-status");
        record.setAppId("test");
        record.setBeginTime(CoreMetrics.currentTimestamp());
        record.setExpireTime(new Timestamp(System.currentTimeMillis() + 60000));
        recordDao.initEntityId(record);
        // status保持null

        TccTransaction txn = new TccTransaction(true, record, tccEngine);
        txn.endAsync(false, ApiResponse.success(null), null).toCompletableFuture().join();
    }

    /**
     * beginCancelAsync对不可取消分支使用专用的cancel阶段错误码，
     * 此前复用ERR_TCC_INVALID_CONFIRM_BRANCH_STATUS报误导性错误
     */
    @Test
    public void testBeginCancelInvalidStatusErrorCode() {
        // 覆盖getTxnGroup避免NopTccBranchRecord对tccRecord关联的懒加载
        NopTccBranchRecord branch = new NopTccBranchRecord() {
            @Override
            public String getTxnGroup() {
                return "g";
            }
        };
        branch.setStatus(TccStatus.CONFIRM_SUCCESS.getCode());

        TccBranchTransaction branchTxn = new TccBranchTransaction(branch, tccEngine);
        NopException e = assertThrows(NopException.class, () -> branchTxn.beginCancelAsync(false));
        assertEquals(TccCoreErrors.ERR_TCC_INVALID_CANCEL_BRANCH_STATUS.getErrorCode(), e.getErrorCode());
    }

    /**
     * task抛Error（而非Exception）时：新事务路径必须先执行endAsync补偿再原样传播Error，
     * registry不残留。此前catch Exception导致Error路径缺少保护
     */
    @Test
    public void testErrorThrowStillCompensatesAndRestoresRegistry() {
        IEntityDao<NopTccRecord> recordDao = daoProvider.daoFor(NopTccRecord.class);

        String[] txnIdHolder = new String[1];
        CompletionException ce = assertThrows(CompletionException.class, () -> {
            tccEngine.runInTransactionAsync("test-error-throw", txn -> {
                txnIdHolder[0] = txn.getTxnId();
                throw new AssertionError("error boom");
            }).toCompletableFuture().join();
        });
        assertTrue(ce.getCause() instanceof AssertionError, "Error should propagate unwrapped");

        // Error同样执行了cancel补偿
        NopTccRecord loaded = recordDao.getEntityById(txnIdHolder[0]);
        assertEquals(TccStatus.CANCEL_SUCCESS.getCode(), loaded.getStatus());
        assertNull(tccEngine.getCurrentTransaction("test-error-throw"));

        // 参与已有事务路径（runTaskWithExitingTxnAsync）抛Error时registry同样恢复
        NopTccRecord existing = recordDao.newEntity();
        existing.setTxnGroup("test-error-exit");
        existing.setAppId("test");
        existing.setStatus(TccStatus.TRYING.getCode());
        existing.setBeginTime(CoreMetrics.currentTimestamp());
        existing.setExpireTime(new Timestamp(System.currentTimeMillis() + 60000));
        existing.setRetryTimes(0);
        recordDao.saveEntity(existing);

        CompletionException ce2 = assertThrows(CompletionException.class, () -> {
            tccEngine.runInTransactionAsync("test-error-exit", existing.getTxnId(), txn -> {
                throw new AssertionError("exit boom");
            }).toCompletableFuture().join();
        });
        assertTrue(ce2.getCause() instanceof AssertionError);
        assertNull(tccEngine.getCurrentTransaction("test-error-exit"));
    }

    /**
     * try阶段的业务失败响应要记录失败原因到分支error字段，
     * 此前error传null丢失诊断信息
     */
    @Test
    public void testTryFailedRecordsErrorCode() {
        IEntityDao<NopTccRecord> recordDao = daoProvider.daoFor(NopTccRecord.class);
        String[] txnIdHolder = new String[1];

        ormTemplate.runInSession(session -> {
            return tccEngine.runInTransaction("test-try-failed-err", txn -> {
                txnIdHolder[0] = txn.getTxnId();
                tccEngine.runBranchTransaction(txn, newBranchRequest("svcA", "confirmA", "cancelA"),
                        branch -> ApiResponse.error(new ErrorBean("ERR-TRY-FAIL").description("biz reject")));
                return null;
            });
        });

        NopTccRecord loaded = recordDao.getEntityById(txnIdHolder[0]);
        assertEquals(TccStatus.CANCEL_SUCCESS.getCode(), loaded.getStatus());

        NopTccBranchRecord branch = findBranch(txnIdHolder[0]);
        assertEquals(TccStatus.TRY_FAILED.getCode(), branch.getStatus());
        assertEquals("ERR-TRY-FAIL", branch.getErrorCode());
    }

    /**
     * CAS保护：stale writer（内存from状态与DB不一致）不得覆盖已落库终态
     */
    @Test
    public void testStaleWriterDoesNotOverwriteTerminalStatus() {
        IEntityDao<NopTccRecord> recordDao = daoProvider.daoFor(NopTccRecord.class);
        ITccRecord record = tccRecordStore.newTccRecord("test-cas");
        tccRecordStore.saveTccRecordAsync(record, TccStatus.TRYING).toCompletableFuture().join();

        // 另一个writer已把DB推进到终态CONFIRM_SUCCESS
        NopTccRecord other = recordDao.getEntityById(record.getTxnId());
        other.setStatus(TccStatus.CONFIRM_SUCCESS.getCode());
        other.setEndTime(CoreMetrics.currentTimestamp());
        recordDao.updateEntityDirectly(other);

        // stale writer仍持有TRYING视角，尝试写CANCELLING：CAS前置校验失败，跳过
        tccRecordStore.updateTccStatusAsync(record, TccStatus.CANCELLING, null).toCompletableFuture().join();

        assertEquals(TccStatus.CONFIRM_SUCCESS.getCode(),
                recordDao.getEntityById(record.getTxnId()).getStatus());
    }

    /**
     * 分支记录随父记录生命周期清理：已完结父事务（BIZ_CANCEL_FAILED）下的非终态分支
     * （CANCEL_FAILED）也一并删除，不再产生孤儿分支
     */
    @Test
    public void testRemoveCompletedRecordsDeletesNonTerminalBranchesOfFinishedParent() {
        IEntityDao<NopTccRecord> recordDao = daoProvider.daoFor(NopTccRecord.class);
        IEntityDao<NopTccBranchRecord> branchDao = daoProvider.daoFor(NopTccBranchRecord.class);

        NopTccRecord parent = recordDao.newEntity();
        parent.setTxnGroup("test-orphan");
        parent.setAppId("test");
        parent.setStatus(TccStatus.BIZ_CANCEL_FAILED.getCode());
        parent.setBeginTime(new Timestamp(System.currentTimeMillis() - 86400000));
        parent.setExpireTime(new Timestamp(System.currentTimeMillis() - 86400000));
        parent.setRetryTimes(0);
        recordDao.saveEntity(parent);

        NopTccBranchRecord branch = branchDao.newEntity();
        branch.setTxnId(parent.getTxnId());
        branch.setBranchNo(1);
        // 非终态分支：旧的按分支自身状态过滤的清理逻辑永远不会删它
        branch.setStatus(TccStatus.CANCEL_FAILED.getCode());
        branch.setServiceName("svc");
        branch.setServiceMethod("m");
        branch.setExpireTime(new Timestamp(System.currentTimeMillis() - 86400000));
        branch.setBeginTime(new Timestamp(System.currentTimeMillis() - 86400000));
        branch.setMaxRetryTimes(5);
        branchDao.saveEntity(branch);

        tccRecordStore.removeCompletedRecords(3600000, true);

        assertNull(recordDao.getEntityById(parent.getTxnId()));
        assertNull(branchDao.getEntityById(branch.getBranchId()));
    }

    /**
     * 参与已有事务时status列为null的脏数据记录不NPE：
     * checkTransactionActive对null状态按未完结处理（此前裸调isFinished会NPE）
     */
    @Test
    public void testCheckTransactionActiveWithNullStatusNotCrash() {
        IEntityDao<NopTccRecord> recordDao = daoProvider.daoFor(NopTccRecord.class);
        NopTccRecord record = recordDao.newEntity();
        record.setTxnGroup("test-null-active");
        record.setAppId("test");
        record.setBeginTime(CoreMetrics.currentTimestamp());
        record.setExpireTime(new Timestamp(System.currentTimeMillis() + 60000));
        recordDao.initEntityId(record);
        // status保持null

        TccRecordStore stubStore = new TccRecordStore() {
            @Override
            public CompletionStage<ITccRecord> getTccRecordAsync(String txnGroup, String txnId) {
                return java.util.concurrent.CompletableFuture.completedFuture(record);
            }
        };
        stubStore.setDaoProvider(daoProvider);
        tccEngine.setTccRecordStore(stubStore);
        try {
            String[] executed = new String[1];
            tccEngine.runInTransactionAsync("test-null-active", record.getTxnId(), txn -> {
                executed[0] = "yes";
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }).toCompletableFuture().join();
            assertEquals("yes", executed[0]);
        } finally {
            tccEngine.setTccRecordStore(tccRecordStore);
        }
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
