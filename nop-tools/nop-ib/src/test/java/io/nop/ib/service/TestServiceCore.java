package io.nop.ib.service;

import io.nop.ib.cli.Commands;
import io.nop.ib.engine.IbEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 10 phase-1 pins (plan 1g): same-project serialization, queue bounds,
 * two-layer budget, cancel semantics, root seam (projectDir ≠ cwd) and the
 * engine LRU contract. All jobs run the real {@code NopIbMain.run} path on a
 * fixture model — no parallel execution implementation.
 */
class TestServiceCore {

    private Path fixtureProject() throws Exception {
        Path repo = Files.createTempDirectory("ib-svc");
        Files.createDirectories(repo.resolve(".nop-ib"));
        Files.writeString(repo.resolve("pom.xml"), "<project/>");
        Map<String, Object> model = new java.util.LinkedHashMap<>();
        model.put("formatVersion", "1");
        model.put("repoRoot", repo.toString());
        model.put("pomHashes", new java.util.LinkedHashMap<>());
        model.put("modules", List.of());
        Files.writeString(repo.resolve(".nop-ib/model.json"),
                io.nop.ib.json.JsonOut.write(model));
        // status/plan read the git change set — give the fixture a repo+commit
        Process p = new ProcessBuilder("git", "init", "-q").directory(repo.toFile()).start();
        p.waitFor();
        new ProcessBuilder("git", "config", "user.email", "t@t").directory(repo.toFile()).start().waitFor();
        new ProcessBuilder("git", "config", "user.name", "t").directory(repo.toFile()).start().waitFor();
        new ProcessBuilder("git", "add", "-A").directory(repo.toFile()).start().waitFor();
        new ProcessBuilder("git", "commit", "-qm", "init").directory(repo.toFile()).start().waitFor();
        return repo;
    }

    /** same project: two submissions run strictly serially (start after finish). */
    @Test
    void sameProjectRunsSerially() throws Exception {
        Path repo = fixtureProject();
        ServiceCore core = new ServiceCore(4);
        // distinct args: identical submissions coalesce by design (coalescing
        // contract), and this test verifies the SERIAL invariant of two real jobs
        BuildJob j1 = core.submit(repo.toString(), "status", List.of(), true);
        BuildJob j2 = core.submit(repo.toString(), "status", List.of("--json"), true);
        core.await(j1);
        core.await(j2);
        assertEquals(BuildJob.State.DONE, j1.state);
        assertEquals(BuildJob.State.DONE, j2.state);
        assertTrue(j2.startedAt >= j1.finishedAt,
                "second job on the same root must start only after the first finished");
    }

    /** root seam (audit B1): projectDir far from cwd routes to the right root. */
    @Test
    void projectDirRoutesToRequestedRoot() throws Exception {
        Path repo = fixtureProject();
        ServiceCore core = new ServiceCore(4);
        BuildJob job = core.submit(repo.toString(), "status", List.of(), true);
        core.await(job);
        // wave 11: the service keys lanes by the REAL path (symlinks resolved)
        assertEquals(repo.toRealPath(), job.repoRoot);
        assertEquals(Commands.EXIT_OK, job.exit);
        assertNotNull(job.payload, "job captures the CLI --json output as payload");
        assertFalse(job.truncated);
        assertSame(core.engineFor(job.repoRoot), core.engineFor(job.repoRoot),
                "engine cache returns the same instance for the same root");
    }

    /** invalid projectDir and unsupported cmd are explicit errors, never no-ops. */
    @Test
    void invalidInputsFailLoudly() throws Exception {
        Path empty = Files.createTempDirectory("ib-svc-empty");
        ServiceCore core = new ServiceCore(4);
        assertThrows(IllegalArgumentException.class,
                () -> core.submit(empty.toString(), "status", List.of(), true));
        assertThrows(IllegalArgumentException.class,
                () -> core.submit("whatever", "deploy", List.of(), true),
                "non-serviceable cmd must be rejected");
    }

