package io.nop.ai.agent.tool;

import io.nop.ai.toolkit.tools.sandbox.BashSandboxConfig;
import io.nop.ai.toolkit.tools.sandbox.BashSandboxRequest;
import io.nop.ai.toolkit.tools.sandbox.HostBashSandbox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2285 (WI4 Phase 2 wiring, Rule #23): the agent-side adapter forwards
 * callId-addressed progress snapshots and cancellation to the SAME
 * IBashSandbox instance the executing BashExecutor holds — same-instance
 * registration must be visible through the adapter.
 */
public class TestBashSandboxProgressTrackerWiring {

    @TempDir
    File tempDir;

    @Test
    void sameInstanceForwardingSeesInFlightExecution() throws Exception {
        HostBashSandbox sandbox = new HostBashSandbox(
                BashSandboxConfig.builder().wallSeconds(30).build(),
                List.of(tempDir.toPath()));
        // the adapter wraps the SAME instance that executes the command
        IToolProgressTracker tracker = new BashSandboxProgressTracker(sandbox);

        BashSandboxRequest request = BashSandboxRequest.builder()
                .command(List.of("sh", "-c", "echo hello; sleep 30"))
                .workingDirectory(tempDir)
                .callId("call-wire-1")
                .config(BashSandboxConfig.builder().wallSeconds(30).build())
                .build();

        CompletableFuture<io.nop.ai.toolkit.tools.sandbox.BashSandboxResult> running =
                CompletableFuture.supplyAsync(() -> sandbox.execute(request));

        ToolProgressSnapshot snapshot = ToolProgressSnapshot.empty();
        for (int i = 0; i < 250; i++) {
            snapshot = tracker.progressSnapshot("call-wire-1");
            if (snapshot.isInFlight() && snapshot.getOutputTail().contains("hello")) {
                break;
            }
            Thread.sleep(20);
        }
        assertTrue(snapshot.isInFlight(), "adapter must observe the same-instance registration");
        assertTrue(snapshot.getOutputTail().contains("hello"));

        assertTrue(tracker.cancelExecution("call-wire-1"));
        running.get(10, TimeUnit.SECONDS);
        assertFalse(tracker.progressSnapshot("call-wire-1").isInFlight());
    }

    @Test
    void noOpTrackerKeepsExplicitEmptySemantics() {
        IToolProgressTracker tracker = NoOpToolProgressTracker.noOp();
        ToolProgressSnapshot snapshot = tracker.progressSnapshot("any");
        assertFalse(snapshot.isInFlight());
        assertEquals("", snapshot.getOutputTail());
        assertFalse(tracker.cancelExecution("any"));
    }
}
