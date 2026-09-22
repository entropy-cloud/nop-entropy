package io.nop.lint.js;

import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintTree;
import io.nop.treesitter.language.Language;

/**
 * The TypeScript language binding: parses through the tree-sitter-typescript
 * grammar and applies identity pattern preprocessing.
 *
 * <p>Identity is correct here because {@code $} is a legal ECMAScript
 * identifier part (ECMA-262 {@code IdentifierPartChar}), so meta-variables
 * such as {@code $VAR}, {@code $_X}, and even {@code $$$ARGS} already lex as
 * single identifiers — the binding tests assert this end to end for both
 * grammars this module ships.</p>
 *
 * <p>No annotation-carried suppression extractor exists for TypeScript in
 * the v1 scope, so {@link #suppressionProvider()} keeps the contract default
 * ({@code null}) and linting runs the pure inline-comment suppression path.</p>
 */
public final class TypeScriptLanguage implements LintLanguage {

    private static final String BLOB = "/grammars/typescript/tree-sitter-typescript-blob.bin";

    /**
     * The one adapter per JVM: instances share it, so repeated construction
     * (ServiceLoader discovery included) never re-decodes the grammar blob.
     */
    private static final TreeSitterLanguageAdapter SHARED =
            new TreeSitterLanguageAdapter("typescript", loadLanguage(), null);

    private static final TypeScriptLanguage INSTANCE = new TypeScriptLanguage();

    private final TreeSitterLanguageAdapter adapter;

    /**
     * Public no-arg constructor: the JDK ServiceLoader discovers classpath
     * providers only through one. Every instance delegates to
     * {@link #SHARED}, so a discovered instance is functionally identical to
     * {@link #get()}.
     */
    public TypeScriptLanguage() {
        this.adapter = SHARED;
    }

    private static Language loadLanguage() {
        return Language.fromClasspath(BLOB);
    }

    /**
     * The canonical shared instance; the grammar blob is decoded once per
     * JVM regardless of how many instances exist.
     */
    public static TypeScriptLanguage get() {
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
