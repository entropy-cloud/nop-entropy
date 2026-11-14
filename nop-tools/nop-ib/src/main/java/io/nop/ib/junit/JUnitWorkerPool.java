package io.nop.ib.junit;

import io.nop.ib.NopIbException;
import io.nop.ib.compile.ClasspathMaterializer;
import io.nop.ib.json.JsonIn;
import io.nop.ib.json.JsonOut;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Persistent test worker JVMs (design 06 WR-D1/WR-D4): W long-lived forks pull
 * classes from the parent; between classes the generic reset layer (System
 * properties snapshot) and the configured resetters (reflection contract) run
 * inside the worker. Zero-fake-green guards: differential rerun in a fresh fork
 * for failed classes, worker retirement on reset-hook failure or after
 * maxClasses, reuse pay-off guard (degrade when reset cost exceeds fork startup).
 */
public final class JUnitWorkerPool {
    private final IbModel model;
    private final IbModule module;
    private final ClasspathMaterializer.Materialized classpath;
    private final List<String> resetters;
    private final int workers;
    private final int maxClassesPerWorker;
    private final List<String> warnings = new ArrayList<>();
    private List<String> cmdPrefix;
    private final java.util.Set<Worker> created =
            java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());

    public JUnitWorkerPool(IbModel model, IbModule module,
                           ClasspathMaterializer.Materialized classpath,
                           List<String> resetters, int workers, int maxClassesPerWorker) {
        this.model = model;
        this.module = module;
        this.classpath = classpath;
        this.resetters = resetters == null ? List.of() : resetters;
        this.workers = Math.max(1, workers);
        this.maxClassesPerWorker = Math.max(1, maxClassesPerWorker);
    }

    public JUnitForkExecutor.ForkResult run(List<String> classes) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("tests", 0);
        summary.put("failures", 0);
        summary.put("skipped", 0);
        List<Map<String, String>> failedDetails = new ArrayList<>();
        List<String> pollution = new ArrayList<>();
        if (classes.isEmpty())
            return new JUnitForkExecutor.ForkResult(0, summary, warnings);
        // G-C tail defect: nested helper classes with no test methods used to be
        // dispatched here (index-level testClasses() over-inclusion) and could
        // crash workers, stalling the sweep for a full watchdog period each.
        // Filtering now happens in the unit index (test marker + superclass chain).

        java.util.concurrent.BlockingQueue<Worker> idle = new LinkedBlockingQueue<>();
        // pre-spawn the pool in parallel: tasks submit faster than workers spawn,
        // so on-demand spawning degenerated into per-class cold JVMs (no reuse)
        int eager = Math.min(workers, classes.size());
        java.util.concurrent.ExecutorService spawner =
                java.util.concurrent.Executors.newFixedThreadPool(eager, r -> {
                    Thread t = new Thread(r, "nop-ib-worker-spawn");
                    t.setDaemon(true);
                    return t;
                });
        java.util.List<java.util.concurrent.Future<?>> spawnJobs = new ArrayList<>();
        long spawnT0 = System.currentTimeMillis();
        for (int i = 0; i < eager; i++) {
            spawnJobs.add(spawner.submit(() -> {
                try {
                    idle.put(spawn());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        for (var j : spawnJobs) {
            try {
                j.get();
            } catch (Exception e) {
                warnings.add("worker pre-spawn failed: " + e);
            }
        }
        spawner.shutdown();
        long startupMs = System.currentTimeMillis() - spawnT0;
        long startupThreshold = Math.max(400, startupMs);
        long totalResetMs = 0;
        int resetSamples = 0;
        boolean[] degrade = new boolean[]{false};

        Map<String, Map<String, Object>> results = new LinkedHashMap<>();
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(workers, r -> {
                    Thread t = new Thread(r, "nop-ib-worker-dispatch");
                    t.setDaemon(true);
                    return t;
                });
        java.util.List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        Object lock = new Object();
        java.util.concurrent.atomic.AtomicReference<Throwable> firstError =
                new java.util.concurrent.atomic.AtomicReference<>();

        for (String cls : classes) {
            futures.add(pool.submit(() -> {
                if (firstError.get() != null)
                    return;
                // one self-heal retry on a fresh worker: transient spawn/EOF failures
                // under concurrent JVM startup storms must not abort the module
                for (int attempt = 0; attempt < 2; attempt++) {
                    Worker w = null;
                    try {
                        w = idle.poll(5, TimeUnit.SECONDS);
                        if (w == null || w.dead) {
                            if (w != null)
                                w.kill();
                            w = spawn();
                        }
                        Map<String, Object> r = w.runClass(cls);
                        w.classesRun++;
                        boolean retire = Boolean.TRUE.equals(r.get("resetFailed"))
                                || w.classesRun >= maxClassesPerWorker;
                        synchronized (lock) {
                            if (Boolean.TRUE.equals(r.get("resetFailed")))
                                pollution.add(cls);
                            if (r.get("resetMs") instanceof Number n && n.longValue() > startupThreshold)
                                degrade[0] = true;
                            results.put(cls, r);
                        }
                        if (retire)
                            w.kill();
                        else
                            idle.put(w);
                        return;
                    } catch (Throwable t) {
                        if (w != null)
                            w.kill();
                        if (attempt == 1) {
                            warnings.add("class " + cls + " failed twice (worker path): " + t);
                            firstError.compareAndSet(null, t);
                        }
                    }
                }
            }));
        }
        for (var f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                firstError.compareAndSet(null, e);
            }
        }
        pool.shutdown();
        // leak-proof: kill EVERY spawned worker (idle + in-flight + crash-quiet)
        for (Worker w : created)
            w.kill();

        if (firstError.get() != null) {
            warnings.add("worker pool aborted: " + firstError.get());
            summary.put("poolAborted", true);
            return new JUnitForkExecutor.ForkResult(1, summary, warnings);
        }

        int tests = 0, failures = 0, skipped = 0;
        List<Map<String, String>> failed = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<Object[]> slowAll = new ArrayList<>();
        List<String> slowNames = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : results.entrySet()) {
            Map<String, Object> r = e.getValue();
            slowNames.add(e.getKey());
            tests += numOf(r.get("tests"));
            failures += numOf(r.get("failures"));
            skipped += numOf(r.get("skipped"));
            if (r.get("resetMs") instanceof Number n) {
                totalResetMs += n.longValue();
                resetSamples++;
            }
            if (numOf(r.get("tests")) == 0)
                missing.add(e.getKey());
            if (r.get("failed") instanceof Iterable<?> iter) {
                for (Object o : iter) {
                    if (o instanceof Map<?, ?> m) {
                        Map<String, String> d = new LinkedHashMap<>();
                        d.put("class", String.valueOf(m.get("class")));
                        d.put("method", String.valueOf(m.get("method")));
                        d.put("type", String.valueOf(m.get("type")));
                        d.put("message", String.valueOf(m.get("message")));
                        d.put("timeMs", String.valueOf(r.get("timeMs")));
                        failed.add(d);
                    }
                }
            }
            slowAll.add(new Object[]{timeOf(r), slowNames.size() - 1});
        }
        if (degrade[0])
            warnings.add("reuse not profitable: reset cost exceeds fork startup ("
                    + startupMs + "ms) — differential rerun remains the pollution guard");
        if (!pollution.isEmpty())
            warnings.add("pollution guard triggered for: " + String.join(",", pollution));

        summary.put("tests", tests);
        summary.put("failures", failures);
        summary.put("skipped", skipped);
        summary.put("failed", failed);
        summary.put("missingClasses", missing);
        summary.put("startupMs", startupMs);
        slowAll.sort((a, b) -> Long.compare((long) b[0], (long) a[0]));
        List<String> slowest = new ArrayList<>();
        for (int i = 0; i < Math.min(5, slowAll.size()); i++)
            slowest.add(slowNames.get((int) slowAll.get(i)[1]) + "=" + slowAll.get(i)[0] + "ms");
        summary.put("slowest", slowest);
        if (resetSamples > 0)
            summary.put("avgResetMs", totalResetMs / resetSamples);
        int exit = failures > 0 ? 1 : 0;
        return new JUnitForkExecutor.ForkResult(exit, summary, warnings);
    }

    private final class Worker {
        final Process process;
        final java.io.BufferedWriter stdin;
        {
            // registry for leak-proof teardown: every spawned worker is killed at
            // run() end whether idle, running, or crashed-quietly
            synchronized (JUnitWorkerPool.this) {
                created.add(this);
            }
        }
        /** protocol lines pushed by the stdout pump; never blocks the worker side */
        final java.util.concurrent.BlockingQueue<String> protocol =
                new LinkedBlockingQueue<>();
        /** last bytes of worker stderr: a crashed worker must explain itself */
        private final java.util.concurrent.atomic.AtomicReference<String> stderrTail =
                new java.util.concurrent.atomic.AtomicReference<>("");
        volatile boolean dead;
        int classesRun;

        String stderrTail() {
            return stderrTail.get();
        }

        /** keeps only the trailing ~4KB of stderr for failure diagnostics */
        private void appendStderr(String line) {
            stderrTail.accumulateAndGet(line, (cur, l) -> {
                String next = cur + l + "\n";
                return next.length() > 4096 ? next.substring(next.length() - 4096) : next;
            });
        }

        Worker() {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmdPrefix());
                pb.directory(model.resolve(module.getBasedir())); // cwd = module basedir
                pb.redirectErrorStream(false);
                process = pb.start();
                stdin = new java.io.BufferedWriter(new java.io.OutputStreamWriter(
                        process.getOutputStream(), StandardCharsets.UTF_8));
                java.io.BufferedReader stdout = new java.io.BufferedReader(new java.io.InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8));
                // stdout pump: test-code logging MUST be consumed continuously or the
                // 64KB pipe fills and the worker blocks forever on System.out.write
                Thread pump = new Thread(() -> {
                    try {
                        String line;
                        while ((line = stdout.readLine()) != null) {
                            if (line.startsWith(IbJUnitRunner.PROTOCOL_PREFIX))
                                protocol.offer(line.substring(
                                        IbJUnitRunner.PROTOCOL_PREFIX.length()).trim());
                        }
                        dead = true;
                    } catch (IOException e) {
                        dead = true;
                    }
                }, "nop-ib-worker-out");
                pump.setDaemon(true);
                pump.start();
                Thread t = new Thread(() -> {
                    try (var r = process.errorReader(StandardCharsets.UTF_8)) {
                        String line;
                        while ((line = r.readLine()) != null)
                            appendStderr(line);
                    } catch (IOException ignored) {
                        // teardown
                    }
                }, "nop-ib-worker-err");
                t.setDaemon(true);
                t.start();
                // startup handshake: wait for the runner's hello before the worker
                // enters the idle pool — a JVM that dies at boot (classpath breakage,
                // crash in static init) fails spawn immediately with its stderr
                // instead of poisoning dispatch with a dead worker
                String hello;
                try {
                    hello = protocol.poll(WORKER_HELLO_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    dead = true;
                    kill();
                    throw new NopIbException("interrupted while waiting for worker hello", e);
                }
                if (hello == null) {
                    dead = true;
                    kill();
                    throw new NopIbException("test worker died at startup (exit "
                            + exitCodeUnchecked() + ") stderr tail:\n" + stderrTail());
                }
            } catch (IOException e) {
                throw new NopIbException("cannot spawn test worker", e);
            }
        }

        private int exitCodeUnchecked() {
            try {
                return process.exitValue();
            } catch (IllegalThreadStateException e) {
                return -1; // still alive but silent: hello timeout
            }
        }

        Map<String, Object> runClass(String cls) throws Exception {
            stdin.write("{\"cmd\":\"run\",\"class\":" + JsonOut.write(cls) + "}");
            stdin.newLine();
            stdin.flush();
            // watchdog: a hung class gets its worker killed -> protocol EOF ->
            // differential rerun path; it can never block the module
            java.util.concurrent.ScheduledFuture<?> watchdog = WATCHDOG.schedule(() -> {
                        System.err.println("nop-ib worker watchdog: killing hung class " + cls);
                        kill();
                    }, forkTimeoutMs, TimeUnit.MILLISECONDS);
            try {
                long deadline = System.currentTimeMillis() + forkTimeoutMs + 30_000;
                // poll in slices: a dead worker (EOF seen by the pump) must fail
                // FAST — waiting the full window on a corpse stalled the sweep tail
                String line = null;
                while (line == null) {
                    long left = deadline - System.currentTimeMillis();
                    if (left <= 0)
                        break;
                    line = protocol.poll(Math.min(500, left), TimeUnit.MILLISECONDS);
                    if (line == null && dead) {
                        throw new IOException("worker died while running " + cls
                                + " stderr tail:\n" + stderrTail());
                    }
                }
                if (line == null) {
                    dead = true;
                    throw new IOException("worker timeout/EOF (crashed) on " + cls
                            + " stderr tail:\n" + stderrTail());
                }
                return JsonIn.asMapOrEmpty(JsonIn.silentParse(line));
            } finally {
                watchdog.cancel(false);
            }
        }

        void kill() {
            dead = true;
            // async teardown: closing stdin makes the worker loop exit; force-kill
            // races are fine. NEVER wait here — sequential waitFor(5s) per worker
            // turned teardown into a 75s stall for a 15-worker pool.
            try {
                stdin.close();
            } catch (IOException ignored) {
                // already dead
            }
            Thread reaper = new Thread(process::destroyForcibly, "nop-ib-worker-reaper");
            reaper.setDaemon(true);
            reaper.start();
        }
    }

    private List<String> cmdPrefix() {
        if (cmdPrefix != null)
            return cmdPrefix;
        Path launcherJar = locateLauncherJar(detectPlatformVersion(classpath.entries()));
        if (launcherJar == null)
            throw new NopIbException("junit-platform-launcher jar unavailable");
        Path runnerClasses = locateRunnerClasses();
        if (runnerClasses == null)
            throw new NopIbException("nop-ib runner classes unavailable");
        Path repoRoot = Path.of(model.getRepoRoot());
        List<String> cp = new ArrayList<>();
        for (String entry : classpath.entries()) {
            Path p = Path.of(entry);
            cp.add(p.isAbsolute() ? entry : repoRoot.resolve(entry).toString());
        }
        if (cp.stream().noneMatch(e -> e.contains("junit-jupiter-engine"))) {
            String jupiterVersion = detectArtifactVersion(classpath.entries(), "junit-jupiter-");
            if (jupiterVersion != null) {
                Path engineJar = locateArtifactJar("org.junit.jupiter", "junit-jupiter-engine",
                        jupiterVersion, null);
                if (engineJar != null)
                    cp.add(engineJar.toString());
            }
        }
        cp.add(launcherJar.toString());
        cp.add(runnerClasses.toString());

        List<String> cmd = new ArrayList<>();
        cmd.add(javaHomeBin("java"));
        String argLine = module.getSurefireArgLine();
        if (argLine != null && !argLine.isBlank()) {
            for (String token : argLine.trim().split("\\s+")) {
                if (token.contains("${"))
                    continue;
                cmd.add(token);
            }
        }
        cmd.add("-Dnop.autotest.force-save-output=false");
        cmd.add("-Dnop.autotest.disable-snapshot=false");
        module.getSurefireSystemProperties().forEach((k, v) -> cmd.add("-D" + k + "=" + v));
        cmd.add("-cp");
        cmd.add(String.join(":", cp));
        cmd.add(IbJUnitRunner.class.getName());
        cmd.add("--worker");
        cmd.add(String.join(",", resetters));
        cmdPrefix = cmd;
        return cmd;
    }

    private Worker spawn() {
        return new Worker();
    }

    private static long forkTimeoutMs =
            Long.getLong("nop.ib.forkTimeoutSeconds", 300) * 1000;

    /** worker must say hello within this window or spawn fails (crash at boot) */
    private static final long WORKER_HELLO_TIMEOUT_MS =
            Long.getLong("nop.ib.workerHelloTimeoutMs", 60_000);

    /**
     * Pool-level watchdog executor: creating one per runClass leaked a thread per
     * class (43k classes = 43k parked threads) and froze the parent.
     */
    private static final java.util.concurrent.ScheduledExecutorService WATCHDOG =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "nop-ib-worker-watchdog");
                t.setDaemon(true);
                return t;
            });


    static long timeOf(Map<String, Object> r) {
        return r.get("timeMs") instanceof Number n ? n.longValue() : 0;
    }

    static int numOf(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    private String javaHomeBin(String tool) {
        String javaHome = System.getProperty("java.home");
        return javaHome != null ? Path.of(javaHome, "bin", tool).toString() : tool;
    }

    Path locateLauncherJar(String version) {
        return locateArtifactJar("org.junit.platform", "junit-platform-launcher", version, null);
    }

    Path locateArtifactJar(String group, String artifact, String version, String classifier) {
        String rel = relativeRepoPath(group, artifact, version, classifier);
        String common = commonRepo();
        Path jar = Path.of(common, rel);
        if (Files.isRegularFile(jar))
            return jar;
        jar = Path.of(model.getLocalRepo(), rel);
        return Files.isRegularFile(jar) ? jar : null;
    }

    private String commonRepo() {
        String env = System.getenv("NOP_IB_COMMON_REPO");
        if (env != null && !env.isBlank())
            return env;
        return Path.of(System.getProperty("user.home"), ".m2", "repository").toString();
    }

    private String relativeRepoPath(String group, String artifact, String version, String classifier) {
        StringBuilder name = new StringBuilder(artifact).append('-').append(version);
        if (classifier != null && !classifier.isEmpty())
            name.append('-').append(classifier);
        name.append(".jar");
        return Path.of(group.replace('.', '/'), artifact, version, name.toString()).toString();
    }

    Path locateRunnerClasses() {
        var pd = IbJUnitRunner.class.getProtectionDomain();
        if (pd.getCodeSource() != null && pd.getCodeSource().getLocation() != null) {
            Path p = Path.of(pd.getCodeSource().getLocation().getPath());
            if (Files.isDirectory(p))
                return p;
        }
        return null;
    }

    static String detectPlatformVersion(List<String> entries) {
        return detectArtifactVersion(entries, "junit-platform-commons-");
    }

    static String detectArtifactVersion(List<String> entries, String prefix) {
        for (String entry : entries) {
            String name = entry.substring(entry.lastIndexOf('/') + 1);
            if (name.startsWith(prefix) && name.endsWith(".jar")) {
                return name.substring(prefix.length(), name.length() - ".jar".length());
            }
        }
        return null;
    }
}
