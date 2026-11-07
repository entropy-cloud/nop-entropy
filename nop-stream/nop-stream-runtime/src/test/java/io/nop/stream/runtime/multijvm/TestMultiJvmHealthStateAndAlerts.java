/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.multijvm;

import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.nop.stream.core.testsupport.TestAwait;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 16 (P-REQ-7/12) multi-JVM e2e (acceptance: 监听器在 MiniStreamCluster
 * e2e 中被断言调用): kills a REAL TaskManager process and asserts, in the
 * REAL coordinator process log, the full observability chain fired by the
 * real failure-detection → globalRecovery path:
 *
 * <ol>
 *   <li>health machine transitions {@code RUNNING -> RECOVERING -> DEGRADED}
 *       (each logged as {@code job health transition: ...});</li>
 *   <li>the health-listener bridge fired {@code JOB_DEGRADED} on the job
 *       event bus (observable via the built-in logging listener line);</li>
 *   <li>the alert service (P-REQ-12, wired by JobCoordinatorMain with the
 *       logging channel) delivered the {@code RECOVERY_STARTED} WARN alert
 *       ({@code nop-stream alert:} prefix).</li>
 * </ol>
 *
 * <p>Gated by {@code -Dnop.stream.test.multi-jvm.enabled=true} (Stage 42
 * convention: spawning real JVMs is slow and environment-sensitive).
 *
 * <p>时间确定化（plan 2510，gated multijvm 边界）：虚拟时钟无法跨越 spawned-JVM 边界；
 * 全部等待改为对 coordinator 子进程日志（外部可观测状态）的条件等待，超时上限仅挂死
 * 保护。租约到期 + failure-detector tick + 恢复的真实时长保留为条件的自然等待时长。
 */
@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")
class TestMultiJvmHealthStateAndAlerts {

    /** Node-lease expiry (15s default) + detection tick + recovery — generous bound. */
    private static final long RECOVERY_TIMEOUT_MS = 90_000L;

    @Test
    void killTaskManagerDrivesHealthTransitionsAndAlertsInCoordinatorProcess() throws Exception {
        try (MiniStreamCluster cluster = new MiniStreamCluster(2, 60_000L, 10_000L, 100L)) {
            cluster.start();

            String jobId = cluster.getJobId();
            String coordinatorLog = "coordinator-0";

            // wait until the coordinator really assigned tasks (killing before
            // assignment would test nothing)
            awaitCondition(() -> coordinatorLogContains(cluster, coordinatorLog,
                    "Assigned"), 30_000L, "initial assignment");

            // kill a real TaskManager process — the node lease expires and the
            // failure detector fires globalRecovery in the coordinator JVM
            String victim = cluster.expectedNodeIds().get(1);
            assertTrue(cluster.killTaskManager(victim), "must kill " + victim);

            awaitCondition(() -> coordinatorLogContains(cluster, coordinatorLog,
                    "job health transition: job=" + jobId + " RUNNING -> RECOVERING"),
                    RECOVERY_TIMEOUT_MS, "RUNNING -> RECOVERING transition");
            awaitCondition(() -> coordinatorLogContains(cluster, coordinatorLog,
                    "job health transition: job=" + jobId + " RECOVERING -> DEGRADED"),
                    RECOVERY_TIMEOUT_MS, "RECOVERING -> DEGRADED transition");
            awaitCondition(() -> coordinatorLogContains(cluster, coordinatorLog,
                    "type=JOB_DEGRADED job=" + jobId),
                    RECOVERY_TIMEOUT_MS, "JOB_DEGRADED event from the health listener bridge");
            awaitCondition(() -> coordinatorLogContains(cluster, coordinatorLog,
                    "nop-stream alert: job=" + jobId + " severity=WARN type=RECOVERY_STARTED"),
                    RECOVERY_TIMEOUT_MS, "RECOVERY_STARTED alert via the logging channel");

            // the recovered cluster still hosts the job (the other TM took over)
            assertTrue(cluster.taskManagerAlive(cluster.expectedNodeIds().get(0)),
                    "the surviving TaskManager must stay alive after recovery");
        }
    }

    private static boolean coordinatorLogContains(MiniStreamCluster cluster, String who, String text)
            throws java.io.IOException {
        return Files.readString(cluster.logFileFor(who)).contains(text);
    }

    private interface Condition {
        boolean satisfied() throws java.io.IOException;
    }

    /**
     * 条件等待（{@link TestAwait#until}）：条件 = coordinator 子进程日志包含目标文本
     * （外部可观测状态）；日志文件尚未创建时继续轮询，超时上限仅挂死保护。
     */
    private static void awaitCondition(Condition condition, long timeoutMs, String what) {
        AtomicReference<java.io.IOException> lastFailure = new AtomicReference<>();
        try {
            TestAwait.until(what, () -> {
                try {
                    return condition.satisfied();
                } catch (java.io.IOException e) {
                    // log file may not exist yet — keep polling until the cap
                    lastFailure.set(e);
                    return false;
                }
            }, timeoutMs);
        } catch (AssertionError e) {
            throw new IllegalStateException("timed out waiting for " + what
                    + (lastFailure.get() == null ? "" : " (last io failure: " + lastFailure.get() + ")"), e);
        }
    }
}
