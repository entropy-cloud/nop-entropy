package io.nop.refactor.core.operation;

import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.refactor.core.NopRefactorException;
import io.nop.refactor.core.symbol.RenameResolution;
import io.nop.refactor.core.symbol.SymbolResolverAdapter;
import io.nop.refactor.core.symbol.SymbolResolverAdapter.SourceFile;
import io.nop.refactor.core.symbol.SymbolResolverAdapter.SymbolTarget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rename input-contract gate (plan 10 adjudication 1/7, evolved from the
 * WI9 skeleton): the request record validates the triple plus the module
 * face, and {@link RenameOperation#check} enforces the first rung's own
 * boundaries — the offset locator form and the target-inside-the-module
 * rule. The resolution semantics themselves are the Java adapter's contract
 * (nop-refactor-java, real-adapter tests) and the runner lifecycle rides
 * the shared framework; core must not depend on the adapter module
 * (dependency-direction gate), so the adapter here is an explicit-failure
 * stub that these contract tests never call into.
 */
public class TestRenameOperationSkeleton {

    private static final String MODULE_PATH = "a/Service.java";
    private static final String MODULE_SOURCE =
            "package a;\n\npublic class Service {\n    void run(int limit) {\n"
                    + "        int doubled = limit * 2;\n    }\n}\n";

    // a test-local adapter stub (Rule 24 explicit-failure form): the input
    // contract tests below never reach the resolution call
    private static final SymbolResolverAdapter RESOLVER = new SymbolResolverAdapter() {
        @Override
        public SymbolResolverAdapter.DeclarationIndex buildIndex(List<SourceFile> files) {
            throw new UnsupportedOperationException(
                    "not needed by the rename input-contract tests");
        }

        @Override
        public SymbolResolverAdapter.Resolution resolveReference(
                SymbolResolverAdapter.DeclarationIndex index, SymbolTarget target) {
            throw new UnsupportedOperationException(
                    "not needed by the rename input-contract tests");
        }

        @Override
        public RenameResolution renameResolution(
                SymbolResolverAdapter.DeclarationIndex index, SymbolTarget target,
                String newName) {
            throw new UnsupportedOperationException(
                    "not needed by the rename input-contract tests");
        }
    };

    private static List<SourceFile> module() {
        return List.of(new SourceFile(MODULE_PATH, MODULE_SOURCE));
    }

    private static LintLanguage javaLanguage() {
        return LanguageRegistry.discoverDefaults().resolve("java");
    }

    private static LintEngine javaEngine() {
        return new LintEngine(LanguageRegistry.discoverDefaults(), LintProfile.STANDARD);
    }

    /** A well-formed first-rung request: offset locator inside the module. */
    private static RenameRequest validRequest() {
        long offset = MODULE_SOURCE.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                - 4; // inside the final closing brace
        return new RenameRequest(SymbolTarget.ofOffset(MODULE_PATH, offset), "freshName",
                RenameScope.MODULE, module(), RESOLVER, javaLanguage(), javaEngine());
    }

    @Test
    void checkAcceptsAWellFormedFirstRungRequest() {
        assertDoesNotThrow(() -> RenameOperation.INSTANCE.check(validRequest()));
    }

    @Test
    void constructionRejectsMalformedInput() {
        // the locator form is fail-closed in SymbolTarget itself: neither form
        assertThrows(NopRefactorException.class,
                () -> new SymbolTarget(null, null, null));
        // both forms at once is just as malformed
        assertThrows(NopRefactorException.class,
                () -> new SymbolTarget("a.Service", MODULE_PATH, 0L));
        // a null or non-identifier new name
        assertThrows(NopRefactorException.class,
                () -> new RenameRequest(SymbolTarget.ofOffset(MODULE_PATH, 40L), null,
                        RenameScope.MODULE, module(), RESOLVER, javaLanguage(),
                        javaEngine()));
        assertThrows(NopRefactorException.class,
                () -> new RenameRequest(SymbolTarget.ofOffset(MODULE_PATH, 40L),
                        "not a name", RenameScope.MODULE, module(), RESOLVER,
                        javaLanguage(), javaEngine()));
        // the v1 domain is module-scoped only
        assertThrows(NopRefactorException.class,
                () -> new RenameRequest(SymbolTarget.ofOffset(MODULE_PATH, 40L),
                        "freshName", null, module(), RESOLVER, javaLanguage(),
                        javaEngine()));
        // the WI10 module face: files, resolver, language and engine are
        // mandatory (plan-bound requests consume all four)
        assertThrows(NullPointerException.class,
                () -> new RenameRequest(SymbolTarget.ofOffset(MODULE_PATH, 40L),
                        "freshName", RenameScope.MODULE, null, RESOLVER, javaLanguage(),
                        javaEngine()));
        assertThrows(NullPointerException.class,
                () -> new RenameRequest(SymbolTarget.ofOffset(MODULE_PATH, 40L),
                        "freshName", RenameScope.MODULE, module(), null, javaLanguage(),
                        javaEngine()));
        assertThrows(NullPointerException.class,
                () -> new RenameRequest(SymbolTarget.ofOffset(MODULE_PATH, 40L),
                        "freshName", RenameScope.MODULE, module(), RESOLVER, null,
                        javaEngine()));
        assertThrows(NullPointerException.class,
                () -> new RenameRequest(SymbolTarget.ofOffset(MODULE_PATH, 40L),
                        "freshName", RenameScope.MODULE, module(), RESOLVER,
                        javaLanguage(), null));
    }

    @Test
    void checkRejectsFqnTargetingAsSecondRung() {
        RenameRequest request = new RenameRequest(SymbolTarget.ofFqn("a.Service"),
                "freshName", RenameScope.MODULE, module(), RESOLVER, javaLanguage(),
                javaEngine());

        NopRefactorException thrown = assertThrows(NopRefactorException.class,
                () -> RenameOperation.INSTANCE.check(request));
        assertTrue(thrown.getMessage().contains("WI11"),
                "the rejection names the rung boundary: " + thrown.getMessage());
    }

    @Test
    void checkRejectsTargetOutsideTheModuleFace() {
        RenameRequest request = new RenameRequest(
                SymbolTarget.ofOffset("other/Missing.java", 3L), "freshName",
                RenameScope.MODULE, module(), RESOLVER, javaLanguage(), javaEngine());

        assertThrows(NopRefactorException.class,
                () -> RenameOperation.INSTANCE.check(request));
    }
}
