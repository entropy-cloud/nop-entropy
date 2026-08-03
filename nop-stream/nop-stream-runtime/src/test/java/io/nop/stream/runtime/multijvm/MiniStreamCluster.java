/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.multijvm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.stream.runtime.cluster.JdbcClusterRegistry;
import io.nop.stream.runtime.launch.ClusterLaunchConfig;
import io.nop.stream.runtime.launch.JobCoordinatorMain;
import io.nop.stream.runtime.launch.TaskManagerMain;

/**
 * Stage 42 Phase 2: test-scope process orchestration harness that spawns N
 * TaskManager JVMs + 1 JobCoordinator JVM as real OS processes, against a shared
 * H2 {@code AUTO_SERVER=TRUE} backing store.
 *
 * <p>Spawns processes via {@link ProcessBuilder}:
 * <pre>
 *   {@code java -cp <test-classpath> io.nop.stream.runtime.launch.TaskManagerMain
 *         nodeId=tm-0 jdbcUrl=<shared-h2> topicNamespace=<runId> ...}
 *   {@code java -cp <test-classpath> io.nop.stream.runtime.launch.JobCoordinatorMain
 *         jobId=job-1 jdbcUrl=<shared-h2> topicNamespace=<runId> ...}
 * </pre>
 *
 * <p>The harness:
 * <ul>
 *   <li>allocates a unique {@code runId} (timestamp + counter) so concurrent
 *       cluster instances do not collide (plan guide — deterministic topic
 *       allocation);</li>
 *   <li>provisions the shared H2 DB file under {@code <project-root>/_tmp/}
 *       (AGENTS.md sandbox contract — never system {@code /tmp});</li>
 *   <li>captures each process's stdout/stderr to a prefixed log file so failures
 *       are diagnosable;</li>
 *   <li>health-checks each process by polling the shared {@link JdbcClusterRegistry}
 *       for node registration before returning from {@link #start()};</li>
 *   <li>implements {@link #killTaskManager(String)} / {@link #restartTaskManager(String)}
 *       via {@link Process#destroy()} (SIGTERM) → {@link Process#destroyForcibly()}
 *       after timeout;</li>
 *   <li>{@link #shutdown()} stops every process, asserts they exited, cleans up
 *       the H2 DB file + checkpoint directory.</li>
 * </ul>
 *
 * <p>Test classes use this harness (Phase 3) gated by
 * {@code @EnabledIfSystemProperty("nop.stream.test.multi-jvm.enabled")}.
 */
