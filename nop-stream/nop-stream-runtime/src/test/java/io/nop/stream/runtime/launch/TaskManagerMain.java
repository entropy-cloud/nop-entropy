/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.launch;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.message.IMessageService;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.JdbcClusterRegistry;
import io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService;
import io.nop.stream.runtime.rpc.StreamControlRpcProxyFactory;
import io.nop.stream.runtime.rpc.StreamControlRpcServer;
import io.nop.stream.runtime.rpc.StreamControlRpcTopics;
import io.nop.stream.runtime.taskmanager.TaskManager;

/**
 * Stage 42 Phase 1: standalone JVM entry point for a {@link TaskManager}.
 *
 * <p>Launched by the {@code MiniStreamCluster} test harness (Phase 2) as:
 * <pre>
 *   java -cp &lt;test-classpath&gt; io.nop.stream.runtime.launch.TaskManagerMain
 *        nodeId=tm-1
 *        jdbcUrl=jdbc:h2:file:/tmp/cluster.db;AUTO_SERVER=TRUE;MODE=MySQL
 *        topicNamespace=run-2026-08-03-001
 *        checkpointBaseDir=/tmp/nop-stream-checkpoints
 *        capacity=16
 * </pre>
 *
 * <p>The process connects to the shared H2 DB (used for both
 * {@link JdbcClusterRegistry} and {@link PollingJdbcMessageService}), registers
 * its node, exposes its {@link io.nop.stream.runtime.rpc.IStreamTaskRpcService}
 * over the control-plane RPC, builds an RPC proxy to the coordinator, and waits
 * for task deployment via the {@code deployTask} RPC (Phase 0). SIGTERM/SIGINT
 * triggers graceful shutdown and exits with code 0. Missing required config
 * fails fast with stderr message and non-zero exit code (plan guide #24).
 *
 * <p><strong>接线验证</strong>: this is a real {@code public static void main}
 * invoked by the {@code MiniStreamCluster} harness via {@code ProcessBuilder}.
 */
public final class TaskManagerMain {

    private static final Logger LOG = LoggerFactory.getLogger(TaskManagerMain.class);

    private final ClusterLaunchConfig config;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private final AtomicReference<Throwable> startError = new AtomicReference<>();

    private SharedJdbcInfrastructure jdbc;
    private PollingJdbcMessageService messageService;
    private JdbcClusterRegistry clusterRegistry;
    private TaskManager taskManager;
    private StreamControlRpcServer taskServer;
    private StreamControlRpcProxyFactory coordinatorProxy;

    public TaskManagerMain(ClusterLaunchConfig config) {
        this.config = config;
    }

    /**
     * Bootstraps the TaskManager process. Visible for testing (in-process launch
     * without spawning a JVM).
     *
     * @return the started {@link TaskManager} (for diagnostics / in-process tests)
     */
    public TaskManager start() {
        // Fail-fast on missing required config (plan guide #24 — no silent no-op).
        String nodeId = config.require(ClusterLaunchConfig.KEY_NODE_ID);
        String jdbcUrl = config.require(ClusterLaunchConfig.KEY_JDBC_URL);
        String topicNamespace = config.require(ClusterLaunchConfig.KEY_TOPIC_NAMESPACE);
        LOG.info("TaskManagerMain starting: nodeId={} jdbcUrl={} topicNamespace={}",
                nodeId, jdbcUrl, topicNamespace);

        int capacity = config.getInt(ClusterLaunchConfig.KEY_CAPACITY, 16);
        long fencingEpoch = config.getLong(ClusterLaunchConfig.KEY_FENCING_EPOCH, 0L);
        long pollIntervalMs = config.getLong(ClusterLaunchConfig.KEY_POLL_INTERVAL_MS, 50L);

        jdbc = new SharedJdbcInfrastructure(config);
        messageService = new PollingJdbcMessageService(jdbc.getJdbcTemplate(), pollIntervalMs);
        messageService.initialize();
        clusterRegistry = new JdbcClusterRegistry(jdbc.getJdbcTemplate());

        String endpoint = "rpc:" + nodeId;
        String controlTopic = StreamControlRpcTopics.coordinatorTopic(topicNamespace);
        taskManager = new TaskManager(nodeId, endpoint, capacity, messageService, clusterRegistry, controlTopic);
        if (fencingEpoch > 0L) {
            taskManager.updateFencingToken(fencingEpoch);
        }
        taskManager.start();
        // Fire an initial heartbeat so the node is immediately visible to a
        // coordinator polling the shared ClusterRegistry. The periodic loop
        // inside TaskManager.start() has a 5s initial delay — this front-loads
        // the first lease renewal so a freshly-launched TaskManager registers
        // within milliseconds (multi-JVM test harness readiness contract).
        taskManager.heartbeat();

        // Expose IStreamTaskRpcService over the control-plane RPC. The task
        // topic is namespaced by topicNamespace so concurrent test runs do not
        // collide.
        String taskRpcTopic = taskRpcTopic(topicNamespace, nodeId);
        taskServer = new StreamControlRpcServer(
                "streamTaskRpc@" + nodeId,
                io.nop.stream.runtime.rpc.IStreamTaskRpcService.class,
                taskManager,
                messageService,
                taskRpcTopic);
        taskServer.start();

        // Build an RPC proxy to the coordinator and wire it back so the task
        // can send checkpoint ACKs / status reports.
        coordinatorProxy = new StreamControlRpcProxyFactory(
                "streamCoordinatorRpc@" + topicNamespace,
                IStreamCoordinatorRpcService.class,
                messageService,
                StreamControlRpcTopics.coordinatorTopic(topicNamespace));
        coordinatorProxy.start();
        taskManager.setCoordinatorRpcService(coordinatorProxy.getProxy());

        LOG.info("TaskManagerMain started (nodeId={}, rpcTopic={}, registered=true)", nodeId, taskRpcTopic);
        return taskManager;
    }

