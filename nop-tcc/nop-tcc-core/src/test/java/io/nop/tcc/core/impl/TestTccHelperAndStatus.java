package io.nop.tcc.core.impl;

import io.nop.tcc.api.TccStatus;
import io.nop.tcc.core.TccCoreConstants;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 TccHelper 的 txnGroup 归一化语义与 TccStatus 状态机谓词：
 * 终态集合、可确认集合、可回滚集合、可取消集合的边界。
 */
public class TestTccHelperAndStatus {

    @Test
    public void testNormalizeTxnGroupDefaults() {
        assertEquals(TccCoreConstants.DEFAULT_TCC_TXN_GROUP, TccHelper.normalizeTxnGroup(null));
        assertEquals(TccCoreConstants.DEFAULT_TCC_TXN_GROUP, TccHelper.normalizeTxnGroup(""));
        assertEquals("custom", TccHelper.normalizeTxnGroup("custom"));
    }

    @Test
    public void testIsDefaultTxnGroup() {
        assertTrue(TccHelper.isDefaultTxnGroup(null));
        assertTrue(TccHelper.isDefaultTxnGroup("default"));
        assertFalse(TccHelper.isDefaultTxnGroup("custom"));
    }

    @Test
    public void testIsSameTxnGroupNormalizesBothSides() {
        assertTrue(TccHelper.isSameTxnGroup(null, "default"));
        assertTrue(TccHelper.isSameTxnGroup("", null));
        assertFalse(TccHelper.isSameTxnGroup("a", "b"));
    }

    @Test
    public void testFromCodeRoundTripAndDirtyData() {
        assertEquals(TccStatus.CREATED, TccStatus.fromCode(0));
        assertEquals(TccStatus.KILLED, TccStatus.fromCode(15));
        assertNull(TccStatus.fromCode(16), "out-of-range code must yield null, not AIOOBE");
        assertNull(TccStatus.fromCode(-1));
        assertNull(TccStatus.fromCode((Integer) null));
    }

    @Test
    public void testFinishedStatusSet() {
        List<Integer> finished = TccStatus.getFinishedStatus();
        assertTrue(TccStatus.CONFIRM_SUCCESS.isFinished());
        assertTrue(TccStatus.CANCEL_SUCCESS.isFinished());
        assertTrue(TccStatus.BIZ_CANCEL_FAILED.isFinished());
        assertTrue(TccStatus.TIMEOUT_SUCCESS.isFinished());
        assertTrue(TccStatus.KILLED.isFinished());
        assertFalse(TccStatus.TRYING.isFinished());
        assertFalse(TccStatus.TIMEOUT_FAILED.isFinished(), "timeout-failed must stay retryable");

        for (TccStatus value : TccStatus.values()) {
            assertEquals(value.isFinished(), finished.contains(value.getCode()),
                    "getFinishedStatus must agree with isFinished for " + value);
        }
    }

    @Test
    public void testAllowConfirmAndRollbackOnly() {
        assertTrue(TccStatus.TRY_SUCCESS.isAllowConfirm());
        assertTrue(TccStatus.CONFIRM_SUCCESS.isAllowConfirm());
        assertFalse(TccStatus.TRYING.isAllowConfirm());

        assertTrue(TccStatus.TRYING.isRollbackOnly());
        assertFalse(TccStatus.TRY_SUCCESS.isRollbackOnly());
        assertFalse(TccStatus.CANCEL_SUCCESS.isRollbackOnly(), "finished states are not rollback-only");
        assertFalse(TccStatus.CREATED.isRollbackOnly(), "created state is not in transaction");
    }

    @Test
    public void testCancellableStates() {
        assertTrue(TccStatus.TRY_SUCCESS.isInTransaction() && !TccStatus.TRY_SUCCESS.isCancelled()
                        && !TccStatus.TRY_SUCCESS.isConfirmed(),
                "TRY_SUCCESS must be cancellable (main compensation target)");
        assertTrue(TccStatus.TIMEOUT_FAILED.isInTransaction(),
                "TIMEOUT_FAILED must stay in transaction for re-compensation");
        assertFalse(TccStatus.TIMEOUT_FAILED.isCancelled());
        assertFalse(TccStatus.CONFIRM_SUCCESS.isCancelled());
        assertTrue(TccStatus.TRY_FAILED.isCancelled(), "try-failed branches need no cancel");
    }
}
