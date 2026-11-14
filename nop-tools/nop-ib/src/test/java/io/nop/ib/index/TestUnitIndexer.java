package io.nop.ib.index;

import io.nop.javac.jdk.JavaCompileOptions;
import io.nop.javac.jdk.JavaFileCompileResult;
import io.nop.javac.jdk.JdkJavaCompiler;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestUnitIndexer {
    private static final String SRC_A = """
            package test;

            public class Alpha {
                public static final int MAGIC = 42;
                private int hidden;
                public int visible(String s) { return new Beta().magic(); }
                public void usesGamma() { Gamma g = new Gamma(); g.run(); }
                protected class Inner { }
            }
            """;
    private static final String SRC_B = """
            package test;

            public class Beta {
                public static final String TYPE = "t";
                public int magic() { return Alpha.MAGIC; }
            }
            """;
    private static final String SRC_C = """
            package test;

            class Gamma {
                public void run() { }
            }
            """;

    private Path compileFixture(Path tmp) throws Exception {
        Path src = tmp.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Alpha.java"), SRC_A);
        Files.writeString(src.resolve("Beta.java"), SRC_B);
        Files.writeString(src.resolve("Gamma.java"), SRC_C);
        Path out = tmp.resolve("classes");
        Files.createDirectories(out);
        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(
                List.of(src.resolve("Alpha.java").toFile(), src.resolve("Beta.java").toFile(),
                        src.resolve("Gamma.java").toFile()),
                new JavaCompileOptions().setOutputDir(out.toFile()).setClassPaths(List.of()));
        assertTrue(result.isSuccess(), String.valueOf(result.getDiagnostics()));
        return out;
    }

    @Test
    void testIndexCapturesRefsConstantsAbiSource(@org.junit.jupiter.api.io.TempDir Path tmp)
            throws Exception {
        Path out = compileFixture(tmp);
        UnitIndexer indexer = new UnitIndexer();
        UnitIndex index = indexer.scan("fixture", List.of(out), tmp);
        assertEquals(4, index.size()); // Alpha, Alpha$Inner, Beta, Gamma

        ClassInfo alpha = index.classInfo("test.Alpha");
        assertNotNull(alpha);
        // constant inlining surface
        assertEquals("42", alpha.getInlineConstants().get("MAGIC"));
        // cross-class reference surface (from bytecode constant pool)
        assertTrue(alpha.getRefs().contains("test.Beta"), String.valueOf(alpha.getRefs()));
        assertTrue(alpha.getRefs().contains("test.Gamma"), String.valueOf(alpha.getRefs()));
        // source mapping derived from package + output dir kind
        assertEquals("src/main/java/test/Alpha.java", alpha.getSourceFile());

        // inner class captured
        assertNotNull(index.classInfo("test.Alpha$Inner"));

        // ABI digest reflects accessible surface; digest changes when a public signature changes
        String abiBefore = alpha.getAbiDigest();
        ClassInfo beta = index.classInfo("test.Beta");
        assertEquals("t", beta.getInlineConstants().get("TYPE"));
        assertNotNull(abiBefore);

        // reverse edges: Alpha references Beta
        var dependents = index.dependents();
        assertTrue(dependents.getOrDefault("test.Beta", java.util.Set.of()).contains("test.Alpha"));
        assertTrue(dependents.getOrDefault("test.Gamma", java.util.Set.of()).contains("test.Alpha"));
    }

    @Test
    void testAbiDigestChangesOnSignatureChange(@org.junit.jupiter.api.io.TempDir Path tmp)
            throws Exception {
        Path out = compileFixture(tmp);
        UnitIndexer indexer = new UnitIndexer();
        String abiBefore = indexer.scan("fixture", List.of(out), tmp).classInfo("test.Beta").getAbiDigest();

        // change Beta's public surface (new public method)
        Files.writeString(tmp.resolve("src/Beta.java"), """
                package test;

                public class Beta {
                    public static final String TYPE = "t";
                    public int magic() { return Alpha.MAGIC; }
                    public int extra() { return 1; }
                }
                """);
        Path out2 = tmp.resolve("classes2");
        Files.createDirectories(out2);
        new JdkJavaCompiler().compileFiles(
                List.of(tmp.resolve("src/Alpha.java").toFile(), tmp.resolve("src/Beta.java").toFile(),
                        tmp.resolve("src/Gamma.java").toFile()),
                new JavaCompileOptions().setOutputDir(out2.toFile()).setClassPaths(List.of()));
        String abiAfter = indexer.scan("fixture", List.of(out2), tmp).classInfo("test.Beta").getAbiDigest();
        assertTrue(!abiBefore.equals(abiAfter), "public method addition must change ABI digest");

        // method-body-only change must NOT change ABI digest
        Path out3 = tmp.resolve("classes3");
        Files.createDirectories(out3);
        Files.writeString(tmp.resolve("src/Beta.java"), """
                package test;

                public class Beta {
                    public static final String TYPE = "t";
                    public int magic() { return Alpha.MAGIC + 1; }
                    public int extra() { return 1; }
                }
                """);
        new JdkJavaCompiler().compileFiles(
                List.of(tmp.resolve("src/Alpha.java").toFile(), tmp.resolve("src/Beta.java").toFile(),
                        tmp.resolve("src/Gamma.java").toFile()),
                new JavaCompileOptions().setOutputDir(out3.toFile()).setClassPaths(List.of()));
        String abiBodyOnly = indexer.scan("fixture", List.of(out3), tmp).classInfo("test.Beta").getAbiDigest();
        assertEquals(abiAfter, abiBodyOnly, "body-only change must be binary-compatible");
    }

    // ---- G-C tail defect regression: dispatchable test-class filtering ----

    private static final String SRC_TEST_MIXED = """
            package test;

            public class TestMixed {
                static class ExposedHelper extends Object {
                    void util() { }
                }

                @org.junit.jupiter.api.Nested
                class InnerSuite {
                    @org.junit.jupiter.api.Test
                    void innerTest() { }
                }

                @org.junit.jupiter.api.Test
                void ownTest() { }
            }
            """;
    private static final String SRC_TEST_SUB = """
            package test;

            public class TestSub extends TestMixedBase {
            }
            """;
    private static final String SRC_TEST_BASE = """
            package test;

            public abstract class TestMixedBase {
                @org.junit.jupiter.api.Test
                void inheritedTest() { }
            }
            """;

    private static List<String> junitClasspath() {
        List<String> cp = new java.util.ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(":")) {
            if (entry.contains("junit-jupiter") || entry.contains("junit-platform")
                    || entry.contains("opentest4j") || entry.contains("apiguardian"))
                cp.add(entry);
        }
        return cp;
    }

    @Test
    void testClassesFiltersHelpersAndDedupesNested(@org.junit.jupiter.api.io.TempDir Path tmp)
            throws Exception {
        Path src = tmp.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("TestMixed.java"), SRC_TEST_MIXED);
        Files.writeString(src.resolve("TestSub.java"), SRC_TEST_SUB);
        Files.writeString(src.resolve("TestMixedBase.java"), SRC_TEST_BASE);
        Path out = tmp.resolve("test-classes");
        Files.createDirectories(out);
        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(
                List.of(src.resolve("TestMixed.java").toFile(), src.resolve("TestSub.java").toFile(),
                        src.resolve("TestMixedBase.java").toFile()),
                new JavaCompileOptions().setOutputDir(out.toFile()).setClassPaths(junitClasspath()));
        assertTrue(result.isSuccess(), String.valueOf(result.getDiagnostics()));

        UnitIndex index = new UnitIndexer().scan("fixture", List.of(out), tmp);
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (ClassInfo info : index.testClasses())
            names.add(info.getClassName());

        // helper without test methods is NOT dispatchable (G-C tail defect:
        // it used to be dispatched and crashed the worker on CNFE during discovery)
        assertTrue(!names.contains("test.TestMixed$ExposedHelper"), String.valueOf(names));
        // @Nested member of a dispatchable outer is deduped (outer discovers it)
        assertTrue(!names.contains("test.TestMixed$InnerSuite"), String.valueOf(names));
        // outer with own tests IS dispatchable
        assertTrue(names.contains("test.TestMixed"), String.valueOf(names));
        // concrete subclass with only inherited tests stays dispatchable via super chain
        assertTrue(names.contains("test.TestSub"), String.valueOf(names));
        // abstract base with @Test methods stays a (harmless) dispatch candidate
        assertTrue(names.contains("test.TestMixedBase"), String.valueOf(names));
        // marker facts recorded for ABI-independent reasoning
        assertTrue(index.classInfo("test.TestMixed").hasTestMethods());
        assertTrue(index.classInfo("test.TestMixed$InnerSuite").isNested());
        assertEquals("java.lang.Object", index.classInfo("test.TestMixed$ExposedHelper").getSuperName());
    }

    @Test
    void testRoundTrip(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path out = compileFixture(tmp);
        UnitIndex index = new UnitIndexer().scan("fixture", List.of(out), tmp);
        UnitIndex restored = UnitIndex.fromMap(index.toMap());
        assertEquals(index.size(), restored.size());
        assertEquals(index.classInfo("test.Beta").getAbiDigest(),
                restored.classInfo("test.Beta").getAbiDigest());
        assertEquals(index.classInfo("test.Alpha").getRefs().size(),
                restored.classInfo("test.Alpha").getRefs().size());
        assertEquals(index.classInfo("test.Alpha").getInlineConstants(),
                restored.classInfo("test.Alpha").getInlineConstants());
    }

    @Test
    void testStoreRoundTrip(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo);
        Path out = compileFixture(tmp);
        UnitIndex index = new UnitIndexer().scan("fixture", List.of(out), tmp);
        UnitIndexStore store = new UnitIndexStore(repo);
        store.save(index);
        assertTrue(store.exists("fixture"));
        UnitIndex loaded = store.load("fixture");
        assertEquals(index.size(), loaded.size());
        assertEquals(index.classInfo("test.Alpha").getContentHash(),
                loaded.classInfo("test.Alpha").getContentHash());
        assertTrue(store.cacheSizeBytes() > 0);
    }
}
