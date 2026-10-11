package io.nop.bytecode.collect;

import io.nop.bytecode.NopBytecodeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end tests for the collection layer: sources are compiled in a @TempDir by the JDK
 * compiler, then collected from the output directory (or a jar built from it), covering the
 * full chain from input to CollectResult delta lists.
 */
class ClassArtifactCollectorTest {

    @TempDir
    Path tmp;

    private static final String SRC_A = """
            package demo;
            public class Alpha {
                public String hi() { return "a"; }
            }
            """;
    private static final String SRC_B = """
            package demo;
            public class Beta {
                public int answer() { return 42; }
            }
            """;

    /** Compiles the given sources into outDir at the given --release level. Returns outDir. */
    private Path compile(String release, Path outDir, String fileName, String source) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "system java compiler available");
        Path srcDir = tmp.resolve("src-" + fileName.replace('.', '-'));
        Files.createDirectories(srcDir);
        Files.write(srcDir.resolve(fileName), source.getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(outDir);
        int rc = compiler.run(null, null, null,
                "--release", release, "-d", outDir.toString(), srcDir.resolve(fileName).toString());
        assertEquals(0, rc, "fixture compilation failed for " + fileName);
        return outDir;
    }

    private Path compileClassesDir(String release, String fileName, String source) throws IOException {
        return compile(release, tmp.resolve("classes-" + fileName.replace('.', '-')), fileName, source);
    }

    @Test
    void directoryCollectionParsesClassNameAndVersionFromBytecode() throws IOException {
        Path classes = compileClassesDir("17", "Alpha.java", SRC_A);
        CollectResult result = new ClassArtifactCollector().collect(
                List.of(CollectInput.directory(classes)), new CollectManifest());

        assertEquals(1, result.artifacts().size());
        ClassArtifact artifact = result.artifacts().get(0);
        // className/majorVersion come from the ClassReader parse of the bytecode, not the file name
        assertEquals("demo.Alpha", artifact.className());
        assertEquals(61, artifact.majorVersion());
        assertEquals("demo/Alpha.class", artifact.relativePath());
        assertEquals(64, artifact.sha256().length());
        assertEquals(1, result.added().size());
        assertEquals(0, result.changed().size());
        assertEquals(0, result.removed().size());
    }

    @Test
    void jarCollectionEnumeratesClassEntries() throws IOException {
        Path classes = compileClassesDir("17", "Alpha.java", SRC_A);
        Path jar = tmp.resolve("demo.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            try (Stream<Path> walk = Files.walk(classes)) {
                for (Path p : walk.filter(Files::isRegularFile).toList()) {
                    out.putNextEntry(new JarEntry(classes.relativize(p).toString().replace('\\', '/')));
                    out.write(Files.readAllBytes(p));
                }
            }
        }
        CollectResult result = new ClassArtifactCollector().collect(
                List.of(CollectInput.jar(jar)), new CollectManifest());

        assertEquals(1, result.artifacts().size());
        ClassArtifact artifact = result.artifacts().get(0);
        assertTrue(artifact.relativePath().endsWith("demo.jar!demo/Alpha.class"), artifact.relativePath());
        assertEquals("demo.Alpha", artifact.className());
    }

    @Test
    void incrementalRunReportsAddedChangedRemoved() throws IOException {
        Path classes = compileClassesDir("17", "Alpha.java", SRC_A);
        Path manifestFile = tmp.resolve("collect.properties");
        ClassArtifactCollector collector = new ClassArtifactCollector();

        CollectManifest manifest = CollectManifest.load(manifestFile);
        CollectResult first = collector.collect(List.of(CollectInput.directory(classes)), manifest);
        assertEquals(1, first.added().size());
        manifest.save(manifestFile);

        // second run over the same input: everything unchanged, nothing rehashed
        CollectManifest reloaded = CollectManifest.load(manifestFile);
        CollectResult second = collector.collect(List.of(CollectInput.directory(classes)), reloaded);
        assertEquals(0, second.added().size());
        assertEquals(0, second.changed().size());
        assertEquals(0, second.removed().size());
        assertEquals(1, second.unchangedCount());

        // change one class: rewrite the source with a different body, recompile into a fresh
        // output dir, then swap the collected directory content (mtime bumped explicitly to
        // defeat coarse filesystem timestamp granularity)
        Path classes2 = compile("17", tmp.resolve("classes-Alpha2"), "Alpha.java", SRC_A.replace("\"a\"", "\"a2\""));
        Path target = classes.resolve("demo/Alpha.class");
        Files.copy(classes2.resolve("demo/Alpha.class"), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.setLastModifiedTime(target, FileTime.fromMillis(System.currentTimeMillis() + 60_000));

        CollectManifest m3 = CollectManifest.load(manifestFile);
        CollectResult third = collector.collect(List.of(CollectInput.directory(classes)), m3);
        assertEquals(1, third.changed().size(), "changed: " + third.changed());
        assertEquals(0, third.added().size());

        // removal: delete a class artifact directly from the input directory
        Files.delete(target);
        CollectManifest m4 = CollectManifest.load(manifestFile);
        CollectResult fourth = collector.collect(List.of(CollectInput.directory(classes)), m4);
        assertEquals(1, fourth.removed().size());
        assertEquals("demo/Alpha.class", fourth.removed().get(0));
    }

    @Test
    void missingInputFailsLoudlyWithPathsInMessage() {
        Path missing = tmp.resolve("does-not-exist");
        MissingInputException ex = assertThrows(MissingInputException.class,
                () -> new ClassArtifactCollector().collect(
                        List.of(CollectInput.directory(missing), CollectInput.jar(tmp.resolve("nope.jar"))),
                        new CollectManifest()));
        assertTrue(ex.getMessage().contains("does-not-exist"), ex.getMessage());
        assertTrue(ex.getMessage().contains("nope.jar"), ex.getMessage());
    }

    @Test
    void corruptClassFileFailsLoudlyWithPathInMessage() throws IOException {
        Path classes = compileClassesDir("17", "Alpha.java", SRC_A);
        Path corrupt = classes.resolve("demo/Corrupt.class");
        Files.write(corrupt, "definitely not a class file".getBytes(StandardCharsets.UTF_8));
        NopBytecodeException ex = assertThrows(NopBytecodeException.class,
                () -> new ClassArtifactCollector().collect(
                        List.of(CollectInput.directory(classes)), new CollectManifest()));
        assertTrue(ex.getMessage().contains("demo/Corrupt.class"), ex.getMessage());
    }

    @Test
    void v65ClassFileCollectedOnModernJdk() throws IOException {
        assumeTrue(Runtime.version().feature() >= 21, "v65 fixture needs a JDK with --release 21");
        Path classes = compileClassesDir("21", "Alpha.java", SRC_A);
        CollectResult result = new ClassArtifactCollector().collect(
                List.of(CollectInput.directory(classes)), new CollectManifest());
        assertEquals(65, result.artifacts().get(0).majorVersion());
    }
}
