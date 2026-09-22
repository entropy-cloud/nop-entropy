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
 * {@link TargetScanner} matrix: directory recursion, the explicit
 * extension-to-language table (item 19: {@code ts→typescript},
 * {@code tsx→tsx}, {@code java→java} migrated from the v1
 * extension-equals-id convention), explicit skipped-file counting (unknown
 * extensions and extension-less names — never silent), stable ordering, and
 * the hard failure on nonexistent targets.
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

    // ==================== extension table (item 19) ====================

    @Test
    public void tsExtensionMapsToTypescriptAndTsxToTsxWhenRegistered() throws IOException {
        LanguageRegistry registry = registryWithJava();
        registry.register(JavaBindingTestSupport.stubBinding("typescript"));
        registry.register(JavaBindingTestSupport.stubBinding("tsx"));
        Files.writeString(dir.resolve("App.ts"), "const x: number = 1;");
        Files.writeString(dir.resolve("Page.tsx"), "export const p = <div>hi</div>;");

        TargetScanner.ScanResult scan = TargetScanner.scan(List.of(dir.toString()), registry);

        assertEquals(2, scan.lintable().size());
        assertEquals("typescript", scan.lintable().get(0).languageId());
        assertTrue(scan.lintable().get(0).path().getFileName().toString().endsWith("App.ts"));
        assertEquals("tsx", scan.lintable().get(1).languageId());
        assertEquals(0, scan.skipped().total());
    }

    @Test
    public void tableExtensionWithUnregisteredLanguageGoesToSkippedBucket() throws IOException {
        // registry binds java only: a .ts file maps to 'typescript' via the
        // table but that id is unregistered — explicitly counted, never silent
        Files.writeString(dir.resolve("orphan.ts"), "const x = 1;");

        TargetScanner.ScanResult scan = TargetScanner.scan(List.of(dir.toString()),
                registryWithJava());

        assertEquals(0, scan.lintable().size());
        assertEquals(1, scan.skipped().total());
        assertEquals(1, scan.skipped().byExtension().get("ts"));
    }

    @Test
    public void mtsAndCtsAreOutsideTheV1TableAndExplicitlySkipped() throws IOException {
        Files.writeString(dir.resolve("mod.mts"), "export const x = 1;");
        Files.writeString(dir.resolve("util.cts"), "export const y = 2;");

        TargetScanner.ScanResult scan = TargetScanner.scan(List.of(dir.toString()),
                registryWithJava());

        assertEquals(0, scan.lintable().size());
        assertEquals(2, scan.skipped().total());
        assertEquals(1, scan.skipped().byExtension().get("mts"));
        assertEquals(1, scan.skipped().byExtension().get("cts"));
    }

    @Test
    public void uppercaseTableExtensionBindsCaseInsensitively() throws IOException {
        LanguageRegistry registry = registryWithJava();
        registry.register(JavaBindingTestSupport.stubBinding("typescript"));
        Files.writeString(dir.resolve("upper.TS"), "const x = 1;");

        TargetScanner.ScanResult scan = TargetScanner.scan(List.of(dir.toString()), registry);

        assertEquals(1, scan.lintable().size());
        assertEquals("typescript", scan.lintable().get(0).languageId());
    }

    @Test
    public void extensionTableIsConsistentInBothDirections() {
        assertEquals("typescript", TargetScanner.languageIdForExtension("ts"));
        assertEquals("tsx", TargetScanner.languageIdForExtension("tsx"));
        assertEquals("java", TargetScanner.languageIdForExtension("java"));
        assertEquals(List.of("ts"), TargetScanner.extensionsForLanguage("typescript"));
        assertEquals(List.of("tsx"), TargetScanner.extensionsForLanguage("tsx"));
        assertEquals(List.of("java"), TargetScanner.extensionsForLanguage("java"));
        assertEquals(List.of(), TargetScanner.extensionsForLanguage("python"));
        assertEquals(List.of("ts"), TargetScanner.extensionsForLanguage(" TYPESCRIPT "),
                "the inverse lookup normalizes like the registry");
    }
}
