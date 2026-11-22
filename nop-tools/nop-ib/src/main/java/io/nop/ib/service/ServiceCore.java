package io.nop.ib.service;

import io.nop.ib.NopIbMain;
import io.nop.ib.cli.Commands;
import io.nop.ib.engine.IbEngine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
    /**
     * plan 2583 test seam: the CLI invocation (default {@link NopIbMain#run}).
     * Pins record the per-job budget the core hands to each invocation.
     */
    CliRunner cli = NopIbMain::run;

    /** shape of {@code NopIbMain.run(args, engine, root, out, err, workerBudget)}. */
    interface CliRunner {
        int run(String[] args, IbEngine engine, Path root,
                java.io.PrintStream out, java.io.PrintStream err, int workerBudget);
    }
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
        loadKnownRoots();
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
        this.reaper.scheduleWithFixedDelay(() -> {
            reapIdleEngines();
            // plan 2588: terminal jobs and their log files are pruned on the
            // periodic reaper — cleanup never waits for the next submit
            registry.prune();
        }, REAP_PERIOD_MILLIS, REAP_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
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
        return submit(projectDir, cmd, args, wait, null);
    }

    /**
     * plan 2588 idempotent submit (design 09 §2): an optional clientRequestId
     * dedupes retries against the key (repoRoot, clientRequestId) — an existing
     * non-terminal job is returned as-is (replayed=true, nothing re-enqueued),
     * a terminal one is returned as the replayable snapshot. The old overload
     * keeps its exact behavior.
     */
    public BuildJob submit(String projectDir, String cmd, List<String> args, boolean wait,
                           String clientRequestId) {
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
        if (clientRequestId != null) {
            BuildJob existing = registry.findIdempotent(root, clientRequestId);
            if (existing != null) {
                existing.replayed = true;
                return existing;
            }
        }
        // in-flight coalescing (2026-11-22 multi-agent audit): distinct agents
        // often submit the IDENTICAL build command while an equal one is still
        // QUEUED on this lane. Coalescing onto a queued job is result-equivalent
        // (it has not snapshotted inputs yet) and saves the duplicate build.
        // Coalescing onto a RUNNING job is deliberately NOT done: the running
        // job already classified its changed-file set — the new submission must
        // re-enqueue and will hit the test cache on the fast path if nothing
        // actually changed (cache-first, never stale results).
        BuildJob inflight = registry.findActive(root, cmd, args);
        if (inflight != null) {
            inflight.replayed = true;
            inflight.coalesced = true;
            return inflight;
        }
        rememberRoot(root);
        BuildJob job = registry.create(projectDir, cmd, root, args, wait, clientRequestId);
        job.logFile = jobLogFile(root, job.id);
        ProgressRegistry.watch(root, job);
        boolean accepted = queue.submit(job, () -> execute(job));
        if (!accepted) {
            ProgressRegistry.unwatch(job);
            registry.cancel(job); // lane full: explicit rejection, mark cancelled
            throw new IllegalStateException("queue full for project " + projectDir
                    + " (limit " + ProjectQueue.MAX_QUEUE + ")");
        }
        registry.prune();
        return job;
    }

    /** plan 2588: durable per-job log location (dir created lazily on first write). */
    static Path jobLogFile(Path root, String jobId) {
        return root.resolve(".nop-ib").resolve("jobs").resolve(jobId + ".log");
    }

    /** The one execution path: budget → override → NopIbMain.run on the job root. */
    private void execute(BuildJob job) {
        synchronized (job) {
            if (job.state != BuildJob.State.QUEUED)
                return; // cancelled while queued — explicit, not a silent no-op
            job.state = BuildJob.State.RUNNING;
            job.startedAt = System.currentTimeMillis();
            job.thread = Thread.currentThread();
            job.notifyAll(); // plan 2588 wakeup: QUEUED→RUNNING wakes long-poll
        }
        // plan 2588: durable per-job log — stdout+stderr tee with stream markers.
        // Job ids recycle after a service restart; a stale log from the previous
        // incarnation must not bleed into this job's logTail diagnostics.
        try {
            java.nio.file.Files.deleteIfExists(job.logFile);
        } catch (java.io.IOException ignored) {
            // best-effort: a stale log is noise, never a build failure
        }
        JobLog jobLog = new JobLog(job.logFile);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(new LineTee(buf, jobLog, "[out] "), true, StandardCharsets.UTF_8);
        // stdout carries the --json payload; stderr diagnostics (Maven fallback
        // warnings etc.) are captured separately — merging them breaks the JSON
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PrintStream errOut = new PrintStream(new LineTee(errBuf, jobLog, "[err] "), true, StandardCharsets.UTF_8);
        List<String> allArgs = new ArrayList<>();
        allArgs.add(job.cmd);
        allArgs.add("--json");
        if (job.args != null)
            allArgs.addAll(job.args);
        int perJobWorkers = 0;
        try {
            perJobWorkers = budget.acquire();
            // plan 2583: the per-job worker budget travels as an explicit
            // parameter through NopIbMain.run → TestCommand.test — the old
            // static override was overwritten/cleared by concurrent jobs and
            // silently read by whichever module-pool thread happened to run.
            int code = cli.run(allArgs.toArray(new String[0]),
                    engineFor(job.repoRoot), job.repoRoot, out, errOut, perJobWorkers);
            // plan 2588: a running-cancel owns the terminal state (exit=-1) —
            // the winding-down invocation must not overwrite it
            if (job.state == BuildJob.State.RUNNING) {
                job.exit = code;
                job.state = code == Commands.EXIT_OK ? BuildJob.State.DONE : BuildJob.State.FAILED;
            }
        } catch (Exception e) {
            if (job.state == BuildJob.State.RUNNING) {
                job.exit = Commands.EXIT_FAILED;
                job.error = String.valueOf(e);
                job.state = BuildJob.State.FAILED;
            }
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
            jobLog.close();
            // plan 2588: progress facts are O(roots) and overwritten next build;
            // the job itself leaves the long-poll watcher list
            ProgressRegistry.unwatch(job);
            // terminal state set (or CANCELLED already written by the cancel
            // path — never overwritten here); wake await/long-poll waiters
            if (job.state == BuildJob.State.DONE || job.state == BuildJob.State.FAILED)
                JobHistory.append(job.repoRoot, JobHistory.terminalSummary(job));
            synchronized (job) {
                job.notifyAll();
            }
        }
    }

    /**
     * plan 2588 tee: forwards every byte to the in-memory payload buffer while
     * writing whole lines to the job log with a stream marker — the payload
     * contract stays byte-exact, the log gains readable provenance.
     */
    private static final class LineTee extends java.io.OutputStream {
        private final ByteArrayOutputStream mirror;
        private final JobLog log;
        private final String marker;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();

        LineTee(ByteArrayOutputStream mirror, JobLog log, String marker) {
            this.mirror = mirror;
            this.log = log;
            this.marker = marker;
        }

        @Override
        public void write(int b) {
            mirror.write(b);
            line.write(b);
            if (b == '\n')
                flushLine();
        }

        @Override
        public void write(byte[] src, int off, int len) {
            mirror.write(src, off, len);
            int start = off;
            for (int i = off; i < off + len; i++)
                if (src[i] == '\n') {
                    line.write(src, start, i - start + 1);
                    flushLine();
                    start = i + 1;
                }
            if (start < off + len)
                line.write(src, start, off + len - start);
        }

        private void flushLine() {
            String text = line.toString(StandardCharsets.UTF_8).trim();
            line.reset();
            if (!text.isEmpty())
                log.line(marker, text);
        }

        @Override
        public void close() {
            flushLine();
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

    /**
     * plan 2588 long-poll wait: blocks up to {@code waitMs} (segmented waits on
     * the job monitor) until the job reaches a terminal state. Wakeup points:
     * terminal finally, QUEUED cancel (plan 2583), QUEUED→RUNNING transition
     * and progress updates (both new in plan 2588).
     */
    public void waitForChange(BuildJob job, long waitMs) {
        long deadline = System.currentTimeMillis() + waitMs;
        synchronized (job) {
            while (!job.isTerminal()) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0)
                    return;
                try {
                    job.wait(Math.min(left, 1000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * plan 2588: intermediate long-poll state with the position in the lane.
     * queueAhead counts same-root jobs still queued ahead of this one (lane is
     * FIFO by queuedAt); aheadIds/runningJobId give a waiting agent the concrete
     * "who is ahead / who is running" view (2026-11-22 audit gaps 2/3).
     */
    public Map<String, Object> pendingSnapshot(BuildJob job, long waitedMs) {
        int queueAhead = 0;
        List<String> aheadIds = new ArrayList<>();
        String runningJobId = null;
        long jobSeq = seqOf(job);
        for (BuildJob j : registry.all()) {
            if (!j.repoRoot.equals(job.repoRoot))
                continue;
            if (j.state == BuildJob.State.RUNNING && runningJobId == null)
                runningJobId = j.id;
            else if (j.state == BuildJob.State.QUEUED && seqOf(j) < jobSeq) {
                queueAhead++;
                aheadIds.add(j.id);
            }
        }
        return job.pendingSnapshot(queueAhead, aheadIds, runningJobId, waitedMs);
    }

    /** lane order tie-break: queuedAt can tie within one millisecond. */
    private static long seqOf(BuildJob job) {
        try {
            return Long.parseLong(job.id.substring("job-".length()));
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    public BuildJob job(String id) {
        return registry.get(id);
    }

    /** Package-private: pins seed the registry directly. */
    JobRegistry registry() {
        return registry;
    }

    /**
     * plan 2588: QUEUED cancel stays a registry no-op wake; RUNNING cancel is
     * collaborative — kill the root's whole registered process tree (children
     * first via descendants(), then the direct child), interrupt the job
     * thread, and mark the job CANCELLED with exit=-1 and a fail-loud log
     * record. Terminal jobs answer {@code already-terminal}.
     */
    public String cancel(String id) {
        BuildJob job = registry.get(id);
        if (job == null)
            return "unknown-job";
        if (job.state == BuildJob.State.RUNNING) {
            int killed = ProcessTracker.killAll(job.repoRoot);
            Thread t = job.thread;
            if (t != null)
                t.interrupt();
            registry.markCancelled(job, killed);
            return "cancelled";
        }
        String result = registry.cancel(job);
        // null = queued-cancel success — normalize before the HTTP layer
        // (Map.of(value=null) throws NPE; live audit 2026-11-22: every QUEUED
        // cancel over HTTP answered 500-NPE while actually succeeding)
        return result == null ? "cancelled" : result;
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
     * summaries — no payloads. plan 2588: optional state filter
     * (queued|running|done|failed|cancelled; null = all).
     */
    /**
     * 2026-11-22: the answer merges the durable per-root history
     * ({@code .nop-ib/history.jsonl}, survives restarts) with the in-memory
     * registry (in-memory wins on id collision — it carries payload/stderr).
     * Newest first; state filter applies to the merged view.
     */
    /**
     * Served-root ledger: absolute repo roots this service has ever normalized,
     * persisted service-scope so a restarted service can still answer /v1/jobs
     * from the per-project durable histories before any new submit arrives.
     * Location overridable via {@code nop.ib.rootsLedger} (tests).
     */
    static Path rootsLedger() {
        String override = System.getProperty("nop.ib.rootsLedger");
        if (override != null)
            return Path.of(override);
        return Path.of(System.getProperty("user.home"), ".nop-ib-service", "served-roots.txt");
    }

    private final java.util.Set<Path> knownRoots = new java.util.LinkedHashSet<>();

    private void loadKnownRoots() {
        Path ledger = rootsLedger();
        if (!Files.isRegularFile(ledger))
            return;
        try {
            for (String line : Files.readAllLines(ledger)) {
                line = line.trim();
                if (!line.isEmpty())
                    knownRoots.add(Path.of(line));
            }
        } catch (IOException ignored) {
            // ledger is advisory
        }
    }

    private void rememberRoot(Path root) {
        if (knownRoots.add(root)) {
            try {
                Files.createDirectories(rootsLedger().getParent());
                Files.writeString(rootsLedger(), root + "\n", StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // advisory
            }
        }
    }

    public List<Map<String, Object>> recentJobs(int limit, String stateFilter) {
        if (limit <= 0)
            limit = DEFAULT_JOBS_LIMIT;
        limit = Math.min(limit, MAX_JOBS_LIMIT);
        Map<String, Map<String, Object>> merged = new java.util.LinkedHashMap<>();
        java.util.Set<Path> historyRoots = new java.util.LinkedHashSet<>(knownRoots);
        historyRoots.addAll(queue.laneRoots());
        for (Path root : historyRoots)
            for (Map<String, Object> h : JobHistory.readLast(root, JobHistory.RETAINED))
                merged.put(String.valueOf(h.get("id")), h);
        List<BuildJob> all = new ArrayList<>(registry.all());
        // queuedAt can tie within one millisecond — break ties by id sequence so
        // "newest first" is total (registry.values() order is arbitrary)
        all.sort((a, b) -> {
            int byTime = Long.compare(b.queuedAt, a.queuedAt);
            return byTime != 0 ? byTime : b.id.compareTo(a.id);
        });
        for (BuildJob j : all)
            merged.put(j.id, j.summary());
        List<Map<String, Object>> sorted = new ArrayList<>(merged.values());
        sorted.sort((a, b) -> {
            long ta = asLong(a.get("queuedAt"));
            long tb = asLong(b.get("queuedAt"));
            int byTime = Long.compare(tb, ta);
            return byTime != 0 ? byTime
                    : String.valueOf(b.get("id")).compareTo(String.valueOf(a.get("id")));
        });
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> j : sorted) {
            if (stateFilter != null
                    && !stateFilter.equals(j.get("state")))
                continue;
            if (out.size() >= limit)
                break;
            out.add(j);
        }
        return out;
    }

    private static long asLong(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    /** Old signature kept: no state filter. */
    public List<Map<String, Object>> recentJobs(int limit) {
        return recentJobs(limit, null);
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

    /** synchronized (plan 2583): engines.size() reads the guarded LRU map. */
    public synchronized Map<String, Object> health() {
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
