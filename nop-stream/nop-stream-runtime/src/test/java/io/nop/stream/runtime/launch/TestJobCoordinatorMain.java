/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.launch;

import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.testsupport.TestAwait;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.concurrent.atomic.AtomicReference;

import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.stream.runtime.cluster.JdbcClusterRegistry;
import io.nop.stream.runtime.coordinator.JobCoordinator;
import io.nop.stream.runtime.taskmanager.TaskManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 42 Phase 1: verifies {@link JobCoordinatorMain} as a standalone JVM
 * entry point (plan guide #11/#22/#24 — anti-hollow, 接线验证, fail-fast).
 *
 * <p>The "trivial job deploys via deployTask RPC" exit criterion is exercised
 * in-process: one {@link TaskManagerMain} + one {@link JobCoordinatorMain}
 * share a real H2 backing store; the coordinator's {@code assignTasks()} fires
 * a real {@code deployTask} RPC over the shared message service; the TaskManager
 * receives it. This is the in-process analog of what {@code MiniStreamCluster}
 * (Phase 2) spawns as separate JVMs.
 */
class TestJobCoordinatorMain {

    @TempDir
    java.nio.file.Path tempDir;

    private String jdbcUrl;
    private String jobId;
    private String topicNamespace;
    private String checkpointBaseDir;
    private String nodeId;
    private TaskManagerMain taskMain;

    @BeforeAll
    static void initCore() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroyCore() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        String dbFile = tempDir.resolve("jc-main-test-" + StringHelper.generateUUID()).toString();
        jdbcUrl = "jdbc:h2:file:" + dbFile + ";MODE=MySQL";
        jobId = "job-" + StringHelper.generateUUID().substring(0, 8);
        topicNamespace = "ns-" + StringHelper.generateUUID().substring(0, 8);
        checkpointBaseDir = tempDir.resolve("checkpoints").toString();
        nodeId = "tm-0";
    }

    @AfterEach
    void tearDown() {
        if (taskMain != null) {
            taskMain.shutdown();
            taskMain = null;
        }
    }

    @Test
    void coordinatorStartFailsFastOnMissingJobId() {
        ClusterLaunchConfig config = ClusterLaunchConfig.parse(new String[]{
                "jdbcUrl=" + jdbcUrl, "topicNamespace=" + topicNamespace});
        JobCoordinatorMain main = new JobCoordinatorMain(config);
        assertThrows(StreamException.class, main::start,
                "Missing jobId must fail fast (#24)");
    }

    @Test
    void coordinatorStartFailsFastOnMissingCheckpointBaseDir() {
        ClusterLaunchConfig config = ClusterLaunchConfig.parse(new String[]{
                "jobId=" + jobId, "jdbcUrl=" + jdbcUrl, "topicNamespace=" + topicNamespace});
        JobCoordinatorMain main = new JobCoordinatorMain(config);
        assertThrows(StreamException.class, main::start,
                "Missing checkpointBaseDir must fail fast (#24)");
    }

    @Test
    void coordinatorDeploysViaDeployTaskRpcToRegisteredTaskManager() throws Exception {
        // 1. Start a TaskManager on the shared backing store.
        ClusterLaunchConfig tmConfig = ClusterLaunchConfig.parse(new String[]{
                "nodeId=" + nodeId,
                "jdbcUrl=" + jdbcUrl,
                "topicNamespace=" + topicNamespace,
                "pollIntervalMs=20"});
        taskMain = new TaskManagerMain(tmConfig);
        TaskManager tm = taskMain.start();
        assertTrue(tm.isRunning(), "TaskManager must be running before coordinator starts");

        // 2. Start the coordinator, expecting the registered TaskManager.
        //    assignTasks() must fire deployTask RPC at the TaskManager.
        ClusterLaunchConfig jcConfig = ClusterLaunchConfig.parse(new String[]{
                "jobId=" + jobId,
                "jdbcUrl=" + jdbcUrl,
                "topicNamespace=" + topicNamespace,
                "checkpointBaseDir=" + checkpointBaseDir,
                "expectedNodeIds=" + nodeId,
                "nodeRegistrationTimeoutMs=5000",
                "pollIntervalMs=20"});
        JobCoordinatorMain main = new JobCoordinatorMain(jcConfig);
        try {
            JobCoordinator coordinator = main.start();
            assertNotNull(coordinator, "Coordinator must be live");
            assertTrue(coordinator.isRemoteDeployMode(),
                    "Coordinator must be in remote-deploy mode (Phase 0 integration)");
            assertEquals(jobId, coordinator.getJobId());
            assertNotNull(coordinator.getJobGraph(),
                    "JobGraph must be injected so assignTasks() can build descriptors");

            // The assignTasks() call inside start() fired deployTask RPC over
            // the shared message service; the TaskManager receives it. The
            // deployTask path reconstructs the invokable locally — even though
            // the trivial placeholder JobGraph has no real operators, the RPC
            // wiring is genuinely exercised.
            assertTrue(coordinator.getTaskAssignments().containsKey("source"),
                    "assignTasks() must have run: source vertex assigned");
            assertTrue(coordinator.getTaskAssignments().containsKey("sink"),
                    "assignTasks() must have run: sink vertex assigned");
        } finally {
            main.shutdown();
        }
    }

    @Test
    void coordinatorShutdownIsIdempotent() throws Exception {
        // Single-node happy path; then double shutdown.
        ClusterLaunchConfig tmConfig = ClusterLaunchConfig.parse(new String[]{
                "nodeId=" + nodeId,
                "jdbcUrl=" + jdbcUrl,
                "topicNamespace=" + topicNamespace,
                "pollIntervalMs=20"});
        taskMain = new TaskManagerMain(tmConfig);
        taskMain.start();

        ClusterLaunchConfig jcConfig = ClusterLaunchConfig.parse(new String[]{
                "jobId=" + jobId,
                "jdbcUrl=" + jdbcUrl,
                "topicNamespace=" + topicNamespace,
                "checkpointBaseDir=" + checkpointBaseDir,
                "expectedNodeIds=" + nodeId,
                "nodeRegistrationTimeoutMs=5000",
                "pollIntervalMs=20"});
        JobCoordinatorMain main = new JobCoordinatorMain(jcConfig);
        main.start();

        // Double shutdown must not throw.
        main.shutdown();
        main.shutdown();
    }

    @Test
    void coordinatorWaitsForTaskManagerRegistration() throws Exception {
        // Coordinator starts BEFORE the TaskManager; nodeRegistrationTimeoutMs
        // must be honored. 时间确定化（plan 2510）：不再用固定 500ms 延后启动
        // TaskManager——先把 start() 放到独立线程并以「线程已到达注册等待阻塞点」
        // （untilThreadParked，pollIntervalMs 轮询下的 TIMED_WAITING）为结构证据
        // 确认协调器确实在等待注册、且尚未返回，再启动 TaskManager 放行，
        // 启动顺序由显式同步而非时间窗决定。
        ClusterLaunchConfig jcConfig = ClusterLaunchConfig.parse(new String[]{
                "jobId=" + jobId,
                "jdbcUrl=" + jdbcUrl,
                "topicNamespace=" + topicNamespace,
                "checkpointBaseDir=" + checkpointBaseDir,
                "expectedNodeIds=" + nodeId,
                "nodeRegistrationTimeoutMs=8000",
                "pollIntervalMs=20"});

        AtomicReference<JobCoordinator> started = new AtomicReference<>(null);
        Thread startThread = new Thread(() -> {
            JobCoordinatorMain main = new JobCoordinatorMain(jcConfig);
            try {
                started.set(main.start());
            } catch (Exception e) {
                // surfaced via the started reference below
            } finally {
                mains.add(main);
            }
        }, "jc-registration-wait-start");
        startThread.setDaemon(true);
        startThread.start();

        try {
            // 结构证据：start() 线程已进入注册等待阻塞点，且尚未返回协调器。
            TestAwait.untilThreadParked("coordinator start() blocked on registration wait", startThread);
            assertNull(started.get(),
                    "start() must not return before the expected TaskManager registers");

            // 放行：TaskManager 注册 → start() 返回。
            ClusterLaunchConfig tmConfig = ClusterLaunchConfig.parse(new String[]{
                    "nodeId=" + nodeId,
                    "jdbcUrl=" + jdbcUrl,
                    "topicNamespace=" + topicNamespace,
                    "pollIntervalMs=20"});
            taskMain = new TaskManagerMain(tmConfig);
            taskMain.start();

            TestAwait.until("coordinator start() returned after the TaskManager registered",
                    () -> started.get() != null);
            assertNotNull(started.get(),
                    "Coordinator must start after the TaskManager registers within timeout");
        } finally {
            for (JobCoordinatorMain main : mains) {
                try {
                    main.shutdown();
                } catch (Exception ignored) {
                    // best-effort teardown
                }
            }
            mains.clear();
        }
    }

    /** Mains created on the start thread (shutdown from the test thread in finally). */
    private final java.util.List<JobCoordinatorMain> mains =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());
}
