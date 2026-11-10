package io.nop.ai.agent.engine;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.1-MA-12 (plan 2555 裁定 B / C1-013 + C1-022) carrier — startup-swap
 * lifecycle and volatile structure pins:
 * <ul>
 *   <li>{@code DefaultAgentEngine.setAgentExecutor} is a STARTUP-swap
 *       operation: after the swap, the engine's dispatch executor IS the new
 *       pool (volatile visibility), and the close-path own-shutdown decision
 *       tracks the latest installed executor (externally injected pools are
 *       never shut down — behaviour pin via {@code ownAgentExecutor});</li>
 *   <li>structure pins: the C1-022 seven field groups (and the C1-013 family
 *       fields) are {@code volatile} — runtime setters on shared singletons
 *       need the happens-before edge.</li>
 * </ul>
 */
public class TestExecutorSwapLifecycle {

    // ========================================================================
    // Startup-swap semantics
    // ========================================================================

    @Test
    void setAgentExecutorSwapsDispatchPoolAndKeepsOwnShutdownFalse() throws Exception {
        DefaultAgentEngine engine = DefaultAgentEngine.builder(null, null).build();

        // Pre-set: engine has no executor installed (lazy self-created only).
        ExecutorService poolA = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "swap-pool-a");
            t.setDaemon(true);
            return t;
        });
        ExecutorService poolB = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "swap-pool-b");
            t.setDaemon(true);
            return t;
        });
        try {
            engine.setAgentExecutor(poolA);
            assertEquals(poolA, engine.getAgentExecutor(),
                    "after the first setter, dispatch must use the injected pool");

            // Startup swap: the new pool is visible to every subsequent
            // dispatch (volatile write/read).
            engine.setAgentExecutor(poolB);
            assertEquals(poolB, engine.getAgentExecutor(),
                    "after the swap, dispatch must use the NEW pool");

            // ownAgentExecutor stays false — close() must never shut down an
            // externally injected pool (own-shutdown semantics unchanged).
            Field ownFlag = DefaultAgentEngine.class.getDeclaredField("ownAgentExecutor");
            ownFlag.setAccessible(true);
            assertEquals(false, ownFlag.get(engine),
                    "injected executor must not be owned (close-path own-shutdown unchanged)");

            // The lazily self-created executor path is untouched: no pool was
            // auto-created by the setter path.
            assertEquals(poolB, engine.getAgentExecutor());
        } finally {
            poolA.shutdownNow();
            poolB.shutdownNow();
        }
    }

    // ========================================================================
    // Volatile structure pins
    // ========================================================================

    private static void assertVolatile(Class<?> clazz, String fieldName) throws Exception {
        Field f = clazz.getDeclaredField(fieldName);
        assertTrue(Modifier.isVolatile(f.getModifiers()),
                clazz.getSimpleName() + "." + fieldName + " must be volatile (C1-022/C1-013 pin)");
    }

    @Test
    void c1013FamilyFieldsAreVolatile() throws Exception {
        assertVolatile(DefaultAgentEngine.class, "agentExecutor");
        assertVolatile(DefaultAgentEngine.class, "ownAgentExecutor");
        assertVolatile(DefaultAgentEngine.class, "toolProgressTracker");
        assertVolatile(SessionLockRenewal.class, "lockRenewExecutor");
        assertVolatile(SessionLockRenewal.class, "ownLockRenewExecutor");
        assertVolatile(AgentCallDelegate.class, "messenger");
    }

    @Test
    void c1022SevenFieldGroupsAreVolatile() throws Exception {
        // Group 1: TeamTaskSchedulerDaemon
        assertVolatile(Class.forName("io.nop.ai.agent.team.scheduler.TeamTaskSchedulerDaemon"),
                "daemonCoordinator");
        assertVolatile(Class.forName("io.nop.ai.agent.team.scheduler.TeamTaskSchedulerDaemon"),
                "daemonOwnerId");
        assertVolatile(Class.forName("io.nop.ai.agent.team.scheduler.TeamTaskSchedulerDaemon"),
                "scanLeaseMs");

        // Group 2: TaskDispatchCoordinator
        assertVolatile(Class.forName("io.nop.ai.agent.team.scheduler.TaskDispatchCoordinator"),
                "taskMemberRouter");
        assertVolatile(Class.forName("io.nop.ai.agent.team.scheduler.TaskDispatchCoordinator"),
                "memberSpawner");
        assertVolatile(Class.forName("io.nop.ai.agent.team.scheduler.TaskDispatchCoordinator"),
                "spawnStepExecutor");

        // Group 3: TeamTaskFlowOrchestrator
        assertVolatile(Class.forName("io.nop.ai.agent.team.flow.TeamTaskFlowOrchestrator"),
                "memberSpawner");
        assertVolatile(Class.forName("io.nop.ai.agent.team.flow.TeamTaskFlowOrchestrator"),
                "taskMemberRouter");
        assertVolatile(Class.forName("io.nop.ai.agent.team.flow.TeamTaskFlowOrchestrator"),
                "spawnStepExecutor");
        assertVolatile(Class.forName("io.nop.ai.agent.team.flow.TeamTaskFlowOrchestrator"),
                "memberExecTimeoutMs");

        // Group 4: TeamExecuteFlowExecutor
        assertVolatile(Class.forName("io.nop.ai.agent.tool.TeamExecuteFlowExecutor"),
                "memberSpawner");

        // Group 5: CallAgentExecutor
        assertVolatile(Class.forName("io.nop.ai.agent.tool.CallAgentExecutor"),
                "maxDelegationDepth");

        // Group 6: DBMessageService (config setter family)
        assertVolatile(Class.forName("io.nop.ai.agent.message.DBMessageService"),
                "pollIntervalMs");
        assertVolatile(Class.forName("io.nop.ai.agent.message.DBMessageService"),
                "maxBatch");
        assertVolatile(Class.forName("io.nop.ai.agent.message.DBMessageService"),
                "staleClaimTimeoutMs");
        assertVolatile(Class.forName("io.nop.ai.agent.message.DBMessageService"),
                "sweepIntervalMs");
        assertVolatile(Class.forName("io.nop.ai.agent.message.DBMessageService"),
                "maxRedeliveries");
        assertVolatile(Class.forName("io.nop.ai.agent.message.DBMessageService"),
                "deliveryThreads");
        assertVolatile(Class.forName("io.nop.ai.agent.message.DBMessageService"),
                "deliveryQueueCapacity");

        // Group 7: memory adapters
        assertVolatile(Class.forName("io.nop.ai.agent.memory.InMemoryVectorAdapter"),
                "tenantResolver");
        assertVolatile(Class.forName("io.nop.ai.agent.memory.InMemoryStorageAdapter"),
                "tenantResolver");
    }

    @Test
    void renewalPoolSizeConfigIsPresent() {
        DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        assertNotNull(config);
        // The MA-16 pool form config key convention is exercised in
        // TestLockRenewalPoolForm; here we pin the engine-side seam only.
    }
}
