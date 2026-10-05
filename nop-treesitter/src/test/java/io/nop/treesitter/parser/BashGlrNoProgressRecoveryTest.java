package io.nop.treesitter.parser;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.compat.TreeSitterBash;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterizes the GLR error-recovery no-progress abort on malformed bash.
 *
 * <p>The module contract in {@code docs-for-ai/03-modules/nop-treesitter.md} says a
 * syntax error does <b>not</b> throw — it returns a tree containing
 * {@code (ERROR ...)} / {@code (MISSING "token")} nodes. On the inputs below the
 * runtime violates that contract, and the official C runtime (tree-sitter CLI
 * 0.25.10) does not.
 *
 * <p><b>Status.</b> One of the four reproducers is fixed (see
 * {@link #aSpacedMalformedExpansionRecoversWithAnErrorNode()}); the other three are
 * outstanding. The remaining tests pin the throw so the defect stays
 * reproducible, <b>not</b> because the throw is desired. Phase 2 of
 * {@code ai-dev/plans/nop-treesitter/2026-10-04-1900-1-glr-recovery-livelock-guard.md}
 * will invert these assertions to "returns a tree containing ERROR/MISSING". The
 * assertions deliberately check only that a {@link TreeSitterException} is thrown
 * and never bind its message, so the eventual fix does not require rewriting them.
 *
 * <p>The C oracle's trees for these inputs, recorded as the Phase 2 target:
 * {@code echo ${x!y}} →
 * {@code (program (command name: (command_name (word))) (ERROR (variable_name) (word)))}.
 */
class BashGlrNoProgressRecoveryTest {

    private static Language bash() {
        return new TreeSitterBash().language();
    }

    /**
     * The reproducers fixed by porting C's summary hand-off in
     * {@code ts_stack_renumber_version} (see the plan's "Root cause" section).
     * Both now satisfy the documented contract: a tree containing an ERROR or
     * MISSING node, and no exception. The expected trees are the ones the C
     * oracle produces.
     */
    @Test
    void theReproducersFixedByPortingTheSummaryHandoffRecover() {
        // A spaced `!` and an unterminated expansion both recover now.
        assertEquals("(program (command (command_name (word))) (ERROR (variable_name) (word)))",
                TSParser.parse(bash(), "echo ${x! y}").toSexpString(false));
        assertEquals("(program (command (command_name (word)) (expansion (variable_name) "
                        + "(MISSING \"}\")) (word)))",
                TSParser.parse(bash(), "echo ${x!yy").toSexpString(false));
    }

    /**
     * The reproducers still outstanding: the unspaced `!y` forms, with or without
     * a trailing suffix and with the expansion closed. Recovery reaches the same
     * stalled state but loses the stack summary by a different route, so strategy
     * 1 stays disabled. Tracked in
     * {@code ai-dev/plans/nop-treesitter/2026-10-04-1900-1-glr-recovery-livelock-guard.md}.
     *
     * <p>These assert the current behaviour so the defect stays reproducible; they
     * deliberately do not bind the exception message.
     */
    @Test
    void unspacedMalformedExpansionStillAborts() {
        for (String source : new String[]{"echo ${x!y}", "echo ${x!y}z", "echo ${x!yy}"}) {
            assertThrows(TreeSitterException.class, () -> TSParser.parse(bash(), source),
                    "known outstanding defect for <<" + source + ">>");
        }
    }

    @Test
    void theAbortIsTheNoProgressBackstopNotALexerFailure() {
        TreeSitterException failure =
                assertThrows(TreeSitterException.class, () -> TSParser.parse(bash(), "echo ${x!y}"));
        // The backstop, not a lexer failure: a syntax error must never surface as
        // anything else.
        assertTrue(String.valueOf(failure.getMessage()).contains("no progress"),
                "expected the no-progress backstop, got: " + failure.getMessage());
    }

    @Test
    void wellFormedInputIsUnaffected() {
        // The control: the same grammar and scanner parse a valid script fine, so
        // the defect is specific to error recovery, not to bash or the scanner.
        String tree = TSParser.parse(bash(), "echo ok").toSexpString(false);
        assertFalse(tree.contains("ERROR") || tree.contains("MISSING"),
                "well-formed input must parse clean, got " + tree);
    }
}
