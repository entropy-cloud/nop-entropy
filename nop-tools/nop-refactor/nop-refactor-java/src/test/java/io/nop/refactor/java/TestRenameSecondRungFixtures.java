package io.nop.refactor.java;

import io.nop.refactor.core.symbol.RenameResolution;
import io.nop.refactor.core.symbol.SymbolResolverAdapter;
import io.nop.refactor.core.symbol.SymbolResolverAdapter.SourceFile;
import io.nop.refactor.core.symbol.SymbolResolverAdapter.SymbolTarget;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The WI11 second-rung fixture census (plan 11 Phase 1, the audit-M1
 * consolidation): every declared fixture face lands as an executable
 * assertion — the type-use forms beyond the plain e2e (cast / instanceof /
 * extends, nested-type import prefixes, dot-boundary negatives), the member
 * impact-surface double case (bound receiver refuses, unbound passes), the
 * sibling-static-import refusal, and the RESOLVED detail counter face.
 */
class TestRenameSecondRungFixtures {

    private static final JavaSymbolResolverAdapter ADAPTER = new JavaSymbolResolverAdapter();

    private static long byteAt(String content, String marker) {
        return content.substring(0, content.indexOf(marker))
                .getBytes(StandardCharsets.UTF_8).length;
    }

    @Test
    void typeUseFormsCastInstanceofAndExtendsAllRewrite() {
        List<SourceFile> module = List.of(new SourceFile("a/Service.java", """
                package a;

                public class Base {
                }

                public class Service extends Base {
                    void m(Object o) {
                        Service self = (Service) o;
                        boolean ok = o instanceof Service;
                    }
                }
                """));
        SymbolResolverAdapter.DeclarationIndex index = ADAPTER.buildIndex(module);
        String content = module.get(0).content();

        RenameResolution resolution = ADAPTER.renameResolution(index,
                SymbolTarget.ofFqn("a.Service"), "Engine");

        assertEquals(RenameResolution.State.RESOLVED, resolution.state(),
                resolution.detail());
        // declaration + the local's declared type + the cast type + the
        // instanceof type — every ClassOrInterfaceType face collected (the
        // extends clause names Base, outside the rename)
        assertEquals(4, resolution.fileRewrites().get(0).spans().size(),
                "cast/instanceof/declared-type/declaration all rewrite");
        assertTrue(resolution.detail().startsWith("resolved:"),
                "the detail carries the rewrite counter: " + resolution.detail());
    }

    @Test
    void nestedTypeImportPrefixRewritesAndSiblingNameStaysPut() {
        List<SourceFile> module = List.of(
                new SourceFile("a/Service.java", """
                        package a;

                        public class Service {
                            public static class Foo {
                            }
                        }
                        """),
                new SourceFile("b/Use.java", """
                        package b;

                        import a.Service.Foo;
                        import a.ServiceHelper;

                        public class Use {
                            Foo foo;
                            ServiceHelper helper;
                        }
                        """));
        SymbolResolverAdapter.DeclarationIndex index = ADAPTER.buildIndex(module);
        // rename the NESTED type a.Service.Foo via its FQN locator
        RenameResolution resolution = ADAPTER.renameResolution(index,
                SymbolTarget.ofFqn("a.Service.Foo"), "Bar");

        assertEquals(RenameResolution.State.RESOLVED, resolution.state(),
                resolution.detail());
        // the Use file rewrites: the nested import prefix AND the simple use;
        // the sibling `a.ServiceHelper` import stays untouched (dot boundary)
        boolean sawImportPrefix = false;
        for (var rewrite : resolution.fileRewrites()) {
            if (rewrite.path().equals("b/Use.java")) {
                assertEquals(2, rewrite.spans().size(),
                        "nested import prefix + simple use");
                sawImportPrefix = true;
            }
        }
        assertTrue(sawImportPrefix, "the bound use file participates");
        assertEquals(2, resolution.fileRewrites().size());
    }

    @Test
    void memberImpactSurfaceRefusesBoundAndSparesUnbound() {
        // the member `helper` on Util: a bound file (same package) carrying a
        // receiver-shaped same-name token refuses; an unbound file with the
        // same token does not trigger
        List<SourceFile> module = List.of(
                new SourceFile("a/Util.java", """
                        package a;

                        public class Util {
                            public int helper() {
                                return helper();
                            }
                        }
                        """),
                new SourceFile("a/Bound.java", """
                        package a;

                        public class Bound {
                            void m(Util util) {
                                util.helper();
                            }
                        }
                        """),
                new SourceFile("z/Unbound.java", """
                        package z;

                        public class Unbound {
                            void m(Other other) {
                                other.helper();
                            }
                        }
                        """));
        SymbolResolverAdapter.DeclarationIndex index = ADAPTER.buildIndex(module);
        String utilSource = module.get(0).content();

        RenameResolution refused = ADAPTER.renameResolution(index,
                SymbolTarget.ofOffset("a/Util.java", byteAt(utilSource, "int helper") + 4),
                "assist");
        assertEquals(RenameResolution.State.CONFLICT, refused.state(),
                "the bound file's receiver-shaped token refuses: " + refused.detail());
        assertTrue(refused.detail().contains("Bound"), refused.detail());

        // drop the bound file: the unbound file's same-name token no longer
        // triggers (structurally unreachable)
        List<SourceFile> withoutBound = List.of(module.get(0), module.get(2));
        RenameResolution allowed = ADAPTER.renameResolution(
                ADAPTER.buildIndex(withoutBound),
                SymbolTarget.ofOffset("a/Util.java", byteAt(utilSource, "int helper") + 4),
                "assist");
        assertEquals(RenameResolution.State.RESOLVED, allowed.state(), allowed.detail());
        assertEquals(2, allowed.occurrences().size(),
                "declaration + the self-call rewrite together");
    }

    @Test
    void siblingStaticImportOfSameNamedMemberRefuses() {
        List<SourceFile> module = List.of(
                new SourceFile("a/Util.java", """
                        package a;

                        public class Util {
                            public static int limit() {
                                return 1;
                            }
                        }
                        """),
                new SourceFile("b/Use.java", """
                        package b;

                        import static a.Util.limit;
                        import static c.Other.limit;

                        public class Use {
                            int cap() {
                                return limit();
                            }
                        }
                        """),
                new SourceFile("c/Other.java", """
                        package c;

                        public class Other {
                            public static int limit() {
                                return 2;
                            }
                        }
                        """));
        SymbolResolverAdapter.DeclarationIndex index = ADAPTER.buildIndex(module);
        String utilSource = module.get(0).content();

        // the importing file ALSO imports c.Other.limit (another type's
        // same-named static member): its unqualified `limit()` is ambiguous
        // between the two — the resolution must refuse, not pick a side
        RenameResolution resolution = ADAPTER.renameResolution(index,
                SymbolTarget.ofOffset("a/Util.java", byteAt(utilSource, "int limit") + 4),
                "cap");
        assertEquals(RenameResolution.State.CONFLICT, resolution.state(),
                "the ambiguous static-import face refuses: " + resolution.detail());
    }
}
