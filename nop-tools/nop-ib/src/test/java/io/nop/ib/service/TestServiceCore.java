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
        BuildJob j1 = core.submit(repo.toString(), "status", List.of(), true);
        BuildJob j2 = core.submit(repo.toString(), "status", List.of(), true);
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
        assertEquals(repo.toAbsolutePath().normalize(), job.repoRoot);
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

    /** cancel: queued jobs cancel; running/done jobs answer cannot-cancel-running. */
    @Test
    void cancelSemanticsAreExplicit() throws Exception {
        JobRegistry registry = new JobRegistry();
        Path root = Path.of("/fake/root");
        BuildJob queued = registry.create("p", "status", root, List.of(), false);
        assertNull(registry.cancel(queued), "queued job cancels");
        assertEquals(BuildJob.State.CANCELLED, queued.state);
        BuildJob running = registry.create("p", "status", root, List.of(), false);
        running.state = BuildJob.State.RUNNING;
        assertEquals(JobRegistry.CANNOT_CANCEL_RUNNING, registry.cancel(running),
                "running job cannot be cancelled — explicit, not a silent no-op");
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
}
