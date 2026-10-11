package io.nop.treesitter.parser;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.compat.TreeSitterBash;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Error recovery on malformed bash must terminate with an ERROR/MISSING tree rather
 * than aborting the parse.
 *
 * <p>The module contract in {@code docs-for-ai/03-modules/nop-treesitter.md} says a
 * syntax error does <b>not</b> throw — it returns a tree containing
 * {@code (ERROR ...)} / {@code (MISSING "token")} nodes. On the inputs below this
 * runtime used to violate that contract: recovery oscillated between two byte
 * positions until a 100-round backstop threw, where the official C runtime (CLI
 * 0.25.10) recovers normally.
 *
 * <p>The cause was two pieces of recovery state, both traced by instrumenting
 * {@code GLRParser} and comparing against the C runtime read locally at
 * {@code ~/sources/treesitter/tree-sitter} (commit {@code f430449a}):
 *
 * <ol>
 *   <li>{@code renumberVersion} dropped the stack summary. C's
 *       {@code ts_stack_renumber_version} hands the target's summary to the source
 *       before overwriting, because the summary hangs off the {@code StackHead}.
 *       Losing it disarms recovery strategy 1 permanently, leaving only strategy 2 —
 *       the strategy whose merge caused the loss.</li>
 *   <li>The strategy-2 merge measured the merged {@code ERROR_REPEAT} from a
 *       {@code position} captured <em>before</em> the merge's pop. C passes no such
 *       parameter: {@code ts_subtree_new_node} derives the extent from the children.
 *       Measuring from the stale position dropped exactly the popped span, so the
 *       merged node never grew and recovery could never get past it.</li>
 * </ol>
 *
 * <p>Every expected tree below was verified against the C oracle and cross-checked
 * against this runtime's own output for the same inputs with no scanner attached.
 * See {@code ai-dev/design/nop-treesitter/02-error-recovery-termination.md}.
 */
class BashGlrNoProgressRecoveryTest {

    private static Language bash() {
        return new TreeSitterBash().language();
    }

    /**
     * The reproducers fixed by porting C's summary hand-off in
     * {@code ts_stack_renumber_version}: recovery converges before the stall
     * guard, and the trees match the C oracle byte for byte.
     */
    @Test
    void spacedAndUnterminatedMalformedExpansionRecover() {
        assertEquals("(program (command (command_name (word))) (ERROR (variable_name) (word)))",
                TSParser.parse(bash(), "echo ${x! y}").toSexpString(false));
        assertEquals("(program (command (command_name (word)) (expansion (variable_name) "
                        + "(MISSING \"}\")) (word)))",
                TSParser.parse(bash(), "echo ${x!yy").toSexpString(false));
    }

    /**
     * The unspaced {@code !y} forms: recovery cannot converge, so the stall
     * fallback consumes the unconsumed remainder into an ERROR node and the parse
     * completes. The trees are a best-effort parse, not the C oracle's — the
     * remainder is marked {@code (UNEXPECTED '!')} where C produces a
     * {@code (word)} — the shape divergence the plan accepts as a non-goal. The
     * contract under test is that nothing throws and the breakage is marked.
     */
    @Test
    void unspacedMalformedExpansionReturnsATreeWithTheBreakageMarked() {
        for (String source : new String[]{"echo ${x!y}", "echo ${x!y}z", "echo ${x!yy}"}) {
            String tree = TSParser.parse(bash(), source).toSexpString(false);
            assertTrue(tree.contains("ERROR"), "<< " + source + " >> must mark the breakage");
        }
    }

    /** Every malformed reproducer produces a tree; none throws. */
    @Test
    void noMalformedReproducerThrows() {
        for (String source : new String[]{"echo ${x!y}", "echo ${x!y}z", "echo ${x! y}",
                "echo ${x!yy", "echo ${x!yy}"}) {
            assertFalse(TSParser.parse(bash(), source).toSexpString(false).isEmpty(),
                    "<< " + source + " >> must still produce a tree");
        }
    }

    @Test
    void wellFormedInputIsUnaffected() {
        String tree = TSParser.parse(bash(), "echo ok").toSexpString(false);
        assertFalse(tree.contains("ERROR") || tree.contains("MISSING"),
                "well-formed input must parse clean, got " + tree);
    }
}
