package io.nop.ib.cli;

import io.nop.ib.compile.IncrementalCompileExecutor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Human-mode summary line shape pin (coblab-rpg 2026-11-22): a RED compile
 * must never print the green-shaped {@code compiled: []} — the line must name
 * the failure and the failing module explicitly, so an AI reading only the
 * summary line cannot mistake a failure for a no-op.
 */
class TestHumanCompileLine {
    @Test
    void redCompileLineNamesFailureAndModule() {
        IncrementalCompileExecutor.CompileOutcome outcome =
                new IncrementalCompileExecutor.CompileOutcome();
        outcome.failModule("rpg-lang");
        String line = CompileCommand.humanCompileLine(outcome);
        assertTrue(line.startsWith("compile FAILED"), "red line must say FAILED: " + line);
        assertTrue(line.contains("rpg-lang"), "red line must name failing module: " + line);
        assertFalse(line.contains("compiled:"), "red line must not reuse green shape: " + line);
    }

    @Test
    void redCompileLineWithoutFailedModuleStillExplicit() {
        IncrementalCompileExecutor.CompileOutcome outcome =
                new IncrementalCompileExecutor.CompileOutcome();
        outcome.failModule(null);
        String line = CompileCommand.humanCompileLine(outcome);
        assertTrue(line.startsWith("compile FAILED"), "red line must say FAILED: " + line);
        assertTrue(line.contains("unknown"), "missing module degrades to 'unknown': " + line);
    }

    @Test
    void greenCompileLineKeepsShape() {
        IncrementalCompileExecutor.CompileOutcome outcome =
                new IncrementalCompileExecutor.CompileOutcome();
        outcome.addCompiledModule("m", 2, 1);
        String line = CompileCommand.humanCompileLine(outcome);
        assertTrue(line.contains("compiled: [m]"), "green keeps compiled shape: " + line);
        assertFalse(line.contains("FAILED"), "green line must not say FAILED: " + line);
    }
}