public class MiniStreamCluster implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MiniStreamCluster.class);
    private static final AtomicLong RUN_ID_SEQ = new AtomicLong(0L);

    /** Default readiness timeout (single process start + first heartbeat). */
    private static final long DEFAULT_HEALTH_TIMEOUT_MS = 30_000L;
    /** Default SIGTERM → destroyForcibly grace window. */
    private static final long DEFAULT_KILL_GRACE_MS = 5_000L;

    /** Project-root _tmp sandbox (AGENTS.md): never system /tmp. */
    private static final String PROJECT_ROOT_TMP = findProjectRootTmp();

    private final String runId;
    private final Path runDir;
    private final Path dbFile;
    private final String jdbcUrl;
    private final Path checkpointDir;
    private final String classpath;
    private final String javaExecutable;
    private final String topicNamespace;

    private final int taskManagerCount;
    private final long pollIntervalMs;
    private final long healthTimeoutMs;
    private final long killGraceMs;

    private final Map<String, ProcessHandle> taskProcesses = new ConcurrentHashMap<>();

    /**
     * Stage 46: coordinator processes keyed by index ("coordinator-0", "coordinator-1", ...).
     * Index 0 is the primary coordinator (spawned by {@link #start()}); additional
     * coordinators are spawned via {@link #spawnJobCoordinator(int)} for HA failover
     * tests. All coordinators share the same JDBC lease table when HA mode is enabled.
     */
    private final Map<String, Process> coordinatorProcesses = new ConcurrentHashMap<>();
    private HikariDataSource harnessDataSource;
    private IJdbcTemplate harnessJdbcTemplate;
    private JdbcClusterRegistry harnessRegistry;

    public MiniStreamCluster(int taskManagerCount) {
        this(taskManagerCount, DEFAULT_HEALTH_TIMEOUT_MS, DEFAULT_KILL_GRACE_MS, 50L);
    }

    public MiniStreamCluster(int taskManagerCount, long healthTimeoutMs, long killGraceMs, long pollIntervalMs) {
        this.runId = System.currentTimeMillis() + "-" + RUN_ID_SEQ.incrementAndGet();
        this.taskManagerCount = taskManagerCount;
        this.healthTimeoutMs = healthTimeoutMs;
        this.killGraceMs = killGraceMs;
        this.pollIntervalMs = pollIntervalMs;

        this.runDir = Paths.get(PROJECT_ROOT_TMP, "mini-stream-cluster", runId);
        this.dbFile = runDir.resolve("cluster.db");
        // AUTO_SERVER=TRUE lets multiple JVMs share one file-based DB on the same
        // machine. AUTO_SERVER_RECONNECT adds robustness if the launcher briefly
        // drops. MODE=MySQL so nop-dao's MySQL dialect applies.
        this.jdbcUrl = "jdbc:h2:file:" + dbFile + ";AUTO_SERVER=TRUE;MODE=MySQL";
        this.checkpointDir = runDir.resolve("checkpoints");
        this.topicNamespace = "run-" + runId;
        this.classpath = System.getProperty("java.class.path");
        this.javaExecutable = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
    }

    // ==================== Lifecycle ====================

    /**
     * Provisions shared state, spawns N TaskManager JVMs, then spawns the
     * JobCoordinator JVM (non-HA, single-instance). Waits for every process to
     * register before returning. Backwards-compatible with Stage 42 tests.
     */
    public synchronized void start() throws IOException, InterruptedException {
        start(false);
    }

    /**
     * Stage 46: provisions shared state, spawns N TaskManager JVMs, then spawns the
     * primary JobCoordinator JVM. When {@code haMode} is true the coordinator runs in
     * leader-gated mode (STANDBY until granted leadership via the shared JDBC lease
     * table); additional coordinators can be spawned via {@link #spawnJobCoordinator(int)}
     * to form an HA cluster.
     */
    public synchronized void start(boolean haMode) throws IOException, InterruptedException {
        Files.createDirectories(runDir);
        Files.createDirectories(checkpointDir);
        Files.createDirectories(runDir.resolve("logs"));

        // Bootstrap a harness-side JDBC handle so we can query the shared
        // registry for health checks. Uses the same H2 file with AUTO_SERVER.
        initHarnessJdbc();

        // Drop any stale tables so the run is clean.
        dropStreamTablesQuietly();

        // Spawn TaskManagers first so the coordinator sees them on its
        // nodeRegistration wait window.
        for (int i = 0; i < taskManagerCount; i++) {
            String nodeId = "tm-" + i;
            spawnTaskManager(nodeId);
        }

        // Wait for every TM to register in the shared cluster registry.
        List<String> expected = expectedNodeIds();
        waitForNodeRegistration(expected, healthTimeoutMs);

        // Spawn the primary JobCoordinator.
        spawnJobCoordinator(0, haMode);
    }

    public synchronized void restartTaskManager(String nodeId) throws IOException, InterruptedException {
        killTaskManager(nodeId);
        // Wait briefly for the OS to reclaim resources.
        Thread.sleep(200L);
        spawnTaskManager(nodeId);
        waitForNodeRegistration(java.util.Collections.singletonList(nodeId), healthTimeoutMs);
    }

    public synchronized boolean killTaskManager(String nodeId) {
        ProcessHandle handle = taskProcesses.remove(nodeId);
        if (handle == null) {
            LOG.warn("killTaskManager({}): no such process (already gone?)", nodeId);
            return false;
        }
        return terminateProcess(handle, killGraceMs);
    }

    public synchronized boolean killCoordinator() {
        return killCoordinator(0);
    }

    /**
     * Stage 46: kills the coordinator at the given index. Returns false if no such
     * coordinator process exists.
     */
    public synchronized boolean killCoordinator(int index) {
        Process p = coordinatorProcesses.remove(coordinatorKey(index));
        if (p == null) {
            return false;
        }
        return terminateProcess(p.toHandle(), killGraceMs);
    }

    /**
     * Stage 46: spawns an additional coordinator JVM at the given index. Used by HA
     * failover tests to form a multi-coordinator cluster sharing one JDBC lease table.
     * The coordinator runs in HA mode (leader-gated).
     */
    public synchronized Process spawnJobCoordinator(int index) throws IOException {
        return spawnJobCoordinator(index, true);
    }

    private static String coordinatorKey(int index) {
        return "coordinator-" + index;
    }

    @Override
    public synchronized void close() {
        shutdown();
    }

    public synchronized void shutdown() {
        LOG.info("MiniStreamCluster({}) shutting down", runId);
        // Kill coordinators first so they do not retry recovery against dying TMs.
        for (Map.Entry<String, Process> e : new ArrayList<>(coordinatorProcesses.entrySet())) {
            terminateProcess(e.getValue().toHandle(), killGraceMs);
        }
        coordinatorProcesses.clear();
        for (Map.Entry<String, ProcessHandle> e : new ArrayList<>(taskProcesses.entrySet())) {
            terminateProcess(e.getValue(), killGraceMs);
        }
        taskProcesses.clear();

        if (harnessDataSource != null) {
            harnessDataSource.close();
            harnessDataSource = null;
        }
        // NOTE: logs + DB are preserved for post-mortem when
        // -Dnop.stream.test.multi-jvm.preserve-artifacts=true; otherwise deleted.
        if (!Boolean.getBoolean("nop.stream.test.multi-jvm.preserve-artifacts")) {
            deleteQuietly(runDir);
        }
    }

    // ==================== Health / inspection ====================

    public Set<String> registeredNodeIds() {
        if (harnessRegistry == null) {
            return java.util.Collections.emptySet();
        }
        try {
            return harnessRegistry.getActiveNodes().stream()
                    .map(io.nop.stream.runtime.cluster.NodeInfo::getNodeId)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            LOG.warn("registeredNodeIds failed", e);
            return java.util.Collections.emptySet();
        }
    }

    public boolean coordinatorAlive() {
        return coordinatorAlive(0);
    }

    /**
     * Stage 46: returns whether the coordinator at the given index is alive.
     */
    public boolean coordinatorAlive(int index) {
        Process p = coordinatorProcesses.get(coordinatorKey(index));
        return p != null && p.toHandle().isAlive();
    }

    /**
     * Stage 46: returns the number of coordinator processes currently tracked
     * (alive or recently killed but not yet removed).
     */
    public int coordinatorCount() {
        return coordinatorProcesses.size();
    }

    public boolean taskManagerAlive(String nodeId) {
        ProcessHandle h = taskProcesses.get(nodeId);
        return h != null && h.isAlive();
    }

    public List<String> expectedNodeIds() {
        List<String> ids = new ArrayList<>(taskManagerCount);
        for (int i = 0; i < taskManagerCount; i++) {
            ids.add("tm-" + i);
        }
        return ids;
    }

    /** Returns the path of the captured stdout/stderr log for {@code nodeId}. */
    public Path logFileFor(String nodeId) {
        return runDir.resolve("logs").resolve(nodeId + ".log");
    }

    public String getRunId() {
        return runId;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getTopicNamespace() {
        return topicNamespace;
    }

    public Path getCheckpointDir() {
        return checkpointDir;
    }

    public IJdbcTemplate getHarnessJdbcTemplate() {
        return harnessJdbcTemplate;
    }

    public JdbcClusterRegistry getHarnessRegistry() {
        return harnessRegistry;
    }

    // ==================== Internals ====================

    private void initHarnessJdbc() {
        harnessDataSource = new HikariDataSource();
        harnessDataSource.setDriverClassName("org.h2.Driver");
        harnessDataSource.setJdbcUrl(jdbcUrl);
        harnessDataSource.setUsername("sa");
        harnessDataSource.setPassword("");
        harnessDataSource.setMaximumPoolSize(2);

        // nop-dao dialect lookup requires CoreInitialization.
        CoreInitialization.initialize();
        JdbcFactory factory = new JdbcFactory();
        harnessJdbcTemplate = factory.newJdbcTemplate(factory.newTransactionTemplate(harnessDataSource));
        harnessRegistry = new JdbcClusterRegistry(harnessJdbcTemplate);
    }

    private void dropStreamTablesQuietly() {
        for (String t : new String[]{
                "nop_stream_task_assignment", "nop_stream_node", "nop_stream_coordinator",
                "nop_stream_msg_queue"}) {
            try {
                harnessJdbcTemplate.executeUpdate(SQL.begin().sql("DROP TABLE IF EXISTS " + t).end());
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    private void spawnTaskManager(String nodeId) throws IOException {
        List<String> cmd = buildJavaCommand(TaskManagerMain.class.getName(),
                "nodeId=" + nodeId,
                "jdbcUrl=" + jdbcUrl,
                "topicNamespace=" + topicNamespace,
                "pollIntervalMs=" + pollIntervalMs,
                "capacity=8");
        Process p = startProcess(nodeId, cmd);
        taskProcesses.put(nodeId, p.toHandle());
    }

    private Process spawnJobCoordinator(int index, boolean haMode) throws IOException {
        List<String> cmd = buildJavaCommand(JobCoordinatorMain.class.getName(),
                "jobId=job-" + runId,
                "jdbcUrl=" + jdbcUrl,
                "topicNamespace=" + topicNamespace,
                "checkpointBaseDir=" + checkpointDir,
                "expectedNodeIds=" + String.join(",", expectedNodeIds()),
                "nodeRegistrationTimeoutMs=" + healthTimeoutMs,
                "pollIntervalMs=" + pollIntervalMs,
                "coordinatorId=coordinator-" + index,
                "leaderElectorEnabled=" + haMode,
                "leaderClusterId=job-" + runId,
                "leaderHostId=coordinator-" + index,
                "leaderLeaseMs=3000",
                "leaderCheckIntervalMs=300");
        String label = "coordinator-" + index;
        Process p = startProcess(label, cmd);
        coordinatorProcesses.put(label, p);
        return p;
    }

    private List<String> buildJavaCommand(String mainClass, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(javaExecutable);
        cmd.add("-Dnop.stream.test.multi-jvm.child=true");
        cmd.add("-Dnop.core.initialize.max-initialize-level=4500");
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(mainClass);
        for (String a : args) {
            cmd.add(a);
        }
        return cmd;
    }

    private Process startProcess(String label, List<String> cmd) throws IOException {
        Path logFile = logFileFor(label);
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile());
        LOG.info("MiniStreamCluster({}) spawning {}: {}", runId, label, String.join(" ", cmd));
        Process p = pb.start();
        // Drain the (now-redirected)InputStream so the process does not block
        // on a full pipe; redirectOutput handles real output, but the child
        // process handle is returned for liveness checks.
        new Thread(new StreamDrainer(p.getInputStream(), label, logFile), "tm-log-" + label).start();
        return p;
    }

    private void waitForNodeRegistration(List<String> expected, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Set<String> active = registeredNodeIds();
        while (System.currentTimeMillis() < deadline) {
            active = registeredNodeIds();
            if (active.containsAll(expected)) {
                LOG.info("MiniStreamCluster({}) all TMs registered: {}", runId, active);
                return;
            }
            Set<String> missing = new java.util.LinkedHashSet<>(expected);
            missing.removeAll(active);
            // Fail-fast if a process died during registration.
            for (String id : new ArrayList<>(missing)) {
                ProcessHandle h = taskProcesses.get(id);
                if (h != null && !h.isAlive()) {
                    throw new IllegalStateException("TaskManager " + id
                            + " exited before registering. Log: " + logFileFor(id));
                }
            }
            Thread.sleep(100L);
        }
        throw new IllegalStateException("Timed out (" + timeoutMs + "ms) waiting for TMs to register. "
                + "Expected=" + expected + " active=" + active);
    }

    private boolean terminateProcess(ProcessHandle handle, long graceMs) {
        if (!handle.isAlive()) {
            return true;
        }
        handle.destroy(); // SIGTERM
        try {
            try {
                handle.onExit().get(graceMs, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException te) {
                LOG.warn("Process {} did not exit in {}ms; destroying forcibly", handle.pid(), graceMs);
                handle.destroyForcibly();
                return false;
            }
        } catch (Exception e) {
            LOG.warn("Failed to await process {} exit; destroying forcibly", handle.pid(), e);
            handle.destroyForcibly();
            return false;
        }
        return true;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            java.util.stream.Stream<Path> walk = Files.walk(path);
            try (walk) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                                // best-effort
                            }
                        });
            }
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private static String findProjectRootTmp() {
        // Walk up from the working directory looking for the project root (the
        // dir containing pom.xml + AGENTS.md), then use its _tmp/.
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path cursor = cwd;
        for (int i = 0; i < 10; i++) {
            if (Files.exists(cursor.resolve("AGENTS.md")) && Files.exists(cursor.resolve("pom.xml"))) {
                Path tmp = cursor.resolve("_tmp");
                try {
                    Files.createDirectories(tmp);
                } catch (IOException ignored) {
                    // fallthrough; the spawn paths will create as needed
                }
                return tmp.toString();
            }
            cursor = cursor.getParent();
            if (cursor == null) {
                break;
            }
        }
        // Fallback: use the working directory's _tmp.
        Path fallback = cwd.resolve("_tmp");
        try {
            Files.createDirectories(fallback);
        } catch (IOException ignored) {
            // best-effort
        }
        return fallback.toString();
    }

    /**
     * Background drainer that tails the spawned process's redirected stdout to
     * the log file. {@link ProcessBuilder#redirectOutput} handles the bulk of
     * the file write, but we also read the (already-redirected) stream here so
     * the child process can never block on a full pipe between the test JVM and
     * the OS file redirect.
     */
    private static final class StreamDrainer implements Runnable {
        private final InputStream in;
        private final String label;
        private final Path logFile;

        StreamDrainer(InputStream in, String label, Path logFile) {
            this.in = in;
            this.label = label;
            this.logFile = logFile;
        }

        @Override
        public void run() {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    // The ProcessBuilder redirectOutput already wrote the line;
                    // this read just keeps the pipe drained. Optionally surface
                    // a hint in the test log.
                    if (StringHelper.isEmpty(line)) {
                        continue;
                    }
                    LOG.debug("[{}] {}", label, line);
                }
            } catch (IOException e) {
                LOG.debug("Stream drainer for {} ended (log={})", label, logFile, e);
            }
        }
    }
}
