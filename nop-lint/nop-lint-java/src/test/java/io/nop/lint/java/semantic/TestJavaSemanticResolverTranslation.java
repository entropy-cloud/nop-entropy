package io.nop.lint.java.semantic;

import io.nop.lint.core.NopLintException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The L4 resolver's failure translation (plan 12 Phase 1, audit finding C9):
 * solver failures (UnsolvedSymbolException and friends) must surface as the
 * module exception with file and position attached — never a bare javaparser
 * exception crossing the resolver boundary. The L2 resolver
 * (JavaTypeResolver.tryResolve) has always honored this contract.
 *
 * <p>Coverage note: the extends-chain scenario is the one live trigger (the
 * ancestor walk resolves the superclass); the logger entry's analyzer
 * returns false for unresolvable receivers without resolving, so it cannot
 * throw in practice — all three entries share the same {@code translate}
 * guard.</p>
 */
class TestJavaSemanticResolverTranslation {

    @TempDir
    Path dir;

    /**
     * The superclass is project-local: {@code implementsInterface} walks the
     * ancestor chain and {@code erasedQualifiedName} fails to resolve it —
     * the audit C9 trigger.
     */
    private JavaSemanticResolver newExtendsResolver() throws Exception {
        Path file = dir.resolve("src/demo/Handler.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                package demo;

                class Handler extends ProjectBase {
                    void run() {
                    }
                }
                """, StandardCharsets.UTF_8);
        return new JavaSemanticResolver();
    }

    @Test
    void superclassChainResolutionFailureTranslates() throws Exception {
        JavaSemanticResolver resolver = newExtendsResolver();
        // Handler at 0-based line 2; the ancestor walk crosses ProjectBase,
        // which the reflection-only solver cannot resolve
        NopLintException ex = assertThrows(NopLintException.class,
                () -> resolver.implementsInterface(dir.resolve("src/demo/Handler.java").toString(), 2, 8, "java.lang.Runnable"));
        assertTrue(ex.getMessage().contains("semantic query failed"), ex.getMessage());
        assertTrue(ex.getMessage().contains("src/demo/Handler.java"), ex.getMessage());
    }

}
