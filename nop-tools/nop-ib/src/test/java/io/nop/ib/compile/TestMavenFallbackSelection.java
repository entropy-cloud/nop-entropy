package io.nop.ib.compile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defect 24 regression pin: when neither the mvnq wrapper nor the project's
 * mvnw exists (external project without a wrapper, e.g. flink-agents), the
 * delegation must run plain {@code mvn} from PATH — not bash against a missing
 * repoRoot/mvnw, which degraded every fallback to a "No such file or
 * directory" line with zero test summaries.
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
}
