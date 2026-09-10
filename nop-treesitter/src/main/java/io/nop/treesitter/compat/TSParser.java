package io.nop.treesitter.compat;

import java.nio.charset.StandardCharsets;

/**
 * Migration bridge for the JNI-embedded tree-sitter API: mirrors the
 * {@code org.treesitter.TSParser} surface ({@code setLanguage} /
 * {@code parseString} / {@code delete}) over the pure-Java runtime.
 */
public final class TSParser {

    private io.nop.treesitter.language.Language language;

    public void setLanguage(TreeSitterLanguage language) {
        this.language = language.language();
    }

    public io.nop.treesitter.language.Language getLanguage() {
        return language;
    }

    /**
     * Parses {@code source} (UTF-8). The {@code oldTree} parameter is accepted
     * for signature compatibility; incremental reuse across calls is not
     * performed by this compatibility entry point (use
     * {@code TSParser.parseIncremental} for subtree reuse).
     */
    public TSTree parseString(TSTree oldTree, String source) {
        byte[] bytes = source == null ? new byte[0] : source.getBytes(StandardCharsets.UTF_8);
        io.nop.treesitter.TSTree tree = io.nop.treesitter.TSParser.parse(language, bytes);
        return new TSTree(tree);
    }

    /**
     * Source-compatibility only: no native state to reset.
     */
    public void reset() {
        // no native parser state
    }

    /**
     * Source-compatibility only: no native resources; GC reclaims.
     */
    public void delete() {
        // no native resources; GC reclaims
    }
}
