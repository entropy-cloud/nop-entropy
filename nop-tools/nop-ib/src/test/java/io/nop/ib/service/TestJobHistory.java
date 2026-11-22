package io.nop.ib.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-11-22: terminal job statuses are always queryable — the newest
 * {@link JobHistory#RETAINED} stay in memory regardless of age, and every
 * terminal job is durably appended to {@code <root>/.nop-ib/history.jsonl} so
 * /v1/jobs answers from history even after a service restart.
 */
class TestJobHistory {
    @org.junit.jupiter.api.BeforeAll
    static void isolateLedger() throws Exception {
        // per-test-class ledger so restart tests are deterministic
        System.setProperty("nop.ib.rootsLedger",
                Files.createTempDirectory("ib-roots-ledger").resolve("roots.txt").toString());
    }


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
    void terminalJobsAppendToDurableHistoryAndSurviveRestart() throws Exception {
        Path repo = fixtureProject();
        ServiceCore core = new ServiceCore(4);
        core.cli = (args, engine, root, out, err, budget) -> 0;
        BuildJob a = core.submit(repo.toString(), "status", List.of(), true);
        core.await(a);
        BuildJob b = core.submit(repo.toString(), "status", List.of("--json"), true);
        core.await(b);

        Path file = JobHistory.historyFile(repo.toRealPath());
        assertTrue(Files.isRegularFile(file), "history.jsonl created");
        List<String> lines = Files.readAllLines(file);
        assertEquals(2, lines.size(), "one JSON line per terminal job");

        // a fresh ServiceCore (simulated restart) answers from the file
        ServiceCore rebooted = new ServiceCore(4);
        List<Map<String, Object>> jobs = rebooted.recentJobs(10, null);
        assertEquals(2, jobs.size(), "history survives restart");
        assertEquals(b.id, jobs.get(0).get("id"), "newest first");
        assertEquals(a.id, jobs.get(1).get("id"));
        assertEquals("done", jobs.get(0).get("state"));
        assertNotNull(jobs.get(0).get("durationMs"));
    }

    @Test
    void cancelledQueuedJobIsRecordedInHistory() throws Exception {
        Path repo = fixtureProject();
        ServiceCore core = new ServiceCore(4);
        CountDownLatch gate = new CountDownLatch(1);
        core.cli = (args, engine, root, out, err, budget) -> {
            try {
                gate.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 0;
        };
        BuildJob head = core.submit(repo.toString(), "status", List.of(), false, "h");
        for (int i = 0; i < 200 && head.startedAt == 0; i++)
            Thread.sleep(10);
        BuildJob queued = core.submit(repo.toString(), "status", List.of("--json"), false, "q");
        assertNull(core.registry().cancel(queued), "queued cancel succeeds");
        gate.countDown();
        core.await(head);

        List<Map<String, Object>> jobs = core.recentJobs(10, "cancelled");
        assertEquals(1, jobs.size(), "cancelled job lands in history");
        assertEquals("cancelled", jobs.get(0).get("state"));
        assertEquals(queued.id, jobs.get(0).get("id"));
    }

    @Test
    void readLastSkipsTornLines() throws Exception {
        Path repo = Files.createTempDirectory("ib-history-torn");
        Files.createDirectories(repo.resolve(".nop-ib"));
        Path file = JobHistory.historyFile(repo);
        Files.writeString(file, "{\"id\":\"job-1\",\"state\":\"done\"}\n{\"id\":\"job-2\",to\n");
        List<Map<String, Object>> last = JobHistory.readLast(repo, 10);
        assertEquals(1, last.size());
        assertEquals("job-1", last.get(0).get("id"));
    }
}
