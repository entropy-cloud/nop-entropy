package io.nop.lint.core.bench;

import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.treesitter.language.Language;

/**
 * Bench-local language holder. The Java binding proper lives in nop-lint-java
 * (which depends on this module, not the reverse), so benchmarks construct
 * the same adapter directly.
 */
final class BenchLanguage {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);

    private BenchLanguage() {
    }

    static LintLanguage get() {
        return JAVA;
    }
}
