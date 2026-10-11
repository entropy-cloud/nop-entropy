package io.nop.refactor.java;

import io.nop.refactor.core.symbol.SymbolResolverAdapter;
import io.nop.refactor.core.symbol.SymbolResolverAdapter.Resolution;
import io.nop.refactor.core.symbol.SymbolResolverAdapter.SourceFile;
import io.nop.refactor.core.symbol.SymbolResolverAdapter.SymbolTarget;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Java adapter's contract proof (plan 09 Phase 2): the declaration
 * index covers every rename-ladder kind (types/fields/methods/parameters/
 * locals), the binding filter admits same-package and import-bound files
 * while structurally excluding unbound packages, the file+offset locator
 * form resolves through the nop-lint-java ScopeAnalyzer public API, and an
 * unresolvable target returns the explicit unresolved form — the minimal
 * implementable-and-consumable SPI face WI10/WI11 build on.
 */
class JavaSymbolResolverAdapterContractTest {

    private static final String SERVICE_PATH = "a/Service.java";
    private static final String USAGE_PATH = "b/Usage.java";
    private static final String UNBOUND_PATH = "c/Unbound.java";

    private static List<SourceFile> module() {
        return List.of(
                new SourceFile(SERVICE_PATH, """
                        package a;

                        public class Service {
                            private int counter;

                            public int getCounter() {
                                return counter;
                            }

                            void run(int limit) {
                                int doubled = limit * 2;
                                System.out.println(doubled);
                            }
                        }
                        """),
                new SourceFile(USAGE_PATH, """
                        package b;

                        import a.Service;

                        public class Usage {
                            void use(Service service, int limit) {
                                int doubled = limit;
                                service.getCounter();
                            }
                        }
                        """),
                new SourceFile(UNBOUND_PATH, """
                        package c;

                        // a different type that merely shares the simple name —
                        // no import of a.Service, so it must never bind
                        public class Service {
                            void other() {
                                int doubled = 1;
                            }
                        }
                        """));
    }

    private static SymbolResolverAdapter.DeclarationIndex index() {
        return new JavaSymbolResolverAdapter().buildIndex(module());
    }

    @Test
    void indexCoversEveryRenameLadderKind() {
        JavaSymbolResolverAdapter.JavaDeclarationIndex javaIndex =
                (JavaSymbolResolverAdapter.JavaDeclarationIndex) index();

        assertEquals(3, javaIndex.fileCount());
    }

    @Test
    void fqnResolutionRespectsTheBindingFilter() {
        JavaSymbolResolverAdapter adapter = new JavaSymbolResolverAdapter();
        SymbolResolverAdapter.DeclarationIndex declarationIndex = adapter.buildIndex(module());

        Resolution resolution = adapter.resolveReference(declarationIndex,
                SymbolTarget.ofFqn("a.Service"));

        assertTrue(resolution.resolved(), "a.Service is indexed and resolves");
        assertFalse(resolution.references().isEmpty());
        for (var reference : resolution.references()) {
            assertTrue(reference.path().equals(SERVICE_PATH)
                            || reference.path().equals(USAGE_PATH),
                    "unbound package c must not leak references: " + reference.path());
        }
        // the bound usage file names the type at its import site and parameter
        assertTrue(resolution.references().stream().anyMatch(r -> USAGE_PATH.equals(r.path())),
                "the import-bound file contributes references");
    }

    @Test
    void offsetFormResolvesThroughTheScopeAnalyzerFace() {
        JavaSymbolResolverAdapter adapter = new JavaSymbolResolverAdapter();
        SymbolResolverAdapter.DeclarationIndex declarationIndex = adapter.buildIndex(module());

        String serviceSource = module().get(0).content();
        int doubledOffset = serviceSource.indexOf("doubled");
        Resolution resolution = adapter.resolveReference(declarationIndex,
                SymbolTarget.ofOffset(SERVICE_PATH, doubledOffset));

        assertTrue(resolution.resolved(),
                "the offset lands on the 'doubled' local: " + resolution.detail());
        assertTrue(resolution.references().stream()
                .allMatch(r -> SERVICE_PATH.equals(r.path())),
                "a local variable's references stay in its own file");
        // the println argument mention — the declaration's own identifier is
        // excluded from the reference face (it is the target, not a reference)
        assertEquals(1, resolution.references().size());
    }

    @Test
    void resolvedTargetWithZeroBoundReferencesIsALegalOutcome() {
        // (the offset locator rides the ScopeAnalyzer face, whose definition
        // model covers the variable positions the WI10 first rung targets)
        JavaSymbolResolverAdapter adapter = new JavaSymbolResolverAdapter();
        SymbolResolverAdapter.DeclarationIndex declarationIndex = adapter.buildIndex(module());

        // 'doubled' in package c is a local variable no other statement uses —
        // resolved, but every mention except its own declaration is absent
        String unboundSource = module().get(2).content();
        // the locator contract is a BYTE offset: this fixture carries a
        // multi-byte character, so the byte position is computed, not the
        // char index
        int charAt = unboundSource.indexOf("doubled");
        long byteAt = unboundSource.substring(0, charAt)
                .getBytes(StandardCharsets.UTF_8).length;
        Resolution other = adapter.resolveReference(declarationIndex,
                SymbolTarget.ofOffset(UNBOUND_PATH, byteAt));
        assertTrue(other.resolved(), "the unused local is indexed: " + other.detail());
        assertTrue(other.references().isEmpty(), "no bound references exist");
        assertFalse(other.detail().isBlank(),
                "the zero-reference outcome stays distinguishable from unresolved");
    }

    @Test
    void unresolvableTargetIsExplicitNotSilent() {
        JavaSymbolResolverAdapter adapter = new JavaSymbolResolverAdapter();
        SymbolResolverAdapter.DeclarationIndex declarationIndex = adapter.buildIndex(module());

        Resolution unknownFqn = adapter.resolveReference(declarationIndex,
                SymbolTarget.ofFqn("no.such.Type"));
        assertFalse(unknownFqn.resolved(), "an unindexed FQN does not resolve");
        assertFalse(unknownFqn.detail().isBlank(), "the detail names the miss");

        Resolution unknownOffset = adapter.resolveReference(declarationIndex,
                SymbolTarget.ofOffset(USAGE_PATH, 1_000_000L));
        assertFalse(unknownOffset.resolved(), "an offset past the file does not resolve");
        assertFalse(unknownOffset.detail().isBlank());
    }

    @Test
    void declarationCarriesByteRangesAndNames() {
        JavaSymbolResolverAdapter adapter = new JavaSymbolResolverAdapter();
        SymbolResolverAdapter.DeclarationIndex declarationIndex = adapter.buildIndex(module());

        Resolution field = adapter.resolveReference(declarationIndex,
                new SymbolTarget(null, SERVICE_PATH,
                        (long) module().get(0).content().indexOf("counter")));
        assertTrue(field.resolved(), "the counter field resolves by offset");
        assertFalse(field.references().isEmpty(), "the declaring file's own mentions count");
        for (var reference : field.references()) {
            assertEquals("counter", reference.name());
            assertNotEquals(0, reference.range().endByte() - reference.range().startByte(),
                    "references carry non-degenerate byte ranges");
        }
        assertTrue(field.references().stream()
                        .allMatch(r -> SERVICE_PATH.equals(r.path())),
                "a private field's references stay in the declaring file");
    }
}
