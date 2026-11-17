package io.nop.ib.compile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Maven location resolution pins (wave 11): explicit {@code nop.ib.maven}
 * system property wins, then the project's mvnw wrapper, then plain mvn from
 * PATH (defect 24 regression: an external project without a wrapper must run
 * PATH mvn, not bash against a missing repoRoot/mvnw).
 */
class TestMavenFallbackSelection {

    @Test
    void fallsBackToPathMvnWhenNoWrapper(@TempDir Path tmp) throws Exception {
        // empty project root: no mvnq, no mvnw
        StringBuilder out = new StringBuilder();
        int code = new MavenFallback(tmp, java.time.Duration.ofSeconds(60))
                .delegate("--version", out, new StringBuilder());
        assertEquals(0, code, "plain mvn must be invocable, output: " + out);
        assertTrue(out.toString().contains("Maven home"),
                "expected mvn --version output, got: " + out);
    }

    @Test
    void wrapperTakePrecedenceWhenPresent(@TempDir Path tmp) throws Exception {
        Path mvnw = tmp.resolve("mvnw");
        Files.writeString(mvnw, "#!/bin/sh\necho WRAPPER-USED\n");
        // make it executable
        try {
            Files.setPosixFilePermissions(mvnw,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (IOException | UnsupportedOperationException e) {
            throw new IllegalStateException("posix permissions unavailable", e);
        }
        StringBuilder out = new StringBuilder();
        int code = new MavenFallback(tmp, java.time.Duration.ofSeconds(60))
                .delegate("--version", out, new StringBuilder());
        assertEquals(0, code, out.toString());
        assertTrue(out.toString().contains("WRAPPER-USED"),
                "project mvnw must take precedence over PATH mvn, got: " + out);
    }

    @Test
    void configuredMavenPropertyWins(@TempDir Path tmp) throws Exception {
        Path fakeMvn = tmp.resolve("my-mvn");
        Files.writeString(fakeMvn, "#!/bin/sh\necho CONFIGURED-USED\n");
        try {
            Files.setPosixFilePermissions(fakeMvn,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (IOException | UnsupportedOperationException e) {
            throw new IllegalStateException("posix permissions unavailable", e);
        }
        // a mvnw also exists — the explicit property must still win
        Path mvnw = tmp.resolve("mvnw");
        Files.writeString(mvnw, "#!/bin/sh\necho WRAPPER-USED\n");
        try {
            Files.setPosixFilePermissions(mvnw,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (IOException | UnsupportedOperationException e) {
            throw new IllegalStateException("posix permissions unavailable", e);
        }
        StringBuilder out = new StringBuilder();
        int code;
        try {
            System.setProperty("nop.ib.maven", fakeMvn.toString());
            code = new MavenFallback(tmp, java.time.Duration.ofSeconds(60))
                    .delegate("--version", out, new StringBuilder());
        } finally {
            System.clearProperty("nop.ib.maven");
        }
        assertEquals(0, code, out.toString());
        assertTrue(out.toString().contains("CONFIGURED-USED"),
                "nop.ib.maven must override mvnw and PATH, got: " + out);
        assertFalse(out.toString().contains("WRAPPER-USED"),
                "configured maven must not fall through to the wrapper");
    }
}
