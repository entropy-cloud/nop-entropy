package io.nop.ib.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-11-22: terminal job statuses stay queryable in MEMORY — the newest
 * {@link JobRegistry#RETAINED} summaries survive registry pruning, and a
 * cancelled-while-queued job records its history line with correct duration
 * semantics (no disk state: user adjudication, memory-only).
 */
class TestTerminalHistory {

    private Path fixtureProject() throws Exception {
        Path repo = Files.createTempDirectory("ib-history");
        Files.createDirectories(repo.resolve(".nop-ib"));
        Files.writeString(repo.resolve("pom.xml"), "<project/>");
        Process p = new ProcessBuilder("git", "init", "-q").directory(repo.toFile()).start();
        p.waitFor();
        new ProcessBuilder("git", "config", "user.email", "t@t").directory(repo.toFile()).start().waitFor();
        new ProcessBuilder("git", "config", "user.name", "t").directory(repo.toFile()).start().waitFor();
        new ProcessBuilder("git", "add", "-A").directory(repo.toFile()).start().waitFor();
        new ProcessBuilder("git", "commit", "-qm", "init").directory(repo.toFile()).start().waitFor();
        return repo;
    }

    @Test
    void terminalJobsAreRecordedAndNewestFirst() throws Exception {
        Path repo = fixtureProject();
        ServiceCore core = new ServiceCore(4);
        core.cli = (args, engine, root, out, err, budget) -> 0;
        BuildJob a = core.submit(repo.toString(), "status", List.of(), true);
        core.await(a);
        BuildJob b = core.submit(repo.toString(), "status", List.of("--json"), true);
        core.await(b);

        List<Map<String, Object>> jobs = core.recentJobs(10, null);
        assertEquals(2, jobs.size());
        assertEquals(b.id, jobs.get(0).get("id"), "newest first");
        assertEquals("done", jobs.get(0).get("state"));
        assertNotNull(jobs.get(0).get("durationMs"));
    }

    /** history survives registry pruning of the live job (cap keeps newest 100). */
    @Test
    void historySurvivesPrune() throws Exception {
        Path repo = fixtureProject();
        ServiceCore core = new ServiceCore(4);
        core.cli = (args, engine, root, out, err, budget) -> 0;
        BuildJob job = core.submit(repo.toString(), "status", List.of(), true);
        core.await(job);
        job.finishedAt = System.currentTimeMillis() - 11 * 60_000L;
        core.registry().prune();
        List<Map<String, Object>> jobs = core.recentJobs(10, null);
        assertEquals(1, jobs.size(), "history entry outlives the pruned live job");
        assertEquals(job.id, jobs.get(0).get("id"));
    }

    @Test
    void cancelledQueuedJobRecordsZeroDurationAndQueueWait() throws Exception {
        Path repo = fixtureProject();
        ServiceCore core = new ServiceCore(4);
        BuildJob job = registryLevelCancel(core, repo);
        List<Map<String, Object>> jobs = core.recentJobs(10, "cancelled");
        assertEquals(1, jobs.size());
        assertEquals(0, ((Number) jobs.get(0).get("durationMs")).intValue(),
                "never-started job has zero run duration");
        // the history record itself carries the queue wait timing
        assertTrue(((Number) core.registry().terminalHistory().get(0).get("queueWaitMs")).longValue() >= 0);
    }

    private BuildJob registryLevelCancel(ServiceCore core, Path repo) throws Exception {
        BuildJob queued = core.registry().create(repo.toString(), "status", repo.toRealPath(),
                List.of(), false, null);
        assertNull(core.registry().cancel(queued));
        return queued;
    }
}
