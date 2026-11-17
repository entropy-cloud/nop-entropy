package io.nop.ib.service;

import io.nop.ib.NopIbMain;
import io.nop.ib.cli.Commands;
import io.nop.ib.engine.IbEngine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Stateless service core (design 07 IB-S1): all build state lives in each
 * project's .nop-ib directory; this object holds only the engine cache, the
 * job registry and the queues — a restarted service re-derives everything and
 * resubmitted requests replay idempotently. Execution goes through the SAME
 * root-aware {@link NopIbMain#run(String[], Path, PrintStream, PrintStream)}
 * entry the CLI uses — there is no second execution path.
 */
public final class ServiceCore {
    public static final int MAX_PAYLOAD_BYTES = 8 * 1024 * 1024;
    public static final int DEFAULT_JOBS_LIMIT = 50;
    public static final int MAX_JOBS_LIMIT = 500;
    /** wave 11 item 3: idle engines are reaped after this TTL (30 minutes). */
    public static final long DEFAULT_ENGINE_IDLE_TTL_MILLIS = 30 * 60 * 1000L;
    /** reap cadence for the background sweeper. */
    static final long REAP_PERIOD_MILLIS = 60_000L;
    public static final Set<String> ALLOWED_CMDS =
            Set.of("status", "plan", "compile", "test", "check", "model", "clean",
                    // wave 11 item 2: whitelisted real-Maven passthrough —
                    // same lane, so mvn jobs serialize against test/check jobs
                    "mvn");

    private final JobRegistry registry = new JobRegistry();
    private final ProjectQueue queue;
    private final JobBudget budget;
    private final int cores;
    /** injectable clock for the TTL pins; millis since epoch. */
    private final LongSupplier clock;
    private final long engineIdleTtlMillis;
    private final long startedAtMillis;
    /** last access time per cached root (millis, per {@code clock}). */
    private final Map<Path, Long> lastAccess = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reaper;

    /** engine cache (IB-S6): LRU, eviction only for roots not currently running. */
    private final Map<Path, IbEngine> engines = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Path, IbEngine> eldest) {
            if (size() <= 8)
                return false;
            return isEvictable(eldest.getKey());
        }
    };

    /**
     * audit M3 guard, extracted for pins: a root with queued/running jobs holds
     * a live engine — evicting it would fork a second instance with its own
     * caches. Package-private for the LRU pins.
     */
    boolean isEvictable(Path root) {
        return queue.pending(root) == 0;
    }

    public ServiceCore(int cores) {
        this(cores, new ProjectQueue());
    }


    /** Package-private: pins inject a lane with pre-seeded pending jobs. */
    ServiceCore(int cores, ProjectQueue queue) {
        this(cores, queue, System::currentTimeMillis);
    }

    /**
     * wave 11 item 3: pins inject a clock so TTL expiry is deterministic.
     * The TTL itself comes from {@code nop.ib.engineIdleTtlMillis} (default
     * 30 minutes). A daemon sweeper runs the reap every
     * {@link #REAP_PERIOD_MILLIS}; every engine touch and every health read
     * reaps too, so TTL is enforced even when the sweeper is starved.
     */
    ServiceCore(int cores, ProjectQueue queue, LongSupplier clock) {
        this.cores = cores;
        this.queue = queue;
        this.clock = clock;
        this.budget = JobBudget.fromSystem(cores);
        this.engineIdleTtlMillis = ttlFromSystem();
        this.startedAtMillis = clock.getAsLong();
        this.reaper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nop-ib-engine-reaper");
            t.setDaemon(true);
            return t;
        });
        this.reaper.scheduleWithFixedDelay(this::reapIdleEngines,
                REAP_PERIOD_MILLIS, REAP_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static long ttlFromSystem() {
        long v = Long.getLong("nop.ib.engineIdleTtlMillis", DEFAULT_ENGINE_IDLE_TTL_MILLIS);
        return v > 0 ? v : DEFAULT_ENGINE_IDLE_TTL_MILLIS;
    }

    /**
     * Validates and enqueues one build request. Throws IllegalArgumentException
     * when projectDir is not a Maven project (caller maps to 400) or the cmd is
     * not serviceable; returns null when the queue lane is full (caller maps to
     * 429). Returns the job otherwise; for wait=false the caller returns the id
     * immediately, for wait=true it should then call {@link #await(BuildJob)}.
     */
    public BuildJob submit(String projectDir, String cmd, List<String> args, boolean wait) {
        if (!ALLOWED_CMDS.contains(cmd))
            throw new IllegalArgumentException("unsupported service cmd: " + cmd
                    + " (allowed: " + ALLOWED_CMDS + ")");
        Path root;
        try {
            // wave 11 fix (alias identity): resolve to the REAL path so symlink
            // aliases of one worktree (~/sources/x vs /Volumes/data/sources/x)
            // share one queue lane and one engine cache entry — a string-keyed
            // identity would let the same project run twice concurrently
            root = IbEngine.discoverRepoRoot(Path.of(projectDir)).toRealPath();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("not a Maven project: " + projectDir, e);
        }
        BuildJob job = registry.create(projectDir, cmd, root, args, wait);
        boolean accepted = queue.submit(job, () -> execute(job));
        if (!accepted) {
            registry.cancel(job); // lane full: explicit rejection, mark cancelled
            throw new IllegalStateException("queue full for project " + projectDir
                    + " (limit " + ProjectQueue.MAX_QUEUE + ")");
        }
        registry.prune();
        return job;
    }

    /** The one execution path: budget → override → NopIbMain.run on the job root. */
    private void execute(BuildJob job) {
        synchronized (job) {
            if (job.state != BuildJob.State.QUEUED)
                return; // cancelled while queued — explicit, not a silent no-op
            job.state = BuildJob.State.RUNNING;
            job.startedAt = System.currentTimeMillis();
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8);
        // stdout carries the --json payload; stderr diagnostics (Maven fallback
        // warnings etc.) are captured separately — merging them breaks the JSON
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PrintStream errOut = new PrintStream(errBuf, true, StandardCharsets.UTF_8);
        List<String> allArgs = new ArrayList<>();
        allArgs.add(job.cmd);
        allArgs.add("--json");
        if (job.args != null)
            allArgs.addAll(job.args);
        int perJobWorkers = 0;
        try {
            perJobWorkers = budget.acquire();
            io.nop.ib.cli.TestCommand.setWorkerBudgetOverride(perJobWorkers);
            try {
                job.exit = NopIbMain.run(allArgs.toArray(new String[0]),
                        engineFor(job.repoRoot), job.repoRoot, out, errOut);
            } finally {
                io.nop.ib.cli.TestCommand.setWorkerBudgetOverride(0);
            }
            job.state = job.exit == Commands.EXIT_OK ? BuildJob.State.DONE : BuildJob.State.FAILED;
        } catch (Exception e) {
            job.exit = Commands.EXIT_FAILED;
            job.error = String.valueOf(e);
            job.state = BuildJob.State.FAILED;
        } finally {
            budget.release();
            job.finishedAt = System.currentTimeMillis();
            byte[] bytes = buf.toByteArray();
            if (bytes.length > MAX_PAYLOAD_BYTES) {
                job.payload = new String(bytes, 0, MAX_PAYLOAD_BYTES, StandardCharsets.UTF_8);
                job.truncated = true;
            } else {
                job.payload = new String(bytes, StandardCharsets.UTF_8);
            }
            String stderrText = errBuf.toString(StandardCharsets.UTF_8).trim();
            if (!stderrText.isEmpty())
                job.stderr = stderrText;
            synchronized (job) {
                job.notifyAll();
            }
        }
    }

    /** Blocks until the job leaves RUNNING/QUEUED (wait=true handler path). */
    public BuildJob await(BuildJob job) {
        synchronized (job) {
            while (job.state == BuildJob.State.QUEUED || job.state == BuildJob.State.RUNNING) {
                try {
                    job.wait(60_000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return job;
                }
            }
        }
        return job;
    }

    public BuildJob job(String id) {
        return registry.get(id);
    }

    /** Package-private: pins seed the registry directly. */
    JobRegistry registry() {
        return registry;
    }

    /** @return null on cancel, JobRegistry.CANNOT_CANCEL_RUNNING otherwise. */
    public String cancel(String id) {
        BuildJob job = registry.get(id);
        return job == null ? "unknown-job" : registry.cancel(job);
    }

    public List<Map.Entry<String, Integer>> queueSnapshot() {
        List<Map.Entry<String, Integer>> out = new ArrayList<>();
        Map<Path, Integer> seen = new LinkedHashMap<>();
        for (BuildJob j : registry.all()) {
            seen.merge(j.repoRoot, 0, (a, b) -> a);
            if (j.state == BuildJob.State.QUEUED || j.state == BuildJob.State.RUNNING)
                seen.merge(j.repoRoot, 1, Integer::sum);
        }
        for (Map.Entry<Path, Integer> e : seen.entrySet())
            out.add(Map.entry(e.getKey().toString(), e.getValue()));
        return out;
    }

    /**
     * wave 11 observability: per-lane queue facts. depth counts jobs still
     * WAITING (the running one excluded), runningCmd is the cmd executing now
     * or null when the lane is idle. Roots union registry history with live
     * lanes so finished-work roots still show as idle lanes.
     */
    public List<Map<String, Object>> queueLanes() {
        Map<Path, Boolean> roots = new LinkedHashMap<>();
        for (BuildJob j : registry.all())
            roots.put(j.repoRoot, Boolean.TRUE);
        for (Path p : queue.laneRoots())
            roots.putIfAbsent(p, Boolean.TRUE);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Path root : roots.keySet()) {
            Map<String, Object> lane = new LinkedHashMap<>();
            lane.put("root", root.toString());
            lane.put("depth", queue.queuedDepth(root));
            lane.put("runningCmd", queue.runningCmd(root));
            lane.put("pending", queue.pending(root));
            out.add(lane);
        }
        return out;
    }

    /**
     * wave 11 observability: the most recent {@code limit} jobs (default
     * {@link #DEFAULT_JOBS_LIMIT}), newest submission first, as compact
     * summaries — no payloads.
     */
    public List<Map<String, Object>> recentJobs(int limit) {
        if (limit <= 0)
            limit = DEFAULT_JOBS_LIMIT;
        limit = Math.min(limit, MAX_JOBS_LIMIT);
        List<BuildJob> all = new ArrayList<>(registry.all());
        // queuedAt can tie within one millisecond — break ties by id sequence so
        // "newest first" is total (registry.values() order is arbitrary)
        all.sort((a, b) -> {
            int byTime = Long.compare(b.queuedAt, a.queuedAt);
            return byTime != 0 ? byTime : b.id.compareTo(a.id);
        });
        List<Map<String, Object>> out = new ArrayList<>();
        for (BuildJob j : all) {
            if (out.size() >= limit)
                break;
            out.add(j.summary());
        }
        return out;
    }

    /**
     * LRU access is guarded — the eviction callback reads queue depth; each hit
     * refreshes the idle clock used by {@link #reapIdleEngines()}.
     * Package-private for pins.
     */
    synchronized IbEngine engineFor(Path root) {
        lastAccess.put(root, clock.getAsLong());
        IbEngine e = engines.get(root);
        if (e == null) {
            e = new IbEngine(root);
            engines.put(root, e);
        }
        return e;
    }

    /**
     * wave 11 item 3 (resource recycling): drop engines idle beyond
     * {@code nop.ib.engineIdleTtlMillis} (default 30 minutes). Guarded by the
     * same {@link #isEvictable} rule as LRU eviction — a root with queued or
     * running jobs keeps its engine (a second instance would fork its own
     * caches). Removal is lossless: all build state lives in the project's
     * {@code .nop-ib/} directory, so the next request cold-loads an identical
     * engine. @return the number of engines reaped. Package-private for pins.
     */
    synchronized int reapIdleEngines() {
        long now = clock.getAsLong();
        int removed = 0;
        Iterator<Map.Entry<Path, IbEngine>> it = engines.entrySet().iterator();
        while (it.hasNext()) {
            Path root = it.next().getKey();
            Long last = lastAccess.get(root);
            if (last != null && now - last >= engineIdleTtlMillis && isEvictable(root)) {
                it.remove();
                lastAccess.remove(root);
                removed++;
            }
        }
        return removed;
    }

    public Map<String, Object> health() {
        reapIdleEngines();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", NopIbMain.VERSION);
        m.put("cores", cores);
        m.put("workerBudget", budget.permits);
        m.put("activeJobs", budget.activeJobs());
        m.put("pendingTotal", queue.pendingTotal());
        // wave 11 item 3 observability
        m.put("engines", engines.size());
        Runtime rt = Runtime.getRuntime();
        m.put("heapUsedMb", (rt.totalMemory() - rt.freeMemory()) / (1024.0 * 1024.0));
        m.put("uptimeMs", clock.getAsLong() - startedAtMillis);
        return m;
    }
}
