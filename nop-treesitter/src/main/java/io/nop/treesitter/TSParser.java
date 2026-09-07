package io.nop.treesitter;

import io.nop.treesitter.language.Language;
import io.nop.treesitter.parser.Parser;
import io.nop.treesitter.subtree.SubtreeArena;

import java.nio.charset.StandardCharsets;

/**
 * Public parse entry point: {@code Language → Lexer → Parser → TSTree} in a
 * single path. No component may be bypassed by callers — the language is loaded
 * from its blob, the bytes are scanned by the language's lexer, the LR(1)
 * driver builds the tree on a fresh arena, and the result is snapshotted into
 * an immutable {@link TSTree}.
 */
public final class TSParser {

    private TSParser() {
    }

    /**
     * Parses a UTF-8 source string into an immutable tree.
     */
    public static TSTree parse(Language language, String source) {
        return parse(language, source.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Parses raw source bytes into an immutable tree.
     */
    public static TSTree parse(Language language, byte[] source) {
        SubtreeArena arena = new SubtreeArena();
        int rootId = Parser.parse(language, arena, source);
        return TSTree.snapshot(language, arena, rootId);
    }
}