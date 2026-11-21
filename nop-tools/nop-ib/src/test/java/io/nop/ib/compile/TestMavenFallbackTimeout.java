package io.nop.ib.compile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2583 Phase-2 pins (audit Major): the Maven delegation timeout must be
 * REAL (waitFor-driven, output pumped on a daemon thread) and observable —
 * a hung delegation returns {@code EXIT_LOCK_TIMEOUT = 75} with the timeout
 * reason in the err sink, instead of blocking forever in readLine and then
 * returning 1 with an empty err.
 *
 * <p>Mutation probe evidence: removing the pump thread turns the timeout pin
 * red — the main thread blocks in readLine before waitFor is ever reached and
 * the test times out (recorded in the plan Closure section).
 */
class TestMavenFallbackTimeout {
    @TempDir
    Path repo;

    @AfterEach
    void restoreMaven() {
        System.clearProperty("nop.ib.maven");
    }

    /** installs a fake "maven" and returns a script with the given body. */
    private Path script(String body) throws Exception {
        Path bin = repo.resolve("bin");
        Files.createDirectories(bin);
        Path sh = bin.resolve("fakemvn.sh");
        Files.writeString(sh, body);
        Files.setPosixFilePermissions(sh, PosixFilePermissions.fromString("rwxr-xr-x"));
        System.setProperty("nop.ib.maven", sh.toString());
        return sh;
    }

    /** pin: a hung delegation hits the timeout → exit 75 + err names the timeout. */
    @Test
    @Timeout(30)
    void hungDelegationTimesOutWith75AndErrDetail() throws Exception {
        // produces output forever, never exits: the old readLine-driven flow
        // would block here forever, the waitFor-driven flow must cut at the cap
        script("#!/bin/sh\nwhile true; do echo 'building...'; sleep 1; done\n");
        MavenFallback fallback = new MavenFallback(repo, java.time.Duration.ofSeconds(1));
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        long t0 = System.currentTimeMillis();
        int code = fallback.delegate("-pl :x test", out, err);
        long elapsed = System.currentTimeMillis() - t0;
        assertEquals(MavenFallback.EXIT_LOCK_TIMEOUT, code,
                "hung delegation must return the explicit 75, got " + code);
        assertTrue(err.toString().contains("timed out"),
                "err must carry the timeout reason: " + err);
        assertTrue(elapsed < 15_000, "timeout must cut at ~1s, took " + elapsed + "ms");
        assertTrue(out.toString().contains("building..."),
                "output produced before the kill must still be captured: " + out);
    }

    /** pin: a delegation that completes normally still returns the real exit. */
    @Test
    @Timeout(30)
    void completingDelegationReturnsRealExitAndOutput() throws Exception {
        script("#!/bin/sh\necho 'tests run: 3'\nexit 0\n");
        MavenFallback fallback = new MavenFallback(repo, java.time.Duration.ofSeconds(30));
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        int code = fallback.delegate("-pl :x test", out, err);
        assertEquals(0, code);
        assertTrue(out.toString().contains("tests run: 3"), out.toString());
        assertEquals(0, err.length());
    }
}
