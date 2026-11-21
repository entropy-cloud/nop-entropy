package io.nop.ib.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2588 pins: idempotent submit (clientRequestId), long-poll intermediate
 * state (phase/queueAhead/waitedMs), the durable per-job log tee with stream
 * markers, progress publication, log cleanup at prune time and the RUNNING
 * cancel path (process-tree kill + CANCELLED with exit=-1 + log record).
 */
class TestAsyncJobApi {

    private Path fixtureProject() throws Exception {
        Path repo = Files.createTempDirectory("ib-async");
        Files.createDirectories(repo.resolve(".nop-ib"));
        Files.writeString(repo.resolve("pom.xml"), "<project/>");
        Map<String, Object> model = new java.util.LinkedHashMap<>();
        model.put("formatVersion", "1");
        model.put("repoRoot", repo.toString());
        model.put("pomHashes", new java.util.LinkedHashMap<>());
        model.put("modules", List.of());
        Files.writeString(repo.resolve(".nop-ib/model.json"),
                io.nop.ib.json.JsonOut.write(model));
        Process p = new ProcessBuilder("git", "init", "-q").directory(repo.toFile()).start();
        p.waitFor();
        new ProcessBuilder("git", "config", "user.email", "t@t").directory(repo.toFile()).start().waitFor();
        new ProcessBuilder("git", "config", "user.name", "t").directory(repo.toFile()).start().waitFor();
        new ProcessBuilder("git", "add", "-A").directory(repo.toFile()).start().waitFor();
        new ProcessBuilder("git", "commit", "-qm", "init").directory(repo.toFile()).start().waitFor();
        return repo;
    }

    /** idempotent submit: same (root, clientRequestId) returns the SAME job, no second enqueue. */
    @Test
    void idempotentSubmitDeduplicates() throws Exception {
        Path repo = fixtureProject();
        ServiceCore core = new ServiceCore(4);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        core.cli = (args, engine, root, out, err, budget) -> {
            started.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 0;
        };
        BuildJob first = core.submit(repo.toString(), "status", List.of(), false, "retry-1");
        assertTrue(started.await(30, TimeUnit.SECONDS), "job starts");
        BuildJob replay = core.submit(repo.toString(), "status", List.of(), false, "retry-1");
        assertEquals(first.id, replay.id, "same clientRequestId returns the existing job id");
        assertTrue(replay.replayed, "replay response is flagged");
        assertEquals(1, core.registry().ofRoot(repo.toRealPath()).size(),
                "no second job was enqueued for the same idempotency key");
        // a different key or root creates a normal (new) job
        BuildJob other = core.submit(repo.toString(), "status", List.of(), false, "retry-2");
        assertTrue(other.replayed == false, "fresh key is not a replay");
        release.countDown();
        core.await(first);
    }

