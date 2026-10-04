package io.nop.treesitter.shell;

import io.nop.treesitter.compat.TreeSitterBash;
import io.nop.treesitter.language.Language;

/** Shared fixtures for the shell analysis tests. */
final class ShellTestSupport {

    static final Language BASH = new TreeSitterBash().language();

    private ShellTestSupport() {
    }

    static ShellAnalysis analysis() {
        return new ShellAnalysis(BASH);
    }

    /** Asserts a script is provable and returns the proven sequence. */
    static ProvenCommandSequence proven(ShellAnalysis analysis, String script) {
        ProvenSplit split = analysis.splitProven(script);
        if (split instanceof ProvenCommandSequence sequence) {
            return sequence;
        }
        throw new AssertionError("expected a proven split for <<" + script + ">>, got " + split);
    }

    /** Asserts a script is refused and returns the refusal. */
    static UnprovenScript unproven(ShellAnalysis analysis, String script) {
        ProvenSplit split = analysis.splitProven(script);
        if (split instanceof UnprovenScript unproven) {
            return unproven;
        }
        throw new AssertionError("expected a refusal for <<" + script + ">>, got " + split);
    }
}
