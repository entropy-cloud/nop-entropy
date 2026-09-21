package io.nop.lint.core.lang;

import io.nop.lint.core.node.LintTree;
import io.nop.treesitter.language.Language;

/**
 * Test-only ServiceLoader provider under the real binding id {@code java}:
 * it lets nop-lint-core's CLI end-to-end tests run the production
 * {@code LanguageRegistry.discoverDefaults()} path against rules that
 * declare {@code language: Java} — the xdef restricts the rule language
 * enum to Java|TypeScript|TSX|XML, so the {@code stub}-id provider cannot
 * bind them. The real Java binding lives in nop-lint-java, which cannot be
 * on this module's classpath (dependency direction java → core). Public
 * with a no-arg constructor, as the ServiceLoader provider contract
 * requires.
 */
public final class TestJavaIdLanguage implements LintLanguage {

    private final TreeSitterLanguageAdapter adapter = new TreeSitterLanguageAdapter("java",
            Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);

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
