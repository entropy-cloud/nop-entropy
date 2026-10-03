package io.nop.ai.toolkit.tools.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Plan 2285 (WI4 Phase 2): callId-addressed in-flight registry — progress
 * snapshots of a running bash execution (incremental output tail), explicit
 * empty snapshots for unknown callIds, and abandon-time process cancellation
 * (design nop-ai-agent-async-tool-execution.md §三.6 进度证据接缝).
 */
public class TestBashExecutionProgress {

    @TempDir
    File tempDir;

    private static void assumeShAvailable() {
        try {
            new ProcessBuilder("sh", "-c", "true").start().waitFor(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("sh not available");
        }
    }

    @Test
    void unknownCallIdReturnsExplicitEmptySnapshot() {
        BashExecutionTracker tracker = new BashExecutionTracker();
        BashProgressSnapshot snapshot = tracker.progressSnapshot("no-such-call");
        assertFalse(snapshot.isInFlight());
        assertEquals("", snapshot.getOutputTail());
        assertEquals(0L, snapshot.getOutputBytes());
        assertFalse(tracker.cancelExecution("no-such-call"));
    }

    @Test
    void runningExecutionExposesIncrementalOutputTail() throws Exception {
        assumeShAvailable();
        HostBashSandbox sandbox = new HostBashSandbox(
                BashSandboxConfig.builder().wallSeconds(30).build(),
                List.of(tempDir.toPath()));
        BashSandboxRequest request = BashSandboxRequest.builder()
                .command(List.of("sh", "-c", "echo line-1; sleep 30"))
                .workingDirectory(tempDir)
                .callId("call-progress-1")
                .build();

        CompletableFuture<BashSandboxResult> running = CompletableFuture.supplyAsync(
                () -> sandbox.execute(request));

        // poll until the reader thread has captured the first line (bounded):
        // inFlight alone flips immediately at execute entry — wait for output
        BashProgressSnapshot snapshot = BashProgressSnapshot.empty();
        for (int i = 0; i < 250; i++) {
            snapshot = sandbox.progressSnapshot("call-progress-1");
            if (snapshot.isInFlight() && snapshot.getOutputTail().contains("line-1")) {
                break;
            }
            Thread.sleep(20);
        }
        assertTrue(snapshot.isInFlight(), "running execution must be in flight");
        assertTrue(snapshot.getOutputTail().contains("line-1"),
                "incremental tail carries the first line: " + snapshot.getOutputTail());
        assertTrue(snapshot.getOutputBytes() >= "line-1".length());

        // abandon: cancel kills the process; the blocked execute returns with
        // a non-zero exit code (external kill semantics — not the wall-time
        // timeout flag); the agent-side abandon adjudication inserts its own
        // synthetic cancel result regardless of this tool result
        assertTrue(sandbox.cancelExecution("call-progress-1"));
        BashSandboxResult result = running.get(10, TimeUnit.SECONDS);
        assertFalse(result.isTimedOut(), "external kill is not the wall-time timeout path");
        assertTrue(result.getExitCode() != 0, "killed process exits non-zero: " + result.getExitCode());

        // after completion the registration is gone — explicit empty again
        assertFalse(sandbox.progressSnapshot("call-progress-1").isInFlight());
    }

    @Test
    void finishedExecutionUnregisteredAndUnsnapshotable() throws Exception {
        assumeShAvailable();
        HostBashSandbox sandbox = new HostBashSandbox(
                BashSandboxConfig.builder().wallSeconds(10).build(),
                List.of(tempDir.toPath()));
        BashSandboxRequest request = BashSandboxRequest.builder()
                .command(List.of("sh", "-c", "true"))
                .workingDirectory(tempDir)
                .callId("call-finished-1")
                .build();

        sandbox.execute(request);

        assertFalse(sandbox.progressSnapshot("call-finished-1").isInFlight(),
                "finished execution must be unregistered");
        assertFalse(sandbox.cancelExecution("call-finished-1"),
                "finished execution has nothing to cancel");
    }

    @Test
    void nullCallIdRequestKeepsLegacyUntrackedBehaviour() throws Exception {
        assumeShAvailable();
        HostBashSandbox sandbox = new HostBashSandbox(
                BashSandboxConfig.builder().wallSeconds(5).build(),
                List.of(tempDir.toPath()));
        BashSandboxRequest request = BashSandboxRequest.builder()
                .command(List.of("sh", "-c", "true"))
                .workingDirectory(tempDir)
                .build();

        BashSandboxResult result = sandbox.execute(request);
        assertEquals(0, result.getExitCode());
        assertFalse(sandbox.progressSnapshot(null).isInFlight());
    }
}
