package io.nop.ib.javac;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Oracle pin for the 2026-11-20 mirroring channel: {@code --add-modules} replayed
 * by {@link JavaCompilerFacade} must reach the real javac — a source importing
 * {@code jdk.incubator.vector} compiles WITH the mirrored flag and fails WITHOUT
 * it (the failure mode is exactly the nop-rg-vector defect: package not visible).
 * Skipped when the running JDK does not expose the incubator module.
 */
class TestFacadeAddModulesOracle {

    private static final String SRC = """
            import jdk.incubator.vector.ByteVector;

            public class IncubProbe {
                public int width() {
                    return ByteVector.SPECIES_PREFERRED.length();
                }
            }
            """;

    private record Fixture(Path src, Path out) {
    }

    private Fixture fixture() throws Exception {
        Path dir = Files.createTempDirectory("ib-oracle");
        Path src = dir.resolve("IncubProbe.java");
        Files.writeString(src, SRC);
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        return new Fixture(src, out);
    }

    private boolean incubatorAvailable() {
        try {
            Class.forName("jdk.incubator.vector.ByteVector");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** WITH the mirrored --add-modules: the incubator import compiles. */
    @Test
    void addModulesMirroredCompilesIncubatorImport() throws Exception {
        if (!incubatorAvailable())
            return; // oracle only meaningful where jdk.incubator.vector exists
        Fixture f = fixture();
        JavaCompilerFacade.CompileResult result = JavaCompilerFacade.compile(
                List.of(f.src().toFile()), f.out(), List.of(), String.valueOf(Runtime.version().feature()),
                null, "none", List.of(f.src().getParent().toFile()), false,
                List.of("jdk.incubator.vector"), false);
        assertTrue(result.success(), () -> "mirrored --add-modules must compile: " + result.diagnostics());
        assertTrue(Files.exists(f.out().resolve("IncubProbe.class")), "class product must exist");
    }

    /** WITHOUT it: the same source fails with package-not-visible (defect shape). */
    @Test
    void withoutAddModulesPackageInvisible() throws Exception {
        if (!incubatorAvailable())
            return;
        Fixture f = fixture();
        JavaCompilerFacade.CompileResult result = JavaCompilerFacade.compile(
                List.of(f.src().toFile()), f.out(), List.of(), String.valueOf(Runtime.version().feature()),
                null, "none", List.of(f.src().getParent().toFile()), false,
                List.of(), false);
        assertFalse(result.success(), "without --add-modules the incubator import must fail");
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("incubator")),
                () -> "diagnostics must name the incubator package: " + result.diagnostics());
    }

    /** --enable-preview replay reaches javac (compiles a preview-free source without error). */
    @Test
    void enablePreviewReplayedWithoutError() throws Exception {
        if (!incubatorAvailable())
            return;
        Fixture f = fixture();
        Path plain = f.src().getParent().resolve("Plain.java");
        Files.writeString(plain, "public class Plain { int w() { return 1; } }");
        JavaCompilerFacade.CompileResult result = JavaCompilerFacade.compile(
                List.of(plain.toFile()), f.out(), List.of(), String.valueOf(Runtime.version().feature()),
                null, "none", List.of(plain.getParent().toFile()), false,
                List.of(), true);
        assertTrue(result.success(), () -> "--enable-preview replay must compile plain sources: "
                + result.diagnostics());
    }
}
