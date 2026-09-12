package io.nop.treesitter;

import io.nop.treesitter.language.Language;
import io.nop.treesitter.parser.glr.GLRParser;
import io.nop.treesitter.parser.glr.ParserOptions;
import io.nop.treesitter.parser.incremental.ChangedRanges;
import io.nop.treesitter.parser.incremental.IncrementalStats;
import io.nop.treesitter.parser.incremental.ReuseCursor;
import io.nop.treesitter.parser.incremental.TSInputEdit;
import io.nop.treesitter.parser.incremental.TSRange;
import io.nop.treesitter.subtree.SubtreeArena;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Public parse entry point: {@code Language → Lexer → Parser → TSTree} in a
 * single path. No component may be bypassed by callers — the language is loaded
 * from its blob, the bytes are scanned by the language's lexer, the GLR driver
 * (graph-structured stack, C-runtime semantics) builds the tree on a fresh
 * arena, and the result is snapshotted into an immutable {@link TSTree}.
 *
 * <p>{@link #parseIncremental} drives the same path over an edited source,
 * offering the previous tree's unchanged leaves to the parser for reuse (C
 * {@code ts_parser__reuse_node}); the produced tree is byte-identical to a
 * fresh full parse — the invariant the reuse machinery must never break.</p>
 */
public final class TSParser {

    private TSParser() {
    }

    /**
     * Parses a UTF-8 source string into an immutable tree.
     */
    public static TSTree parse(Language language, String source) {
        return parse(language, source, ParserOptions.DEFAULT);
    }

    /**
     * Parses a UTF-8 source string into an immutable tree with the given options.
     */
    public static TSTree parse(Language language, String source, ParserOptions options) {
        return parse(language, source.getBytes(StandardCharsets.UTF_8), options);
    }

    /**
     * Parses raw source bytes into an immutable tree.
     */
    public static TSTree parse(Language language, byte[] source) {
        return parse(language, source, ParserOptions.DEFAULT);
    }

    /**
     * Parses raw source bytes into an immutable tree with the given options.
     */
    public static TSTree parse(Language language, byte[] source, ParserOptions options) {
        SubtreeArena arena = new SubtreeArena(expectedNodes(language, source));
        int rootId = GLRParser.parse(language, arena, source, options);
        language.noteNodeCount(arena.size(), source.length);
        // The parse arena is freshly created and the parser instance is dropped
        // on return — ownership transfers to the tree without a deep copy.
        return TSTree.adopt(language, arena, rootId, source);
    }

    /**
     * Arena pre-reservation estimate for {@code source}: the language's
     * observed nodes-per-byte ratio (see {@link Language#noteNodeCount}) times
     * the source length with a 1.25x margin, capped at 2^21 slots (≈140 MB of
     * columns) so a dense-input outlier cannot spike the heap. Before the
     * first observation the arena starts at its small initial capacity — the
     * first parse learns the ratio and eats the geometric-growth cost once;
     * worst-case waste for a reserved-but-unused slot is ≈66 bytes.
     */
    private static int expectedNodes(Language language, byte[] source) {
        float observed = language.observedNodesPerByte();
        if (observed <= 0) {
            return 0;
        }
        long estimate = (long) (source.length * observed * 1.25f) + 64;
        return (int) Math.min(estimate, 1 << 21);
    }

    /**
     * Reparses an edited source, reusing unchanged leaves of {@code oldTree}.
     * The old tree is not mutated and stays usable for further incremental
     * parses. The result is byte-identical to {@code parse(language,
     * newSource)}.
     *
     * @param oldTree   a tree produced by this API from the old source with the
     *                  same {@code language} instance
     * @param edits     the edits that transformed the old source into
     *                  {@code newSource}; must be pairwise non-overlapping in
     *                  old-source coordinates
     * @param newSource the edited UTF-8 source bytes
     */
    public static TSTree parseIncremental(Language language, TSTree oldTree,
                                          List<TSInputEdit> edits, byte[] newSource) {
        return parseIncremental(language, oldTree, edits, newSource, ParserOptions.DEFAULT, null);
    }

    /**
     * {@link #parseIncremental(Language, TSTree, List, byte[])} with explicit
     * parser options and an optional {@link IncrementalStats} out-parameter
     * recording how many leaves were reused and how many tokens were lexed.
     */
    public static TSTree parseIncremental(Language language, TSTree oldTree,
                                          List<TSInputEdit> edits, byte[] newSource,
                                          ParserOptions options, IncrementalStats stats) {
        if (language == null) {
            throw new TreeSitterException("language must not be null");
        }
        if (oldTree == null) {
            throw new TreeSitterException("oldTree must not be null");
        }
        if (edits == null) {
            throw new TreeSitterException("edits must not be null");
        }
        if (newSource == null) {
            throw new TreeSitterException("newSource must not be null");
        }
        if (options == null) {
            throw new TreeSitterException("options must not be null");
        }
        if (oldTree.language() != language) {
            throw new TreeSitterException("oldTree was parsed by a different language instance");
        }
        List<TSInputEdit> sorted = new ArrayList<>(edits);
        int oldLength = oldTree.source().length;
        for (TSInputEdit edit : sorted) {
            if (edit == null) {
                throw new TreeSitterException("edits must not contain null");
            }
            if (edit.oldEndByte() > oldLength) {
                throw new TreeSitterException("edit old range [" + edit.startByte() + "," + edit.oldEndByte()
                        + ") out of old source bounds [0," + oldLength + ")");
            }
            if (edit.newEndByte() > newSource.length) {
                throw new TreeSitterException("edit new range [" + edit.startByte() + "," + edit.newEndByte()
                        + ") out of new source bounds [0," + newSource.length + ")");
            }
        }
        sorted.sort(Comparator.comparingInt(TSInputEdit::startByte));
        for (int i = 1; i < sorted.size(); i++) {
            TSInputEdit prev = sorted.get(i - 1);
            TSInputEdit curr = sorted.get(i);
            if (curr.startByte() < prev.oldEndByte()) {
                throw new TreeSitterException("overlapping edits: [" + prev.startByte() + "," + prev.oldEndByte()
                        + ") and [" + curr.startByte() + "," + curr.oldEndByte() + ")");
            }
        }

        SubtreeArena arena = new SubtreeArena(expectedNodes(language, newSource));
        ReuseCursor reuse = new ReuseCursor(oldTree, sorted);
        int rootId = GLRParser.parse(language, arena, newSource, options, reuse, stats);
        language.noteNodeCount(arena.size(), newSource.length);
        return TSTree.snapshot(language, arena, rootId, newSource);
    }

    /**
     * The ranges whose tree content differs between two trees of the same
     * language — C {@code ts_tree_get_changed_ranges}. Both trees are
     * immutable snapshots (typically the output of {@link #parse} and
     * {@link #parseIncremental}); identical trees yield an empty list.
     */
    public static List<TSRange> getChangedRanges(TSTree oldTree, TSTree newTree) {
        return ChangedRanges.getChangedRanges(oldTree, newTree);
    }
}
