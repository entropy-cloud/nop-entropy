package io.nop.refactor.java;

import io.nop.refactor.core.symbol.RenameResolution;
import io.nop.refactor.core.symbol.SymbolKind;
import io.nop.refactor.core.symbol.SymbolResolverAdapter;
import io.nop.refactor.core.symbol.SymbolResolverAdapter.SourceFile;
import io.nop.refactor.core.symbol.SymbolResolverAdapter.SymbolTarget;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The WI10 first-rung rename resolution contract (plan 10 Phase 1): the
 * four states are explicit and mutually exclusive, the occurrence set is the
 * bound NameExpr face only (shadowed siblings, method calls, field accesses
 * and type names never enter it — the negative-rewrite fixtures), the
 * conflict domain is the enclosing method's locals/parameters plus the
 * file's fields plus the self-rename case, and the symbol-intact assertion
 * rides every RESOLVED outcome.
 */
class JavaSymbolResolverRenameContractTest {

    private static final JavaSymbolResolverAdapter ADAPTER = new JavaSymbolResolverAdapter();

    private static final String SERVICE_PATH = "a/Service.java";

    private static List<SourceFile> shadowModule() {
        return List.of(new SourceFile(SERVICE_PATH, """
                package a;

                public class Service {
                    private int limit;

                    void run(int limit) {
                        int doubled = limit * 2;
                        if (doubled > 0) {
                            int doubled2 = doubled;
                        }
                        System.out.println(doubled);
                        this.limit = doubled;
                    }

                    void call() {
                        call();
                        Service service = new Service();
                        service.run(1);
                    }
                }
                """));
    }

    private static SymbolResolverAdapter.DeclarationIndex shadowIndex() {
        return ADAPTER.buildIndex(shadowModule());
    }

    private static long byteAt(String content, String marker) {
        return content.substring(0, content.indexOf(marker))
                .getBytes(StandardCharsets.UTF_8).length;
    }

    @Test
    void parameterRenameCollectsOnlyBoundOccurrences() {
        SymbolResolverAdapter.DeclarationIndex index = shadowIndex();
        String content = shadowModule().get(0).content();

        // the parameter `limit` (not the field): its declaration and the two
        // uses inside run() — the field's own mentions stay untouched
        RenameResolution resolution = ADAPTER.renameResolution(index,
                SymbolTarget.ofOffset(SERVICE_PATH, byteAt(content, "run(int limit") + 8),
                "maxCount");

        assertEquals(RenameResolution.State.RESOLVED, resolution.state(), resolution.detail());
        assertEquals(SymbolKind.PARAMETER, resolution.declaration().kind());
        assertEquals(Boolean.TRUE, resolution.symbolIntact(),
                "the preview binding count must match the original");
        // rewrite ranges = the declaration identifier + `limit * 2` (the
        // `this.limit` field access name is not a NameExpr and the field
        // declaration is a SimpleName — neither can bind to the parameter)
        assertEquals(2, resolution.occurrences().size());
        for (var range : resolution.occurrences()) {
            // a rewrite range is the OLD identifier's span — the splice target
            assertEquals("limit".length(), range.endByte() - range.startByte());
        }
    }

    @Test
    void localVariableRenameIgnoresShadowedSiblingsAndCalls() {
        SymbolResolverAdapter.DeclarationIndex index = shadowIndex();
        String content = shadowModule().get(0).content();

        RenameResolution resolution = ADAPTER.renameResolution(index,
                SymbolTarget.ofOffset(SERVICE_PATH, byteAt(content, "int doubled = limit") + 4),
                "twiceValue");

        assertEquals(RenameResolution.State.RESOLVED, resolution.state(), resolution.detail());
        assertEquals(SymbolKind.LOCAL_VARIABLE, resolution.declaration().kind());
        assertEquals(Boolean.TRUE, resolution.symbolIntact());
        // declaration + `doubled > 0` + `int doubled2 = doubled` initializer
        // + println argument + `this.limit = doubled` — the `doubled2`
        // declarator's own name and the `call()` method invocation are NOT
        // occurrences (the SimpleName-scan face would have corrupted them)
        assertEquals(5, resolution.occurrences().size());
        assertEquals("doubled".length(),
                resolution.occurrences().get(0).endByte()
                        - resolution.occurrences().get(0).startByte());
    }

    @Test
    void conflictOnSiblingBlockLocal() {
        SymbolResolverAdapter.DeclarationIndex index = shadowIndex();
        String content = shadowModule().get(0).content();

        // renaming the parameter to `doubled` collides with the method's
        // local declared in a nested statement — declaredNames at the
        // parameter position alone would miss it
        RenameResolution resolution = ADAPTER.renameResolution(index,
                SymbolTarget.ofOffset(SERVICE_PATH, byteAt(content, "run(int limit") + 8),
                "doubled");

        assertEquals(RenameResolution.State.CONFLICT, resolution.state());
        assertTrue(resolution.detail().contains("doubled"));
        assertNull(resolution.declaration());
        assertTrue(resolution.occurrences().isEmpty());
        assertNull(resolution.symbolIntact());
    }

