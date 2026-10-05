package io.nop.treesitter.parser;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.compat.TreeSitterBash;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

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
 * <p><b>Why this test asserts the throw.</b> It pins the current behavior so the
 * defect is reproducible, <b>not</b> because the throw is desired. Phase 2 of
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
     * The four known reproducers. Looped rather than parameterized because this
     * module does not depend on {@code junit-jupiter-params}.
     */
    @Test
    void malformedExpansionAbortsTheParse() {
        String[] sources = {"echo ${x!y}", "echo ${x!y}z", "echo ${x! y}", "echo ${x!yy}"};
        for (String source : sources) {
            assertThrows(TreeSitterException.class, () -> TSParser.parse(bash(), source),
                    "known defect: recovery oscillates and the no-progress backstop aborts <<" + source + ">>");
        }
    }

    @Test
    void theAbortIsTheNoProgressBackstopNotALexerFailure() {
        TreeSitterException failure =
                assertThrows(TreeSitterException.class, () -> TSParser.parse(bash(), "echo ${x!y}"));
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
