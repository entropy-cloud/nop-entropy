package io.nop.lint.js;

import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintTree;
import io.nop.treesitter.language.Language;

/**
 * The TSX language binding: parses through the tree-sitter-tsx grammar (the
 * TypeScript grammar extended with JSX element syntax) and applies identity
 * pattern preprocessing — the same {@code $}-identifier argument as
 * {@link TypeScriptLanguage}, asserted by this module's tests for both
 * grammars.
 *
 * <p>No annotation-carried suppression extractor exists for TSX in the v1
 * scope, so {@link #suppressionProvider()} keeps the contract default
 * ({@code null}) and linting runs the pure inline-comment suppression path.</p>
 */
public final class TsxLanguage implements LintLanguage {

    private static final String BLOB = "/grammars/tsx/tree-sitter-tsx-blob.bin";

    /**
     * The one adapter per JVM: instances share it, so repeated construction
     * (ServiceLoader discovery included) never re-decodes the grammar blob.
     */
    private static final TreeSitterLanguageAdapter SHARED =
            new TreeSitterLanguageAdapter("tsx", loadLanguage(), null);

    private static final TsxLanguage INSTANCE = new TsxLanguage();

    private final TreeSitterLanguageAdapter adapter;

    /**
     * Public no-arg constructor: the JDK ServiceLoader discovers classpath
     * providers only through one. Every instance delegates to
     * {@link #SHARED}, so a discovered instance is functionally identical to
     * {@link #get()}.
     */
    public TsxLanguage() {
        this.adapter = SHARED;
    }

    private static Language loadLanguage() {
        return Language.fromClasspath(BLOB);
    }

    /**
     * The canonical shared instance; the grammar blob is decoded once per
     * JVM regardless of how many instances exist.
     */
    public static TsxLanguage get() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return adapter.id();
    }

    @Override
    public Language treeSitter() {
        return adapter.treeSitter();
    }

    @Override
    public LintTree parse(String source) {
        return adapter.parse(source);
    }

    @Override
    public LintTree parse(byte[] source) {
        return adapter.parse(source);
    }

    @Override
    public LintTree parseIncremental(LintTree oldTree, byte[] newSource) {
        return adapter.parseIncremental(oldTree, newSource);
    }

    @Override
    public String preprocessPattern(String patternText) {
        return adapter.preprocessPattern(patternText);
    }

    @Override
    public int kindId(String kindName) {
        return adapter.kindId(kindName);
    }
}
