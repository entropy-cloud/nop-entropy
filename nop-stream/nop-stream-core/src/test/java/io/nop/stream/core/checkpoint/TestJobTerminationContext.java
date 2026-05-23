package io.nop.stream.core.checkpoint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestJobTerminationContext {

    @Test
    void testCancelFactory() {
        JobTerminationContext ctx = JobTerminationContext.cancel();
        assertEquals(JobTerminationMode.CANCEL, ctx.getMode());
        assertTrue(ctx.isAbortTransactions());
        assertFalse(ctx.isWaitForSinkCommit());
    }

    @Test
    void testDrainFactory() {
        JobTerminationContext ctx = JobTerminationContext.drain(60000);
        assertEquals(JobTerminationMode.DRAIN, ctx.getMode());
        assertEquals(60000, ctx.getTimeout());
        assertTrue(ctx.isWaitForSinkCommit());
        assertFalse(ctx.isAbortTransactions());
    }

    @Test
    void testSuspendFactory() {
        JobTerminationContext ctx = JobTerminationContext.suspend("ns1");
        assertEquals(JobTerminationMode.SUSPEND, ctx.getMode());
        assertEquals("ns1", ctx.getSavepointNamespace());
    }

    @Test
    void testExportSavepointFactory() {
        JobTerminationContext ctx = JobTerminationContext.exportSavepoint("ns2");
        assertEquals(JobTerminationMode.EXPORT_SAVEPOINT, ctx.getMode());
        assertEquals("ns2", ctx.getSavepointNamespace());
    }
}
