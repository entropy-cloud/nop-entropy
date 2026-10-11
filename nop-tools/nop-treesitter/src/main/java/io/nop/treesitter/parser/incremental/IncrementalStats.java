package io.nop.treesitter.parser.incremental;

/**
 * Test-facing counters for one {@code parseIncremental} run, proving that
 * reuse actually happened instead of being assumed.
 *
 * <p>{@code reusedSubtrees} counts old-tree leaves pushed onto the parse stack
 * without re-lexing; {@code lexedTokens} counts lexer invocations (including
 * the end-of-input token, which is never reused).</p>
 */
public final class IncrementalStats {

    private int reusedSubtrees;
    private int lexedTokens;

    public int reusedSubtrees() {
        return reusedSubtrees;
    }

    public int lexedTokens() {
        return lexedTokens;
    }

    public void recordReuse() {
        reusedSubtrees++;
    }

    public void recordLex() {
        lexedTokens++;
    }

    @Override
    public String toString() {
        return "IncrementalStats{reusedSubtrees=" + reusedSubtrees + ", lexedTokens=" + lexedTokens + "}";
    }
}
