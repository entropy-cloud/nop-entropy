package io.nop.lint.java.semantic;

import io.nop.lint.core.semantic.TypeResolutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JavaTypeResolver matrix (roadmap item 26 Phase 2): JDK type
 * resolution through the reflection solver, the F8 expectedType semantics
 * (imports-aware simple names), the fail-closed translation layer (F5),
 * the cross-file cache isolation (F4), and cache invalidation on
 * initProject.
 */
public class TestJavaTypeResolver {

    @TempDir
    Path dir;

    private Path write(String name, String source) throws IOException {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
        return file;
    }

    // ==================== resolution ====================

    @Test
    public void jdkTypeNameResolvesAtAPosition() throws IOException {
        Path file = write("A.java", """
                import java.util.List;

                class A {
                    void m() {
                        List<String> items = new java.util.ArrayList<>();
                    }
                }
                """);
        JavaTypeResolver resolver = new JavaTypeResolver();
        // the `items` declarator sits on 0-based line 4, col 8 (the text
        // block does NOT emit a leading newline — the terminator after the
        // opening delimiter is not content); the generic describe carries
        // the type arguments
        assertEquals("java.util.List<java.lang.String>",
                resolver.typeNameAt(file.toString(), 4, 8));
    }

    @Test
    public void stringVariableIsAssignableToStringAndCharSequence() throws IOException {
        Path file = write("B.java", """
                class B {
                    void m() {
                        String s = "x";
                    }
                }
                """);
        JavaTypeResolver resolver = new JavaTypeResolver();
        // `s` declarator: 0-based line 2, col 8
        assertTrue(resolver.isAssignableTo(file.toString(), 2, 8, "String"),
                "the F8 semantics resolve the simple name through java.lang");
        assertTrue(resolver.isAssignableTo(file.toString(), 2, 8, "java.lang.String"),
                "the FQN compares directly");
        assertTrue(resolver.isAssignableTo(file.toString(), 2, 8, "CharSequence")
                        || resolver.isAssignableTo(file.toString(), 2, 8, "java.lang.CharSequence"),
                "the ancestor chain satisfies assignability");
    }

    @Test
    public void importsAwareSimpleNameResolution() throws IOException {
        Path file = write("C.java", """
                import java.util.ArrayList;

                class C {
                    void m() {
                        ArrayList<String> items = new ArrayList<>();
                    }
                }
                """);
        JavaTypeResolver resolver = new JavaTypeResolver();
        // the simple name `ArrayList` resolves through the unit's import
        assertEquals("java.util.ArrayList<java.lang.String>",
                resolver.typeNameAt(file.toString(), 4, 8));
    }

    // ==================== fail-closed ====================

    @Test
    public void projectLocalTypeFailsClosedWithTranslatedException() throws IOException {
        Path file = write("D.java", """
                class D {
                    void m() {
                        Helper helper = null;
                    }
                }
                """);
        JavaTypeResolver resolver = new JavaTypeResolver();
        // the declarator sits on 0-based line 2, col 8
        TypeResolutionException ex = assertThrows(TypeResolutionException.class,
                () -> resolver.typeNameAt(file.toString(), 2, 8));
        assertTrue(ex.getMessage().contains("resolution failed"), ex.getMessage());
    }

    @Test
    public void positionOutsideTheUnitFailsClosed() throws IOException {
        Path file = write("E.java", "class E {\n}\n");
        JavaTypeResolver resolver = new JavaTypeResolver();
        assertThrows(TypeResolutionException.class,
                () -> resolver.typeNameAt(file.toString(), 99, 0));
    }

    // ==================== cache ====================

    @Test
    public void repeatedQueriesHitTheCacheIdempotently() throws IOException {
        Path file = write("F.java", """
                class F {
                    void m() {
                        String s = "x";
                    }
                }
                """);
        JavaTypeResolver resolver = new JavaTypeResolver();
        String first = resolver.typeNameAt(file.toString(), 2, 8);
        String second = resolver.typeNameAt(file.toString(), 2, 8);
        assertEquals(first, second, "the cache returns the same answer for the same key");
    }

    @Test
    public void cacheIsIsolatedAcrossFiles() throws IOException {
        // the same (line, col) in two files with different content must not
        // share a cached answer (the F4 cross-file leak)
        write("G1.java", "class G1 {\n    void m() {\n        String s = \"x\";\n    }\n}\n");
        Path g2 = write("G2.java", "class G2 {\n    void m() {\n        StringBuilder b = null;\n    }\n}\n");

        JavaTypeResolver resolver = new JavaTypeResolver();
        String fromG1 = resolver.typeNameAt(dir.resolve("G1.java").toString(), 2, 8);
        String fromG2 = resolver.typeNameAt(g2.toString(), 2, 8);

        assertEquals("java.lang.String", fromG1);
        assertEquals("java.lang.StringBuilder", fromG2,
                "the same (line, col) key must not leak across files");
    }

    @Test
    public void initProjectInvalidatesTheCache() throws IOException {
        Path file = write("H.java", """
                class H {
                    void m() {
                        String s = "x";
                    }
                }
                """);
        JavaTypeResolver resolver = new JavaTypeResolver();
        assertEquals("java.lang.String", resolver.typeNameAt(file.toString(), 2, 8));
        resolver.initProject(dir);
        // after invalidation the query re-resolves to the same answer (the
        // cache cleared, the file re-parsed)
        assertEquals("java.lang.String", resolver.typeNameAt(file.toString(), 2, 8));
    }
}
