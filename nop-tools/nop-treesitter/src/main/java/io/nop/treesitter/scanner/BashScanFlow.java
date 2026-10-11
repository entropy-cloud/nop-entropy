package io.nop.treesitter.scanner;

/**
 * Three-way outcome of one {@code scanner.c} region, used instead of a bare
 * {@code boolean} so that the C control flow can be reproduced exactly.
 *
 * <p>{@code scanner.c} is one giant {@code scan()} built from {@code goto}
 * labels. Three different continuations exist inside it and a plain
 * {@code boolean} cannot express them:</p>
 *
 * <ul>
 *   <li>{@link #MATCHED} — {@code return true}</li>
 *   <li>{@link #NO_MATCH} — {@code return false} (aborts the whole scan; does
 *       <em>not</em> fall through to the next region)</li>
 *   <li>{@link #CONTINUE} — the region's guard did not fire; control falls
 *       through to the next region (which for {@code regex} /
 *       {@code extglob_pattern} / {@code expansion_word} / {@code brace_start}
 *       means the next label in file order)</li>
 * </ul>
 */
final class BashScanFlow {

    /** The region's outer {@code if} guard did not hold: fall through. */
    static final int CONTINUE = 0;

    /** The region recognized a token: {@code return true}. */
    static final int MATCHED = 1;

    /** The region rejected: {@code return false}. */
    static final int NO_MATCH = 2;

    /**
     * A {@code goto} target rather than a scan outcome: the region asking for it
     * hands control back to the scanner, which runs the named label and every
     * label after it (the label chain mutates cross-call state, so it can only
     * run in the class that owns the state).
     */
    static final int JUMP_REGEX = 3;

    static final int JUMP_EXTGLOB = 4;

    static final int JUMP_EXPANSION_WORD = 5;

    static final int JUMP_BRACE_START = 6;

    private BashScanFlow() {
    }

    /** {@code true} when the scan must stop with this outcome. */
    static boolean isTerminal(int flow) {
        return flow != CONTINUE;
    }

    /** Converts a terminal flow outcome to the {@code boolean} scan result. */
    static boolean result(int flow) {
        return flow == MATCHED;
    }
}