    /**
     * Blocks the calling thread (typically {@code main}) until shutdown is
     * triggered via {@link #shutdown()} or the JVM SIGTERM hook.
     */
    public void awaitShutdown() throws InterruptedException {
        shutdownLatch.await();
    }

    /**
     * Graceful shutdown — invoked by the SIGTERM hook or programmatically.
     */
    public synchronized void shutdown() {
        LOG.info("TaskManagerMain shutting down");
        try {
            if (taskServer != null) {
                taskServer.stop();
            }
        } catch (Exception e) {
            LOG.warn("Failed to stop task RPC server", e);
        }
        try {
            if (coordinatorProxy != null) {
                coordinatorProxy.stop();
            }
        } catch (Exception e) {
            LOG.warn("Failed to stop coordinator RPC proxy", e);
        }
        try {
            if (taskManager != null) {
                taskManager.stop();
            }
        } catch (Exception e) {
            LOG.warn("Failed to stop TaskManager", e);
        }
        try {
            if (messageService != null) {
                messageService.close();
            }
        } catch (Exception e) {
            LOG.warn("Failed to close message service", e);
        }
        try {
            if (jdbc != null) {
                jdbc.close();
            }
        } catch (Exception e) {
            LOG.warn("Failed to close JDBC infrastructure", e);
        }
        shutdownLatch.countDown();
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public IMessageService getMessageService() {
        return messageService;
    }

    public static String taskRpcTopic(String topicNamespace, String nodeId) {
        return StreamControlRpcTopics.taskTopic(topicNamespace + "." + nodeId);
    }

    // ==================== main entry point ====================

    public static void main(String[] args) {
        // Initialize nop-dao (registers the H2 dialect manager) so the spawned
        // child process can resolve a dialect for its H2 connection. The harness
        // JVM does this in its test setup; the spawned JVM is a separate process
        // and must bootstrap the same initialization.
        io.nop.core.initialize.CoreInitialization.initialize();

        ClusterLaunchConfig config;
        try {
            config = ClusterLaunchConfig.parse(args);
        } catch (IllegalArgumentException e) {
            // Fail-fast (#24): stderr + non-zero exit code.
            LOG.error("TaskManagerMain config error", e);
            System.err.println(usage());
            System.exit(2);
            return; // unreachable
        }

        TaskManagerMain main = new TaskManagerMain(config);
        // SIGTERM / SIGINT hook → graceful shutdown → exit 0.
        Thread shutdownHook = new Thread(() -> {
            try {
                main.shutdown();
            } catch (Exception ignored) {
                // Best-effort; exit code stays 0 for SIGTERM-initiated shutdown.
            }
        }, "tm-shutdown-hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        try {
            main.start();
            main.awaitShutdown();
            System.exit(0);
        } catch (Throwable t) {
            // Start-time failure or fatal runtime error.
            System.err.println("TaskManagerMain fatal error: " + t);
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public static String usage() {
        return "Usage: TaskManagerMain nodeId=<id> jdbcUrl=<h2-url>"
                + " topicNamespace=<ns> [capacity=<n>] [checkpointBaseDir=<path>]"
                + " [fencingEpoch=<n>] [pollIntervalMs=<n>]";
    }
}