    /** long-poll: a queued job answers the explicit intermediate state after waitMs. */
    @Test
    void longPollReturnsQueuedIntermediateState() throws Exception {
        Path root = Path.of("/fake/longpoll");
        ProjectQueue lane = new ProjectQueue();
        ServiceCore core = new ServiceCore(4, lane);
        JobRegistry registry = core.registry();
        CountDownLatch gate = new CountDownLatch(1);
        BuildJob head = registry.create("p", "status", root, List.of(), false);
        assertTrue(lane.submit(head, () -> {
            // mimic ServiceCore.execute: the lane wrapper only sets runningCmd,
            // the RUNNING transition happens inside the execution body
            head.state = BuildJob.State.RUNNING;
            try {
                gate.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        BuildJob queued = registry.create("p", "test", root, List.of(), true);
        assertTrue(lane.submit(queued, () -> { }));
        BuildJob queued2 = registry.create("p", "test", root, List.of(), true);
        assertTrue(lane.submit(queued2, () -> { }));
        // the head must be RUNNING (not still QUEUED) when we snapshot the tail
        for (int i = 0; i < 200 && lane.runningCmd(root) == null; i++)
            Thread.sleep(10);
        assertEquals("status", lane.runningCmd(root), "head job started");

        long t0 = System.currentTimeMillis();
        core.waitForChange(queued2, 800);
        long waited = System.currentTimeMillis() - t0;
        assertTrue(waited >= 700, "waitMs is honored, waited " + waited + "ms");
        Map<String, Object> snap = core.pendingSnapshot(queued2, waited);
        assertEquals(queued2.id, snap.get("id"));
        assertEquals("queued", snap.get("state"));
        assertEquals("queued", snap.get("phase"), "explicit queued phase, never a reused failed");
        assertEquals(1, ((Number) snap.get("queueAhead")).intValue(),
                "the queued job ahead counts as queueAhead (the RUNNING head does not)");
        assertTrue(((Number) snap.get("waitedMs")).longValue() >= 700);
        gate.countDown();
    }

    /**
     * progress published by the CLI thread is visible ONLY to the running job's
     * snapshot: ProgressRegistry is keyed by repoRoot (lane-wide), so a queued
     * job surfacing it would read as "my build is already executing" — the
     * 2026-11-22 live-audit misjudgment. Queued snapshots carry queueWaitMs and
     * the lane context (queueAheadIds/runningJobId) instead.
     */
    @Test
    void progressVisibleOnlyToRunningJob() throws Exception {
        Path root = Path.of("/fake/progress");
        ProjectQueue lane = new ProjectQueue();
        ServiceCore core = new ServiceCore(4, lane);
        JobRegistry registry = core.registry();
        CountDownLatch gate = new CountDownLatch(1);
        BuildJob head = registry.create("p", "status", root, List.of(), false);
        assertTrue(lane.submit(head, () -> {
            try {
                gate.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        BuildJob job = registry.create("p", "test", root, List.of(), true);
        assertTrue(lane.submit(job, () -> { }));
        // mirror the production execute() transition (ServiceCore.execute is the
        // QUEUED→RUNNING point; the test bypasses it via a raw lane runnable)
        head.state = BuildJob.State.RUNNING;
        head.startedAt = System.currentTimeMillis();
        // the CLI thread (TestCommand/CompileCommand) publishes under the ROOT key
        ProgressRegistry.update(root, Map.of("stage", "test", "modulesTotal", 7, "modulesDone", 3));

        Map<String, Object> queuedSnap = core.pendingSnapshot(job, 0);
        assertEquals("queued", queuedSnap.get("phase"));
        assertNull(queuedSnap.get("progress"), "queued job must not see lane-wide progress");
        assertNull(queuedSnap.get("logTail"), "queued job must not see another job's log tail");
        assertNotNull(queuedSnap.get("queueWaitMs"), "queued wait time is explicit");
        assertEquals("test", queuedSnap.get("cmd"));
        assertEquals(head.id, queuedSnap.get("runningJobId"), "lane head is identified");

        Map<String, Object> runningSnap = core.pendingSnapshot(head, 0);
        assertEquals("running", runningSnap.get("phase"));
        assertNotNull(runningSnap.get("progress"), "running job sees its own progress");
        assertEquals(7, ((Number) ((Map<?, ?>) runningSnap.get("progress")).get("modulesTotal")).intValue());
        gate.countDown();
    }

    /** job log: stdout/stderr tee with markers, logPath in the snapshot, prune deletes the file. */
    @Test
    void jobLogTeeMarkersAndPruneCleanup() throws Exception {
        Path repo = fixtureProject();
        ServiceCore core = new ServiceCore(4);
        core.cli = (args, engine, root, out, err, budget) -> {
            out.println("[nop-ib] hello stdout marker");
            err.println("hello stderr marker");
            return 0;
        };
        BuildJob job = core.submit(repo.toString(), "status", List.of(), true);
        core.await(job);
        assertNotNull(job.logFile, "the job carries its log path");
        assertEquals(ServiceCore.jobLogFile(repo.toRealPath(), job.id), job.logFile);
        String log = Files.readString(job.logFile);
        assertTrue(log.contains("[out] [nop-ib] hello stdout marker"),
                "stdout is tee'd with the [out] marker, got: " + log);
        assertTrue(log.contains("[err] hello stderr marker"),
                "stderr is tee'd with the [err] marker");
        Map<String, Object> snap = job.snapshot();
        assertEquals(job.logFile.toString(), snap.get("logPath"), "logPath is in the snapshot");

        // prune after the retention window deletes the job AND its log file
        job.finishedAt = System.currentTimeMillis() - 11 * 60_000L;
        core.registry().prune();
        assertNull(core.job(job.id), "terminal job forgotten");
        assertFalse(Files.exists(job.logFile), "prune deletes the log file");
    }

    /**
     * RUNNING cancel: the registered child process tree dies (grand-children
     * included — Java destroyForcibly alone only kills the direct child), the
     * job reaches CANCELLED with exit=-1 and the log records the cancel point.
     */
    @Test
    void cancelRunningKillsProcessTree() throws Exception {
        Path repo = fixtureProject();
        ServiceCore core = new ServiceCore(4);
        AtomicReference<Process> child = new AtomicReference<>();
        CountDownLatch registered = new CountDownLatch(1);
        core.cli = (args, engine, root, out, err, budget) -> {
            // bash keeps a grand-child (sleep) alive: the tree-kill must reach it.
            // The sleep pid goes to a file — once bash dies the sleep is re-parented
            // and p.descendants() no longer lists it, so the assertion needs the pid.
            Path pidFile = root.resolve(".nop-ib").resolve("probe-sleep.pid");
            Process p;
            try {
                p = new ProcessBuilder("bash", "-c",
                        "sleep 30 & echo $! > " + pidFile + "; wait")
                        .directory(root.toFile()).start();
            } catch (java.io.IOException e) {
                return 1;
            }
            child.set(p);
            ProcessTracker.register(root, p);
            registered.countDown();
            try {
                return p.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 130;
            }
        };
        BuildJob job = core.submit(repo.toString(), "status", List.of(), false);
        assertTrue(registered.await(30, TimeUnit.SECONDS), "child process spawned");
        // wait until the grand-child sleep exists and its pid is recorded
        Path pidFile = repo.toRealPath().resolve(".nop-ib").resolve("probe-sleep.pid");
        long sleepPid = -1;
        for (int i = 0; i < 100 && sleepPid < 0; i++) {
            if (Files.exists(pidFile))
                sleepPid = Long.parseLong(Files.readString(pidFile).trim());
            else
                Thread.sleep(20);
        }
        assertTrue(sleepPid > 0, "grand-child pid recorded");
        assertEquals("cancelled", core.cancel(job.id), "RUNNING cancel is accepted");
        assertEquals(BuildJob.State.CANCELLED, job.state);
        assertEquals(-1, job.exit, "cancelled job reports exit=-1");
        Process p = child.get();
        for (int i = 0; i < 100 && p.isAlive(); i++)
            Thread.sleep(50);
        assertFalse(p.isAlive(), "direct child killed");
        // the re-parented grand-child must be dead too (tree termination):
        // an empty Optional means the process is gone
        java.util.Optional<ProcessHandle> sleep = ProcessHandle.of(sleepPid);
        assertTrue(sleep.isEmpty() || !sleep.get().isAlive(),
                "grand-children (surefire-parity tree) must be killed too, pid " + sleepPid);
        String log = Files.readString(job.logFile);
        assertTrue(log.contains("cancelled at phase=running"),
                "the cancel point is recorded in the job log: " + log);
    }

    /** QUEUED cancel semantics unchanged (plan 2583 pin kept over 2588 rework). */
    @Test
    void cancelOfQueuedJobWakesAwaiters() throws Exception {
        Path root = Path.of("/fake/cancel-wake");
        ProjectQueue lane = new ProjectQueue();
        ServiceCore core = new ServiceCore(4, lane);
        JobRegistry registry = core.registry();
        Object gate = new Object();
        BuildJob head = registry.create("p", "status", root, List.of(), false);
        assertTrue(lane.submit(head, () -> {
            try {
                synchronized (gate) {
                    gate.wait();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }));
        BuildJob queued = registry.create("p", "test", root, List.of(), true);
        assertTrue(lane.submit(queued, () -> { }));
        long t0 = System.currentTimeMillis();
        assertNull(core.cancel(queued.id), "queued job cancels");
        core.await(queued);
        assertTrue(System.currentTimeMillis() - t0 < 5_000,
                "await must return promptly on cancel (notifyAll)");
        synchronized (gate) {
            gate.notifyAll();
        }
    }
}
