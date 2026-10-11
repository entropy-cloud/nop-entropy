package io.nop.lint.core.lang;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.node.LintTree;
import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.parser.glr.ParserOptions;
import io.nop.treesitter.parser.incremental.IncrementalStats;
import io.nop.treesitter.parser.incremental.TSInputEdit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Tree-sitter backed {@link LintLanguage}: shared implementation for the
 * language bindings. Parses through the backend's public parse entry,
 * optionally preprocesses pattern text through a caller-supplied expando
 * function, and resolves kind names against a name-to-id map built once from
 * the backend's full symbol name table.
 *
 * <p>The map deliberately avoids the backend's {@code symbolId} lookup, which
 * scans only the non-alias symbol range: aliased kinds such as Java's
 * {@code type_identifier} live beyond that range and would silently resolve
 * to -1. When visible symbols share a name (e.g. Java's {@code throws}
 * keyword token vs the named {@code throws} clause node), the named symbol's
 * id wins — the semantics pattern kind matching expects.</p>
 */
public final class TreeSitterLanguageAdapter implements LintLanguage {

    private final String id;
    private final Language language;
    private final UnaryOperator<String> expando;
    private final Map<String, Integer> kindIds;

    /**
     * Creates an adapter; a null expando function means identity preprocessing.
     */
    public TreeSitterLanguageAdapter(String id, Language language, UnaryOperator<String> expando) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.language = Objects.requireNonNull(language, "language must not be null");
        this.expando = expando != null ? expando : UnaryOperator.identity();
        this.kindIds = buildKindIds(language);
    }

    /**
     * Scans the full symbol name table (non-alias plus alias range) and keeps
     * visible entries; on a same-name collision the named symbol's id wins.
     */
    private static Map<String, Integer> buildKindIds(Language language) {
        int total = language.symbolCount() + language.aliasCount();
        Map<String, Integer> ids = new HashMap<>(total * 2);
        for (int i = 1; i < total; i++) {
            if (!language.symbolVisible(i)) {
                continue;
            }
            String name = language.symbolName(i);
            if (name == null) {
                continue;
            }
            Integer existing = ids.get(name);
            if (existing == null || (!language.symbolNamed(existing) && language.symbolNamed(i))) {
                ids.put(name, i);
            }
        }
        return Map.copyOf(ids);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Language treeSitter() {
        return language;
    }

    @Override
    public LintTree parse(String source) {
        return LintTree.of(TSParser.parse(language, source));
    }

    @Override
    public LintTree parse(byte[] source) {
        return LintTree.of(TSParser.parse(language, source));
    }

    @Override
    public LintTree parseIncremental(LintTree oldTree, byte[] newSource) {
        return parseIncremental(oldTree, newSource, null);
    }

    /**
     * {@link #parseIncremental(LintTree, byte[])} with an optional
     * {@link IncrementalStats} out-parameter: the reuse counters prove at
     * runtime that the diff-produced edit sequence was actually consumed by
     * the backend's incremental entry (wiring evidence, design 03 §1.2).
     */
    public LintTree parseIncremental(LintTree oldTree, byte[] newSource, IncrementalStats stats) {
        if (newSource == null) {
            throw new NopLintException("newSource must not be null");
        }
        if (oldTree == null) {
            // Documented cold-start branch: no old tree, full parse, no reuse.
            return LintTree.of(TSParser.parse(language, newSource));
        }
        TSTree previous = oldTree.tree();
        if (previous.language() != language) {
            throw new NopLintException("oldTree was parsed by a different language binding instance, id="
                    + id);
        }
        try {
            List<TSInputEdit> edits = EditCalculator.diff(previous.source(), newSource);
            return LintTree.of(TSParser.parseIncremental(language, previous, edits, newSource,
                    ParserOptions.DEFAULT, stats));
        } catch (TreeSitterException e) {
            throw new NopLintException("incremental parse failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String preprocessPattern(String patternText) {
        return expando.apply(patternText);
    }

    @Override
    public int kindId(String kindName) {
        Integer id = kindIds.get(kindName);
        return id != null ? id : -1;
    }
}