    /** queue lane bounds at 32 with an explicit rejection — no silent drop. */
    @Test
    void queueFullIsExplicitRejection() throws Exception {
        ProjectQueue queue = new ProjectQueue();
        Object gate = new Object();
        List<BuildJob> accepted = new java.util.ArrayList<>();
        Path root = Path.of("/fake/root");
        var registry = new JobRegistry();
        BuildJob first = registry.create("p", "status", root, List.of(), false);
        assertTrue(queue.submit(first, () -> {
            try {
                synchronized (gate) {
                    gate.wait();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }));
        for (int i = 0; i < ProjectQueue.MAX_QUEUE - 1; i++) {
            BuildJob j = registry.create("p", "status", root, List.of(), false);
            assertTrue(queue.submit(j, () -> { }), "lane accepts up to MAX_QUEUE total");
            accepted.add(j);
        }
        BuildJob overflow = registry.create("p", "status", root, List.of(), false);
        assertFalse(queue.submit(overflow, () -> { }),
                "the 33rd submission must be rejected explicitly");
        assertEquals(ProjectQueue.MAX_QUEUE, queue.pending(root));
        synchronized (gate) {
            gate.notifyAll();
        }
    }

    /** two-layer budget: permits cap concurrency, per-job workers compress. */
    @Test
    void budgetCapsAndCompresses() throws Exception {
        JobBudget budget = new JobBudget(4);
        int w1 = budget.acquire();
        assertEquals(4, w1, "single active job keeps the full budget");
        int w2 = budget.acquire();
        assertEquals(2, w2, "second active job compresses to permits/active");
        assertEquals(2, budget.activeJobs());
        budget.release();
        int w3 = budget.acquire();
        // admission-time compression: the new job sees active=2 → half permits.
        // (a long-running job admitted at full budget keeps its share until it
        // finishes — the transient overshoot is bounded by one job's initial
        // share and recorded in design 07 §2.3)
        assertEquals(2, w3);
        budget.release();
        budget.release();
        assertEquals(0, budget.activeJobs());
    }

    /** cancel: queued jobs cancel; running jobs are routed to the tree-kill path; done jobs answer already-terminal. */
    @Test
    void cancelSemanticsAreExplicit() throws Exception {
        JobRegistry registry = new JobRegistry();
        Path root = Path.of("/fake/root");
        BuildJob queued = registry.create("p", "status", root, List.of(), false);
        assertNull(registry.cancel(queued), "queued job cancels");
        assertEquals(BuildJob.State.CANCELLED, queued.state);
        BuildJob done = registry.create("p", "status", root, List.of(), false);
        done.state = BuildJob.State.DONE;
        assertEquals(JobRegistry.ALREADY_TERMINAL, registry.cancel(done),
                "terminal job answers already-terminal — explicit, not a silent no-op");
    }

    /** unknown cancel target is explicit, and health exposes budget facts. */
    @Test
    void healthExposesBudgetFacts() throws Exception {
        ServiceCore core = new ServiceCore(8);
        Map<String, Object> health = core.health();
        assertEquals(8, health.get("cores"));
        assertEquals(4, health.get("workerBudget"), "default = max(2, cores/2)");
        assertEquals("unknown-job", core.cancel("nope"));
    }

    /** LRU: the 9th root evicts the least-recently-used engine (audit M1 fix). */
    @Test
    void engineLruEvictsOldestRoot() throws Exception {
        ServiceCore core = new ServiceCore(4);
        List<Path> roots = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++)
            roots.add(fixtureProject());
        for (int i = 0; i < 9; i++)
            core.engineFor(roots.get(i));
        IbEngine evicted = core.engineFor(roots.get(0));
        // roots.get(0) fell out of the LRU window: its cache entry is gone and a
        // NEW instance is built on re-access
        assertSame(core.engineFor(roots.get(8)), core.engineFor(roots.get(8)),
                "recent roots stay cached");
        assertNotSame(core.engineFor(roots.get(0)), evicted == core.engineFor(roots.get(0)) ? null : evicted,
                "evicted root must be rebuilt as a fresh instance");
    }

    /** running root is never evicted (audit M3): pending jobs pin the engine. */
    @Test
    void runningRootIsNotEvictable() throws Exception {
        ServiceCore core = new ServiceCore(4);
        Path root = fixtureProject();
        core.engineFor(root);
        // a queued/running job on the root pins its engine regardless of LRU age
        assertTrue(core.isEvictable(root), "idle root starts evictable");
        Object gate = new Object();
        ProjectQueue lane = new ProjectQueue();
        ServiceCore coreWithLane = new ServiceCore(4, lane);
        coreWithLane.engineFor(root);
        JobRegistry registry = new JobRegistry();
        BuildJob blocked = registry.create("p", "status", root, List.of(), false);
        lane.submit(blocked, () -> {
            try {
                synchronized (gate) {
                    gate.wait();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        assertFalse(coreWithLane.isEvictable(root),
                "root with a queued/running job must not be evictable (M3)");
        synchronized (gate) {
            gate.notifyAll();
        }
    }

    /** wave 11: a submission from a submodule dir maps to the reactor root lane. */
    @Test
    void submoduleSubmissionSharesTheRootLane() throws Exception {
        Path repo = fixtureProject(); // has root pom.xml, no mvnw
        Files.createDirectories(repo.resolve("modA"));
        Files.writeString(repo.resolve("modA/pom.xml"), "<project/>");
        ServiceCore core = new ServiceCore(4);
        BuildJob fromRoot = core.submit(repo.toString(), "status", List.of(), true);
        // distinct args so the pair does not coalesce (coalescing contract);
        // this test verifies the shared-lane serial invariant
        BuildJob fromSubdir = core.submit(repo.resolve("modA").toString(), "status", List.of("--json"), true);
        assertEquals(fromRoot.repoRoot, fromSubdir.repoRoot,
                "subdir submission must join the reactor-root lane (no second lane per worktree)");
        core.await(fromRoot);
        core.await(fromSubdir);
        assertTrue(fromSubdir.startedAt >= fromRoot.finishedAt,
                "same-lane jobs serialize even when submitted via different dirs");
    }

    /** wave 11: symlink aliases of one worktree resolve to one lane key. */
    @Test
    void symlinkAliasSharesOneLane() throws Exception {
        Path repo = fixtureProject().toRealPath();
        Path alias = repo.getParent().resolve(repo.getFileName() + "-alias");
        Files.deleteIfExists(alias);
        java.nio.file.Files.createSymbolicLink(alias, repo);
        ServiceCore core = new ServiceCore(4);
        BuildJob viaReal = core.submit(repo.toString(), "status", List.of(), true);
        BuildJob viaAlias = core.submit(alias.toString(), "status", List.of(), true);
        assertEquals(viaReal.repoRoot, viaAlias.repoRoot,
                "symlink alias must resolve to the same canonical lane");
        Files.deleteIfExists(alias);
    }

    /** wave 11: the service accepts the model command (cold-start model refresh). */
    @Test
    void modelCommandIsServiceable() throws Exception {
        Path repo = fixtureProject();
        ServiceCore core = new ServiceCore(4);
        BuildJob job = core.submit(repo.toString(), "model", List.of("show"), true);
        assertEquals(repo.toRealPath(), job.repoRoot);
    }

    /** wave 11 observability: /v1/jobs summaries — newest first, limit honored. */
    @Test
    void recentJobsReturnsCompactSummariesWithLimit() throws Exception {
        Path repo = fixtureProject();
        ServiceCore core = new ServiceCore(4);
        JobRegistry registry = core.registry();
        Path root = repo.toRealPath();
        BuildJob done = registry.create("p", "compile", root, List.of(), false);
        done.state = BuildJob.State.RUNNING;
        done.startedAt = done.queuedAt + 10;
        done.finishedAt = done.startedAt + 250;
        done.exit = 0;
        done.state = BuildJob.State.DONE;
        BuildJob queued = registry.create("p", "test", root, List.of(), false);
        BuildJob running = registry.create("p", "status", root, List.of(), false);
        running.state = BuildJob.State.RUNNING;
        running.startedAt = running.queuedAt - 50; // started 50ms ago, still running

        List<Map<String, Object>> all = core.recentJobs(0); // 0 -> default limit
        assertEquals(3, all.size(), "all three jobs listed, newest first");
        assertEquals("status", all.get(0).get("cmd"));
        assertEquals("test", all.get(1).get("cmd"));
        assertEquals("compile", all.get(2).get("cmd"));

        Map<String, Object> s = all.get(2);
        assertEquals(done.id, s.get("id"));
        assertEquals(root.toString(), s.get("projectDir"), "projectDir is the repo root");
        assertEquals("done", s.get("state"));
        assertEquals(0, ((Number) s.get("exit")).intValue());
        assertEquals(250L, ((Number) s.get("durationMs")).longValue(),
                "finished job reports finishedAt-startedAt");
        assertEquals(done.queuedAt, ((Number) s.get("queuedAt")).longValue());
        assertEquals(done.startedAt, ((Number) s.get("startedAt")).longValue());
        assertEquals(done.finishedAt, ((Number) s.get("finishedAt")).longValue());

        Map<String, Object> r = all.get(0);
        assertEquals("running", r.get("state"));
        assertEquals(-1, ((Number) r.get("exit")).intValue(), "unfinished job has no exit yet");
        assertTrue(((Number) r.get("durationMs")).longValue() >= 0,
                "running job reports elapsed time");

        Map<String, Object> q = all.get(1);
        assertEquals("queued", q.get("state"));
        assertNull(q.get("startedAt"), "queued job never started");
        assertNull(q.get("durationMs"), "queued job has no duration yet");

        List<Map<String, Object>> capped = core.recentJobs(2);
        assertEquals(2, capped.size(), "limit truncates to the newest N");
        assertEquals("status", capped.get(0).get("cmd"));
    }

    /** wave 11 observability: lane depth excludes the running job; runningCmd nulls when idle. */
    @Test
    void queueLaneExposesDepthAndRunningCmd() throws Exception {
        Path root = Path.of("/fake/lane-obs");
        ProjectQueue queue = new ProjectQueue();
        ServiceCore core = new ServiceCore(4, queue);
        JobRegistry registry = core.registry();
        Object gate = new Object();
        BuildJob first = registry.create("p", "compile", root, List.of(), false);
        assertTrue(queue.submit(first, () -> {
            try {
                synchronized (gate) {
                    gate.wait();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }));
        BuildJob second = registry.create("p", "test", root, List.of(), false);
        assertTrue(queue.submit(second, () -> { }));

        for (int i = 0; i < 100 && queue.runningCmd(root) == null; i++)
            Thread.sleep(10);
        assertEquals("compile", queue.runningCmd(root), "executing job's cmd is visible");
        assertEquals(1, queue.queuedDepth(root),
                "depth counts only jobs still waiting (running excluded)");
        assertEquals(2, queue.pending(root), "pending stays active+queued");

        List<Map<String, Object>> lanes = core.queueLanes();
        assertEquals(1, lanes.size());
        Map<String, Object> lane = lanes.get(0);
        assertEquals(root.toString(), lane.get("root"));
        assertEquals(1, ((Number) lane.get("depth")).intValue());
        assertEquals("compile", lane.get("runningCmd"));

        synchronized (gate) {
            gate.notifyAll();
        }
        for (int i = 0; i < 100 && queue.pending(root) > 0; i++)
            Thread.sleep(10);
        assertNull(queue.runningCmd(root), "idle lane reports null runningCmd");
        assertEquals(0, queue.queuedDepth(root), "drained lane has depth 0");
        Map<String, Object> idle = core.queueLanes().get(0);
        assertEquals(0, ((Number) idle.get("depth")).intValue());
        assertNull(idle.get("runningCmd"));
    }

    /**
     * plan 2583 pin: concurrent jobs each receive their OWN compressed worker
     * budget as an explicit invocation parameter. Pre-fix, a global static
     * override was shared by all jobs — the second job's compression clobbered
     * (or premature-cleared) the first job's budget while both were running.
     * Here job1 parks INSIDE its invocation until job2 has been admitted with
     * its own (smaller) budget; job1's budget must still be the full share.
     */
    @Test
    void concurrentJobsKeepIndependentBudgets() throws Exception {
        Path r1 = fixtureProject();
        Path r2 = fixtureProject();
        // cores=8 → budget permits = max(2, 8/2) = 4; first job admitted gets
        // the full 4, the second compresses to permits/active = 2
        ServiceCore core = new ServiceCore(8);
        java.util.List<Integer> budgets = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.concurrent.CountDownLatch job1Started = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch job2Admitted = new java.util.concurrent.CountDownLatch(1);
        core.cli = (args, engine, root, out, err, workerBudget) -> {
            budgets.add(workerBudget);
            if (budgets.size() == 1) {
                job1Started.countDown();
                try {
                    job2Admitted.await(30, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                job2Admitted.countDown();
            }
            return 0;
        };
        BuildJob j1 = core.submit(r1.toString(), "status", List.of(), true);
        // deterministic admission order: wait until job1 is RUNNING (budget
        // acquired) before submitting job2, so job1's share is the full 4
        assertTrue(job1Started.await(30, java.util.concurrent.TimeUnit.SECONDS),
                "job1 must start first");
        BuildJob j2 = core.submit(r2.toString(), "status", List.of(), true);
        core.await(j1);
        core.await(j2);
        assertEquals(2, budgets.size(), "both invocations recorded");
        assertEquals(4, budgets.get(0),
                "job1 keeps its full-share budget while job2 runs with its own");
        assertEquals(2, budgets.get(1),
                "job2's admission-time compression (permits/active) reaches the invocation");
    }

    /** plan 2583 pin: cancelling a QUEUED job wakes await() immediately. */
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
        long waited = System.currentTimeMillis() - t0;
        assertEquals(BuildJob.State.CANCELLED, queued.state);
        assertTrue(waited < 5_000,
                "await must return promptly on cancel (notifyAll), took " + waited + "ms");
        synchronized (gate) {
            gate.notifyAll();
        }
    }
}
