package io.nop.lint.core.cli;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.testing.JavaBindingTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TargetScanner} matrix: directory recursion, the v1
 * extension-equals-language-id binding convention, explicit skipped-file
 * counting (unknown extensions and extension-less names — never silent),
 * stable ordering, and the hard failure on nonexistent targets.
 */
public class TestTargetScanner {

    @TempDir
    Path dir;

    private LanguageRegistry registryWithJava() {
        return JavaBindingTestSupport.registryWithJava();
    }

    @Test
    public void directoryIsWalkedRecursivelyAndBoundByExtension() throws IOException {
        Files.createDirectories(dir.resolve("a/b"));
        Files.writeString(dir.resolve("a/One.java"), "class One {}");
        Files.writeString(dir.resolve("a/b/Two.java"), "class Two {}");
        Files.writeString(dir.resolve("a/b/notes.txt"), "text");

        TargetScanner.ScanResult scan = TargetScanner.scan(List.of(dir.toString()),
                registryWithJava());

        assertEquals(2, scan.lintable().size());
        assertEquals("java", scan.lintable().get(0).languageId());
        assertTrue(scan.lintable().get(0).path().toString().endsWith("One.java"));
        assertEquals(1, scan.skipped().total());
        assertEquals(1, scan.skipped().byExtension().get("txt"));
    }

    @Test
    public void singleFileTargetIsTakenAsIs() throws IOException {
        Path file = dir.resolve("Solo.java");
        Files.writeString(file, "class Solo {}");

        TargetScanner.ScanResult scan = TargetScanner.scan(List.of(file.toString()),
                registryWithJava());

        assertEquals(1, scan.lintable().size());
        assertEquals(file, scan.lintable().get(0).path());
        assertEquals(0, scan.skipped().total());
    }

    @Test
    public void unknownExtensionAndMissingExtensionAreCountedExplicitly() throws IOException {
        Path readme = dir.resolve("README");
        Path log = dir.resolve("run.log");
        Files.writeString(readme, "text");
        Files.writeString(log, "text");

        TargetScanner.ScanResult scan = TargetScanner.scan(
                List.of(readme.toString(), log.toString()), registryWithJava());

        assertEquals(0, scan.lintable().size());
        assertEquals(2, scan.skipped().total());
        assertEquals(1, scan.skipped().byExtension().get(TargetScanner.NO_EXTENSION));
        assertEquals(1, scan.skipped().byExtension().get("log"));
        assertTrue(scan.skipped().describe().contains(TargetScanner.NO_EXTENSION),
                "the summary fragment must name the no-extension bucket: "
                        + scan.skipped().describe());
    }

    @Test
    public void uppercaseExtensionBindsCaseInsensitively() throws IOException {
        Path file = dir.resolve("Upper.JAVA");
        Files.writeString(file, "class Upper {}");

        TargetScanner.ScanResult scan = TargetScanner.scan(List.of(file.toString()),
                registryWithJava());

        assertEquals(1, scan.lintable().size());
        assertEquals("java", scan.lintable().get(0).languageId());
    }

    @Test
    public void lintableFilesAreSortedForStableOutput() throws IOException {
        Files.writeString(dir.resolve("b.java"), "class B {}");
        Files.writeString(dir.resolve("a.java"), "class A {}");

        TargetScanner.ScanResult scan = TargetScanner.scan(List.of(dir.toString()),
                registryWithJava());

        assertEquals(2, scan.lintable().size());
        assertTrue(scan.lintable().get(0).path().getFileName().toString().equals("a.java"));
        assertTrue(scan.lintable().get(1).path().getFileName().toString().equals("b.java"));
    }

    @Test
    public void nonexistentTargetFailsClosed() {
        Path missing = dir.resolve("does-not-exist");

        NopLintException ex = assertThrows(NopLintException.class,
                () -> TargetScanner.scan(List.of(missing.toString()), registryWithJava()));
        assertTrue(ex.getMessage().contains("does not exist"), ex.getMessage());
    }

    @Test
    public void emptyScanYieldsExplicitZeroSkippedSummary() throws IOException {
        Path file = dir.resolve("clean.java");
        Files.writeString(file, "class Clean {}");

        TargetScanner.ScanResult scan = TargetScanner.scan(List.of(file.toString()),
                registryWithJava());

        assertEquals(0, scan.skipped().total());
        assertEquals("0", scan.skipped().describe());
    }
}
