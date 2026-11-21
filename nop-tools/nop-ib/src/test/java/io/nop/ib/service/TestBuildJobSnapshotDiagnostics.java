package io.nop.ib.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the diagnosis surface of GET /v1/job/{id} for a RUNNING job: a job
 * that reports nothing is indistinguishable from a hang, so the snapshot must
 * carry elapsedMs and the durable log tail without waiting for the terminal
 * payload.
 */
class TestBuildJobSnapshotDiagnostics {

    @TempDir
    Path dir;

    private BuildJob job() {
        return new BuildJob("job-7", "/tmp/p", "check", dir, null, false);
    }

    @Test
    void runningSnapshotCarriesLogTailAndElapsed() throws Exception {
        BuildJob job = job();
        job.logFile = dir.resolve("job-7.log");
        Files.writeString(job.logFile, "line1\nline2\nline3\n");
        job.state = BuildJob.State.RUNNING;
        job.startedAt = System.currentTimeMillis() - 5_000;

        Map<String, Object> snap = job.snapshot();
        assertEquals("running", snap.get("state"));
        assertTrue(((Number) snap.get("elapsedMs")).longValue() >= 4_000);
        @SuppressWarnings("unchecked")
        List<String> tail = (List<String>) snap.get("logTail");
        assertEquals(List.of("line1", "line2", "line3"), tail);
    }

    @Test
    void missingLogFileYieldsNoTailKey() {
        BuildJob job = job();
        job.logFile = dir.resolve("absent.log");
        job.state = BuildJob.State.QUEUED;
        Map<String, Object> snap = job.snapshot();
        assertFalse(snap.containsKey("logTail"));
    }

    @Test
    void terminalSnapshotKeepsPayloadContractAndNoTail() throws Exception {
        BuildJob job = job();
        job.logFile = dir.resolve("job-7.log");
        Files.writeString(job.logFile, "old\n");
        job.state = BuildJob.State.FAILED;
        job.exit = 1;
        job.payload = "{\"ok\":false}";
        Map<String, Object> snap = job.snapshot();
        assertEquals(1, snap.get("exit"));
        assertEquals("{\"ok\":false}", snap.get("payload"));
        assertFalse(snap.containsKey("logTail"), "terminal snapshots carry payload, not tail");
    }

    @Test
    void logTailBoundsAndMissingFile() throws Exception {
        Path f = dir.resolve("big.log");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++)
            sb.append("l").append(i).append('\n');
        Files.writeString(f, sb.toString());
        List<String> tail = BuildJob.logTail(f, 10);
        assertEquals(10, tail.size());
        assertEquals("l40", tail.get(0));
        assertEquals("l49", tail.get(9));
        assertTrue(BuildJob.logTail(dir.resolve("nope"), 5).isEmpty());
    }
}