    @Test
    void conflictOnFieldNameCapture() {
        // dedicated module: the new name exists ONLY as a field, so the
        // method-boundary face passes and the field face is what rejects —
        // unqualified field mentions after the rename point would be
        // captured by the renamed local (silent-capture guard, plan 10
        // adjudication 3)
        List<SourceFile> module = List.of(new SourceFile(SERVICE_PATH, """
                package a;

                public class Service {
                    private int total;

                    void run(int seed) {
                        int doubled = seed * 2;
                        System.out.println(doubled + total);
                    }
                }
                """));
        SymbolResolverAdapter.DeclarationIndex index = ADAPTER.buildIndex(module);
        String content = module.get(0).content();

        RenameResolution resolution = ADAPTER.renameResolution(index,
                SymbolTarget.ofOffset(SERVICE_PATH, byteAt(content, "int doubled") + 4),
                "total");

        assertEquals(RenameResolution.State.CONFLICT, resolution.state());
        assertTrue(resolution.detail().contains("field"), resolution.detail());
    }

    @Test
    void selfRenameIsRejectedAsANonDegeneracyGuard() {
        SymbolResolverAdapter.DeclarationIndex index = shadowIndex();
        String content = shadowModule().get(0).content();

        RenameResolution resolution = ADAPTER.renameResolution(index,
                SymbolTarget.ofOffset(SERVICE_PATH, byteAt(content, "int doubled = limit") + 4),
                "doubled");

        assertEquals(RenameResolution.State.CONFLICT, resolution.state());
        assertTrue(resolution.detail().contains("no-op"));
    }

    @Test
    void memberTargetsEnterTheSecondRung() {
        SymbolResolverAdapter.DeclarationIndex index = shadowIndex();
        String content = shadowModule().get(0).content();

        // the `call` method's unqualified self-call (`call();`) is a bound
        // declaring-class reference: the rename collects it alongside the
        // declaration
        RenameResolution methodTarget = ADAPTER.renameResolution(index,
                SymbolTarget.ofOffset(SERVICE_PATH, byteAt(content, "void call") + 5),
                "invoke");
        assertEquals(RenameResolution.State.RESOLVED, methodTarget.state(),
                methodTarget.detail());
        assertEquals(2, methodTarget.occurrences().size(),
                "declaration + the unqualified self-call");
        assertEquals(Boolean.TRUE, methodTarget.symbolIntact());

        // the field `limit` (same simple name as the parameter) renames with
        // its this-tail: declaration + `this.limit = doubled`
        RenameResolution fieldTarget = ADAPTER.renameResolution(index,
                SymbolTarget.ofOffset(SERVICE_PATH, byteAt(content, "private int limit") + 12),
                "counter");
        assertEquals(RenameResolution.State.RESOLVED, fieldTarget.state(),
                fieldTarget.detail());
        assertEquals(2, fieldTarget.occurrences().size());
        assertEquals(Boolean.TRUE, fieldTarget.symbolIntact());
    }

    @Test
    void unresolvedLocatorIsExplicit() {
        SymbolResolverAdapter.DeclarationIndex index = shadowIndex();

        RenameResolution resolution = ADAPTER.renameResolution(index,
                SymbolTarget.ofOffset(SERVICE_PATH, 1_000_000L), "anything");

        assertEquals(RenameResolution.State.UNRESOLVED, resolution.state());
        assertFalse(resolution.detail().isBlank());
    }

    @Test
    void zeroReferenceLocalResolvesWithOnlyItsOwnRange() {
        // an unused local: the only rewrite range is the declaration's own
        // identifier, and the assertion still holds symmetrically (0 == 0)
        List<SourceFile> module = List.of(new SourceFile(SERVICE_PATH, """
                package a;

                public class Service {
                    void lonely() {
                        int unused = 1;
                    }
                }
                """));
        SymbolResolverAdapter.DeclarationIndex index = ADAPTER.buildIndex(module);
        String content = module.get(0).content();

        RenameResolution resolution = ADAPTER.renameResolution(index,
                SymbolTarget.ofOffset(SERVICE_PATH, byteAt(content, "int unused") + 4),
                "idled");

        assertEquals(RenameResolution.State.RESOLVED, resolution.state(), resolution.detail());
        assertEquals(1, resolution.occurrences().size(),
                "only the declaration identifier rides the edit list");
        assertEquals(Boolean.TRUE, resolution.symbolIntact());
    }

    @Test
    void multibyteContentKeepsTheByteOffsetContract() {
        List<SourceFile> module = List.of(new SourceFile(SERVICE_PATH, """
                package a;

                // 注释里的多字节字符 — em dash — shifts byte offsets
                public class Service {
                    void run(int seed) {
                        int doubled = seed * 2;
                        System.out.println(doubled);
                    }
                }
                """));
        SymbolResolverAdapter.DeclarationIndex index = ADAPTER.buildIndex(module);
        String content = module.get(0).content();

        RenameResolution resolution = ADAPTER.renameResolution(index,
                SymbolTarget.ofOffset(SERVICE_PATH, byteAt(content, "int doubled") + 4),
                "twiceValue");

        assertEquals(RenameResolution.State.RESOLVED, resolution.state(), resolution.detail());
        assertEquals(2, resolution.occurrences().size());
        assertEquals(Boolean.TRUE, resolution.symbolIntact());
        for (var range : resolution.occurrences()) {
            assertNotEquals(0, range.endByte() - range.startByte());
        }
    }
}
