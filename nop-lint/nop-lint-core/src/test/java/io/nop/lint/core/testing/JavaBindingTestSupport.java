package io.nop.lint.core.testing;

import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.treesitter.language.Language;

/**
 * Test-scope support: a {@code LintLanguage} binding over the real Java
 * grammar under the conventional id {@code java} (the same emulation
 * TestLintEngine uses, because the real binding lives in nop-lint-java which
 * cannot be on this module's classpath). Rule test fixtures declare
 * {@code language: Java}; the registry's case-insensitive resolution binds
 * them to this binding.
 */
public final class JavaBindingTestSupport {

    private JavaBindingTestSupport() {
    }

    public static LintLanguage javaBinding() {
        return new TreeSitterLanguageAdapter("java",
                Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);
    }

    public static LanguageRegistry registryWithJava() {
        LanguageRegistry registry = LanguageRegistry.empty();
        registry.register(javaBinding());
        return registry;
    }

    /**
     * A binding over the Java grammar under an arbitrary id — tests use it
     * to make rule groups bound under synthetic languages without the real
     * language module.
     */
    public static LintLanguage stubBinding(String id) {
        return new TreeSitterLanguageAdapter(id,
                Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);
    }
}
