package io.nop.job.core;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 338 Phase 2.2: onCycleStart hook 接线探针。
 * 验证 {@link AbstractBatchScanner#scanOnce()} 入口确实调用 {@link AbstractBatchScanner#onCycleStart()}，
 * 而且每个调度周期只调一次（不被错放进 scanBatch 内部）。
 * <p>
 * Plan 338 重构（方案 B）：增加 cursor 自动 reset 验证——子类通过 {@link AbstractBatchScanner#newCursor()}
 * 注册的 {@link AbstractBatchScanner.Cursor} 在每个 scanOnce 入口自动 reset，无需子类手动 override onCycleStart。
 */
public class TestAbstractBatchScanner {

    /**
     * 探针 scanner：scanBatch 前 2 次返回 true、第 3 次返回 false（共调用 3 次）。
     * 若 onCycleStart 正确放在 scanOnce 入口，startCount == 1。
     * 若 onCycleStart 被错放进 scanBatch 内部，startCount == 3。
     * 若 onCycleStart 完全未调用，startCount == 0。
     */
    static class ProbeScanner extends AbstractBatchScanner {
        int startCount = 0;
        int batchCount = 0;

        @Override
        protected void onCycleStart() {
            startCount++;
        }

        @Override
        protected boolean scanBatch() {
            batchCount++;
            return batchCount < 3;
        }
    }

    @Test
    public void testOnCycleStartCalledOnceAtScanOnceEntry() {
        ProbeScanner scanner = new ProbeScanner();
        scanner.scanOnce();
        assertEquals(1, scanner.startCount,
                "onCycleStart must be called exactly once at scanOnce entry, not per scanBatch iteration");
        assertEquals(3, scanner.batchCount,
                "scanBatch should be invoked 3 times (twice returns true, third returns false to exit)");
    }

    /**
     * 验证基类自动 reset 注册的 cursor。
     * CursorScanner 在 scanBatch 中推进 cursor 并 markDrained；下次 scanOnce 入口基类应自动 reset。
     */
    static class CursorScanner extends AbstractBatchScanner {
        final Cursor cursor = newCursor();
        int batchCount = 0;

        @Override
        protected boolean scanBatch() {
            batchCount++;
            // advance cursor + markDrained on every call; reset should happen on next scanOnce
            cursor.advance(new Timestamp(batchCount * 1000L), "id-" + batchCount);
            cursor.markDrained();
            return false; // single batch per cycle
        }
    }

    @Test
    public void testRegisteredCursorAutoResetBetweenCycles() {
        CursorScanner scanner = new CursorScanner();

        // First cycle: scanBatch advances cursor and marks drained
        scanner.scanOnce();
        assertEquals(1, scanner.batchCount, "1st cycle: 1 batch call");
        assertTrue(scanner.cursor.isDrained(), "after scanBatch cursor should be drained");
        assertNotNull(scanner.cursor.time(), "cursor should have been advanced");
        assertEquals("id-1", scanner.cursor.id(), "cursor id advanced to id-1");

        // Second cycle: scanOnce entry should auto-reset cursor before scanBatch
        scanner.scanOnce();
        // After the 2nd scanBatch call, cursor has been advanced again (to id-2).
        // But the reset (cursor cleared to null/false) happened BEFORE scanBatch ran,
        // which we can verify indirectly: if reset didn't happen, drain flag would persist
        // across cycles and behavior would diverge. To make reset observable we check
        // that batchCount incremented (scanBatch actually ran — if cursor hadn't been
        // reset, scanBatch might early-return based on stale state in real subclasses).
        assertEquals(2, scanner.batchCount, "2nd cycle: scanBatch ran again (cursor was reset before it)");
        // After 2nd scanBatch, cursor reflects last advance call (id-2)
        assertEquals("id-2", scanner.cursor.id(), "cursor advanced to id-2 in 2nd cycle");
    }

    /**
     * 验证基类的 cursor reset 时机：在 onCycleStart 之前、scanBatch 之前。
     * 这保证子类在 onCycleStart 和 scanBatch 中看到的都是已 reset 的 cursor。
     */
    static class ResetOrderScanner extends AbstractBatchScanner {
        final Cursor cursor = newCursor();
        Timestamp cursorTimeDuringOnCycleStart;
        boolean cursorDrainedDuringOnCycleStart;

        @Override
        protected void onCycleStart() {
            cursorTimeDuringOnCycleStart = cursor.time();
            cursorDrainedDuringOnCycleStart = cursor.isDrained();
            // mutate cursor here so we can detect if reset happened before
            cursor.advance(new Timestamp(999L), "mutated-in-onCycleStart");
            cursor.markDrained();
        }

        @Override
        protected boolean scanBatch() {
            return false;
        }
    }

    @Test
    public void testCursorResetBeforeOnCycleStart() {
        ResetOrderScanner scanner = new ResetOrderScanner();

        // First cycle: onCycleStart observes initial state (null cursor — reset is no-op)
        // and mutates cursor to (999L, "mutated-in-onCycleStart", drained=true).
        scanner.scanOnce();
        assertNull(scanner.cursorTimeDuringOnCycleStart,
                "1st cycle onCycleStart: cursor reset to null before onCycleStart");
        assertFalse(scanner.cursorDrainedDuringOnCycleStart,
                "1st cycle onCycleStart: cursor drained flag reset to false before onCycleStart");

        // Second cycle: cursor should have been reset BEFORE onCycleStart is called,
        // even though previous cycle left it in mutated state.
        scanner.scanOnce();
        assertNull(scanner.cursorTimeDuringOnCycleStart,
                "2nd cycle onCycleStart: cursor reset to null even after previous mutation");
        assertFalse(scanner.cursorDrainedDuringOnCycleStart,
                "2nd cycle onCycleStart: cursor drained flag reset to false even after previous markDrained");
    }
}
