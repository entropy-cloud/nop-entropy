package io.nop.tcc.dao.store;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.tcc.api.ITccBranchRecord;
import io.nop.tcc.api.ITccRecord;
import io.nop.tcc.api.TccBranchRequest;
import io.nop.tcc.api.TccStatus;
import io.nop.tcc.dao.entity.NopTccBranchRecord;
import io.nop.tcc.dao.entity.NopTccRecord;
import io.nop.tcc.dao.test.AbstractTccTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * C10-003 收口门 store 半边（plan 2515 红①，C1-001←C3-001 / C3-002←C3-016）：
 * 全局/分支状态 CAS 更新命中 0 行（并发 stale writer）时，失败必须显式传播
 * （异常完成，类型化 CAS 冲突错误），不得 WARN 后吞为正常返回——
 * 被吞的 0 行返回使互斥守卫基于陈旧内存副本，confirm/cancel RPC 对撞照发。
 */
public class TestTccStoreCasConflict_C10_003 extends AbstractTccTest {

    private static final String CAS_CONFLICT_ERROR_CODE = "nop.err.tcc.status-cas-conflict";

    /** 模拟并发 stale writer：绕过 store 内存副本，按 txnId 直接把 DB 行状态推进 */
    private void overwriteGlobalStatusInDb(String txnId, int statusCode) {
        Map<String, Object> props = new HashMap<>();
        props.put(NopTccRecord.PROP_NAME_status, statusCode);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopTccRecord.PROP_NAME_txnId, txnId));
        long updated = daoProvider.daoFor(NopTccRecord.class).updateByQuery(query, props);
        assertEquals(1, updated, "concurrent writer must flip the db row");
    }

    @Test
    public void C1_001_globalCasZeroRow_mustFailExplicitly_notSwallow() {
        ITccRecord record = tccRecordStore.newTccRecord("cas-conflict-group");
        tccRecordStore.saveTccRecordAsync(record, TccStatus.TRYING).toCompletableFuture().join();

        // 并发 writer 先行推进 DB 状态，本次 CAS（内存副本 from=TRYING）必然命中 0 行
        overwriteGlobalStatusInDb(record.getTxnId(), TccStatus.CONFIRM_SUCCESS.getCode());

        try {
            tccRecordStore.updateTccStatusAsync(record, TccStatus.CANCEL_SUCCESS, null)
                    .toCompletableFuture()
                    .join();
            fail("CAS update hitting 0 rows must fail explicitly instead of being swallowed as a WARN");
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            assertTrue(cause instanceof NopException, "cause must be typed NopException, got " + cause);
            assertEquals(CAS_CONFLICT_ERROR_CODE, ((NopException) cause).getErrorCode(),
                    "CAS conflict must be reported with the typed CAS conflict error code");
        }

        // DB 权威终态不被 stale writer 覆盖
        ITccRecord loaded = tccRecordStore
                .getTccRecordAsync("cas-conflict-group", record.getTxnId()).toCompletableFuture().join();
        assertEquals(TccStatus.CONFIRM_SUCCESS, loaded.getTccStatus(),
                "db terminal status must be preserved (stale writer must not overwrite)");
    }

    @Test
    public void C3_016_branchCasZeroRow_mustFailExplicitly_notSwallow() {
        ITccRecord record = tccRecordStore.newTccRecord("cas-conflict-group");
        tccRecordStore.saveTccRecordAsync(record, TccStatus.TRYING).toCompletableFuture().join();

        TccBranchRequest request = new TccBranchRequest();
        request.setServiceName("svc");
        request.setServiceMethod("tryMethod");
        request.setConfirmMethod("confirmMethod");
        request.setCancelMethod("cancelMethod");

        ITccBranchRecord branch = tccRecordStore.newBranchRecord(record, request);
        tccRecordStore.saveBranchRecordAsync(branch, TccStatus.TRYING).toCompletableFuture().join();

        // 并发 writer 先行推进分支 DB 状态，本次分支 CAS（内存副本 from=TRYING）必然命中 0 行
        Map<String, Object> props = new HashMap<>();
        props.put(NopTccBranchRecord.PROP_NAME_status, TccStatus.CANCEL_SUCCESS.getCode());
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopTccBranchRecord.PROP_NAME_branchId, branch.getBranchId()));
        long updated = daoProvider.daoFor(NopTccBranchRecord.class).updateByQuery(query, props);
        assertEquals(1, updated, "concurrent writer must flip the branch db row");

        try {
            tccRecordStore.updateTccBranchStatusAsync(branch, TccStatus.CANCEL_SUCCESS, null)
                    .toCompletableFuture()
                    .join();
            fail("branch CAS update hitting 0 rows must fail explicitly instead of being swallowed");
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            assertTrue(cause instanceof NopException, "cause must be typed NopException, got " + cause);
            assertEquals(CAS_CONFLICT_ERROR_CODE, ((NopException) cause).getErrorCode(),
                    "branch CAS conflict must be reported with the typed CAS conflict error code");
        }

        NopTccBranchRecord loaded = daoProvider.daoFor(NopTccBranchRecord.class)
                .getEntityById(branch.getBranchId());
        assertNotNull(loaded);
        assertEquals(TccStatus.CANCEL_SUCCESS, TccStatus.fromCode(loaded.getStatus()),
                "db branch terminal status must be preserved (stale writer must not overwrite)");
    }
}
