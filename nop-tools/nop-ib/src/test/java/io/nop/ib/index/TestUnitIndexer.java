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
