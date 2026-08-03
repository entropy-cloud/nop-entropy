/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.multijvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 42 Phase 2: verifies {@link MiniStreamCluster} as a process orchestration
 * harness. <strong>Gated by {@code -Dnop.stream.test.multi-jvm.enabled=true}</strong>
 * so the default {@code ./mvnw test} suite does NOT pay the JVM-spawn cost.
 *
 * <p>When enabled, this test:
 * <ol>
 *   <li>starts a {@code MiniStreamCluster} with 2 TaskManager JVMs + 1
 *       JobCoordinator JVM;</li>
 *   <li>asserts all become healthy (registered in the shared
 *       {@code JdbcClusterRegistry}) within 30s;</li>
 *   <li>verifies {@code killTaskManager(nodeId)} stops exactly one process;</li>
 *   <li>verifies {@code restartTaskManager(nodeId)} relaunches it and it
 *       re-registers;</li>
 *   <li>verifies process logs are captured with process identity;</li>
 *   <li>verifies {@code shutdown()} stops everything cleanly.</li>
 * </ol>
 *
 * <p>Run manually:
 * <pre>
 *   ./mvnw test -pl nop-stream/nop-stream-runtime -am -T 1C \
 *     -Dtest=TestMiniStreamClusterProcessSpawn \
 *     -Dnop.stream.test.multi-jvm.enabled=true \
 *     -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")
class TestMiniStreamClusterProcessSpawn {

    @Test
    void twoTaskManagersAndOneCoordinatorStartAndRegister() throws Exception {
        try (MiniStreamCluster cluster = new MiniStreamCluster(2)) {
            cluster.start();

            // Health-check: both TaskManagers registered in the shared registry.
            Set<String> registered = cluster.registeredNodeIds();
            assertTrue(registered.containsAll(List.of("tm-0", "tm-1")),
                    "Expected both TaskManagers registered, got " + registered);

            // The coordinator process is alive.
            assertTrue(cluster.coordinatorAlive(),
                    "Coordinator process must be alive after start()");

            // Logs are captured per process.
            assertTrue(Files.exists(cluster.logFileFor("tm-0")),
                    "tm-0 log file must exist: " + cluster.logFileFor("tm-0"));
            assertTrue(Files.exists(cluster.logFileFor("tm-1")),
                    "tm-1 log file must exist");
            assertTrue(Files.exists(cluster.logFileFor("coordinator-0")),
                    "coordinator log file must exist");

            // Kill one TM and verify it stops.
            boolean killed = cluster.killTaskManager("tm-0");
            assertTrue(killed, "killTaskManager must report success when it stopped a live process");
            // Give the registry a brief moment to expire the lease is NOT needed
            // — killTaskManager only ensures the process is gone; the lease
            // remains until expiry. The next restart re-registers.
            assertFalse(cluster.taskManagerAlive("tm-0"),
                    "tm-0 process must be gone after killTaskManager");

            // Restart it and verify it re-registers.
            cluster.restartTaskManager("tm-0");
            assertTrue(cluster.taskManagerAlive("tm-0"),
                    "tm-0 process must be alive after restartTaskManager");

            // Wait for re-registration via health-check polling.
            long deadline = System.currentTimeMillis() + 10_000L;
            boolean reRegistered = false;
            while (System.currentTimeMillis() < deadline) {
                if (cluster.registeredNodeIds().contains("tm-0")) {
                    reRegistered = true;
                    break;
                }
                TimeUnit.MILLISECONDS.sleep(100L);
            }
            assertTrue(reRegistered,
                    "tm-0 must re-register after restart; logs: " + cluster.logFileFor("tm-0"));
        }
    }

    @Test
    void shutdownStopsAllProcesses() throws Exception {
        MiniStreamCluster cluster = new MiniStreamCluster(2);
        cluster.start();
        assertTrue(cluster.coordinatorAlive());

        cluster.shutdown();

        assertFalse(cluster.coordinatorAlive(),
                "Coordinator must not be alive after shutdown");
        for (String id : cluster.expectedNodeIds()) {
            assertFalse(cluster.taskManagerAlive(id),
                    id + " must not be alive after shutdown");
        }
    }

    @Test
    void topicNamespaceIsUniquePerInstance() {
        MiniStreamCluster a = new MiniStreamCluster(1);
        MiniStreamCluster b = new MiniStreamCluster(1);
        try {
            assertFalse(a.getTopicNamespace().equals(b.getTopicNamespace()),
                    "Two MiniStreamCluster instances must get distinct topic namespaces "
                            + "(a=" + a.getTopicNamespace() + ", b=" + b.getTopicNamespace() + ")");
            assertNotNull(a.getJdbcUrl());
            assertNotNull(b.getJdbcUrl());
            assertFalse(a.getJdbcUrl().equals(b.getJdbcUrl()),
                    "Two MiniStreamCluster instances must use distinct DB files");
        } finally {
            a.shutdown();
            b.shutdown();
        }
    }
}
