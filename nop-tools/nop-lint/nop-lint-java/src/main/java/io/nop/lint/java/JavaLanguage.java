package io.nop.lint.java;

import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.suppress.SuppressionProvider;
import io.nop.treesitter.language.Language;

/**
 * The Java language binding: parses through the tree-sitter-java grammar and
 * applies identity pattern preprocessing.
 *
 * <p>Identity is correct here because {@code $} is a legal Java identifier
 * part (JLS &sect;3.8 "Java letter"), so meta-variables such as {@code $VAR}
 * and {@code $$$ARGS} already parse as identifiers — the grammar's lexer
 * keeps them intact, which the binding tests assert end to end.</p>
 */
public final class JavaLanguage implements LintLanguage {

    private static final String BLOB = "/grammars/java/tree-sitter-java-blob.bin";

    /**
     * The one adapter per JVM: instances share it, so repeated construction
     * (ServiceLoader discovery included) never re-decodes the grammar blob.
     */
    private static final TreeSitterLanguageAdapter SHARED =
            new TreeSitterLanguageAdapter("java", loadLanguage(), null);

    private static final JavaLanguage INSTANCE = new JavaLanguage();

    private final TreeSitterLanguageAdapter adapter;

    /**
     * Public no-arg constructor: the JDK ServiceLoader discovers classpath
     * providers only through one (the static {@code provider()} factory
     * path applies to named-module providers exclusively). Every instance
     * delegates to {@link #SHARED}, so a discovered instance is
     * functionally identical to {@link #get()}.
     */
    public JavaLanguage() {
        this.adapter = SHARED;
    }

    private static Language loadLanguage() {
        return Language.fromClasspath(BLOB);
    }

    /**
     * The canonical shared instance; the grammar blob is decoded once per
     * JVM regardless of how many instances exist.
     */
    public static JavaLanguage get() {
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

    @Override
    public SuppressionProvider suppressionProvider() {
        return JavaSuppressWarningsProvider.get();
    }
}
