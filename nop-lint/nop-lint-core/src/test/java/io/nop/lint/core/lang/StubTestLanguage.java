package io.nop.lint.core.lang;

import io.nop.lint.core.node.LintTree;
import io.nop.treesitter.language.Language;

/**
 * Test-only ServiceLoader provider: a fully functional binding over the Java
 * grammar blob under the synthetic id {@code stub}. It lets nop-lint-core
 * tests prove discovery end to end without depending on nop-lint-java (the
 * dependency direction is java → core, so the real Java binding cannot be on
 * this module's classpath). Public with a no-arg constructor, as the
 * ServiceLoader provider contract requires.
 */
public final class StubTestLanguage implements LintLanguage {

    private final TreeSitterLanguageAdapter adapter = new TreeSitterLanguageAdapter("stub",
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
    public String preprocessPattern(String patternText) {
        return adapter.preprocessPattern(patternText);
    }

    @Override
    public int kindId(String kindName) {
        return adapter.kindId(kindName);
    }
}
