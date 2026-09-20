package io.nop.lint.java;

import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintTree;
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

    private static final JavaLanguage INSTANCE = new JavaLanguage();

    private final TreeSitterLanguageAdapter adapter;

    private JavaLanguage() {
        this.adapter = new TreeSitterLanguageAdapter("java", loadLanguage(), null);
    }

    private static Language loadLanguage() {
        return Language.fromClasspath(BLOB);
    }

    /**
     * The shared binding instance; the grammar blob is decoded once per JVM.
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
    public String preprocessPattern(String patternText) {
        return adapter.preprocessPattern(patternText);
    }

    @Override
    public int kindId(String kindName) {
        return adapter.kindId(kindName);
    }
}
