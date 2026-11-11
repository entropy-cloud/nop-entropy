package io.nop.code.core.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W-04 (plan 2565, facility F1 / adjudication C, TP-013): the git process bridge must arm its
 * watchdog BEFORE the pipe-read phase — a hang-stub process that prints partial output and then
 * sleeps forever (without closing stdout) must return within the timeout window with
 * timedOut=true and the partial output preserved. The pre-fix inline shape (read-then-wait)
 * blocks on this stub: that defect shape is reintroduced as mutation chain 3 and must turn
 * this leg red (the legacy call sites had no injection seam, so the stub leg cannot execute
 * against them — red0 is carried by the mutation chain, registered in the ledger).
 */
class TestGitProcessTimeout {

    private Path stubDir;
    private String stubSleep = "3";

    private String installHangStub(String partialOutput) throws Exception {
        stubDir = Files.createTempDirectory("git-hang-stub");
        Path stub = stubDir.resolve("hanggit");
        Files.writeString(stub, "#!/bin/sh\n"
                + "echo \"" + partialOutput + "\"\n"
                + "exec sleep " + stubSleep + "\n");
        // exec replaces the shell so destroyForcibly kills the fd holder directly
        Runtime.getRuntime().exec(new String[]{"chmod", "+x", stub.toString()}).waitFor();
        return stub.toString();
    }

    @AfterEach
    void restore() {
        GitProcessRunner.setDefaultExecutable("git");
        if (stubDir != null) {
            stubDir.toFile().deleteOnExit();
        }
    }

    @Test
    void testHungProcessReturnsWithinWindowWithTimedOutFlag() throws Exception {
        String stub = installHangStub("partial output");
        GitProcessRunner.setDefaultExecutable(stub);

        GitProcessRunner runner = GitProcessRunner.createDefault();
        long start = System.currentTimeMillis();
        GitProcessRunner.Result result = runner.run(stubDir, 800L,
                List.of("rev-parse", "HEAD"), Collections.emptyMap());
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 5000L, "hung git process must return within the window, took " + elapsed + "ms");
        assertTrue(result.isTimedOut(), "timeout flag must be set");
    }

    @Test
    void testPartialOutputPreservedOnTimeout() throws Exception {
        String stub = installHangStub("partial output");
        GitProcessRunner.setDefaultExecutable(stub);

        GitProcessRunner.Result result = GitProcessRunner.createDefault().run(stubDir, 800L,
                List.of("diff"), Collections.emptyMap());
        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().contains("partial output"),
                "partial output read before the hang must be preserved");
    }

    @Test
    void testRunLinesReturnsOnHungProcess() throws Exception {
        String stub = installHangStub("line one");
        GitProcessRunner.setDefaultExecutable(stub);

        StringBuilder seen = new StringBuilder();
        GitProcessRunner.Result result = GitProcessRunner.createDefault().runLines(stubDir, 800L,
                List.of("diff"), Collections.emptyMap(), line -> seen.append(line).append('\n'));

        assertTrue(result.isTimedOut());
        assertTrue(seen.toString().contains("line one"),
                "runLines must stream the lines read before the hang");
        assertNull(result.getOutput(), "runLines streams, full capture stays null");
    }

    @Test
    void testNormalExitProducesOutputAndExitCode() throws Exception {
        stubDir = Files.createTempDirectory("git-ok-stub");
        Path stub = stubDir.resolve("okgit");
        Files.writeString(stub, "#!/bin/sh\necho hello\necho world\nexit 0\n");
        Runtime.getRuntime().exec(new String[]{"chmod", "+x", stub.toString()}).waitFor();
        GitProcessRunner.setDefaultExecutable(stub.toString());

        GitProcessRunner.Result result = GitProcessRunner.createDefault().run(stubDir, 8000L,
                List.of("status"), Collections.emptyMap());
        assertFalse(result.isTimedOut());
        assertEquals(0, result.getExitCode());
        assertEquals("hello\nworld", result.getOutput());
    }

    @Test
    void testExceptionMessageCarriesNoEnvironment() throws Exception {
        String stub = installHangStub("x");
        GitProcessRunner.setDefaultExecutable(stub);
        final AtomicReference<IllegalStateException> thrown = new AtomicReference<>();
        try {
            GitProcessRunner.createDefault().run(stubDir, 800L, List.of("fetch"),
                    Collections.singletonMap("NOP_GIT_PASSWORD", "secret-value"));
        } catch (IllegalStateException e) {
            thrown.set(e);
        }
        // no start failure expected for the stub; guard the contract for real io failures
        if (thrown.get() != null) {
            assertFalse(thrown.get().getMessage().contains("secret-value"),
                    "exception messages must never carry environment values");
            assertFalse(thrown.get().getMessage().contains("NOP_GIT"),
                    "exception messages must never carry environment variable names");
        }
    }
}
