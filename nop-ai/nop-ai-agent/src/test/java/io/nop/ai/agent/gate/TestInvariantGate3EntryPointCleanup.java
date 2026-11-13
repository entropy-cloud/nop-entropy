package io.nop.ai.agent.gate;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate ③ (INV-3, plan 2026-08-12-1120-2 Phase 3): every resource entry-point
 * of the I0 target set table §3.4 must have a symmetric acquire/release pair.
 *
 * <p>Declaration criterion (I1 Phase 1 Decision D6): the entry-point row's
 * acquire marker and its symmetric release marker must both exist in the
 * specified source files (live line-anchored). Behaviour-level symmetry
 * verification (the failure path really releases everything and the sessionId
 * is re-executable) is the existing {@code TestEngineEntryCleanupSymmetry}
 * baseline plus the I2 wiring spot-checks — the gate verifies declaration
 * presence only.
 *
 * <p>Table completeness: the §3.4 reproduction command families are re-run
 * mechanically — each command's pattern must still hit its expected file —
 * and diffed against the gate table rows. New entry-point families without a
 * derivable pattern are covered by the I2 adversarial probing (out of the
 * gate's mechanical scope).
 *
 * <p>First-run verdict: 8/8 acquire/release pairs present (plan 278 repair
 * surface) — zero gaps.
 */
public class TestInvariantGate3EntryPointCleanup {

    private static final String GAP_FAMILY = "gate-3-entry-point-cleanup";
    private static final String MODULE = "nop-ai-agent";

    /** Markers that must be present in one file of an entry-point row. */
    static final class FileMarkers {
        final String file;
        final List<String> markers;

        FileMarkers(String file, List<String> markers) {
            this.file = file;
            this.markers = markers;
        }
    }

    /** I0 target set table §3.4 row (project source level = 11, no records). */
    static final class EntryPoint {
        final String id;
        final List<FileMarkers> checks;

        EntryPoint(String id, List<FileMarkers> checks) {
            this.id = id;
            this.checks = checks;
        }

        static FileMarkers in(String file, String... markers) {
            return new FileMarkers(file, List.of(markers));
        }
    }

    /**
     * I0 target set table §3.4 (8 rows). Each row lists the acquire/start and
     * symmetric release/stop markers per file; every marker must be present.
     */
    static final List<EntryPoint> TABLE = List.of(
            new EntryPoint("session-execution-registration",
                    List.of(EntryPoint.in("engine/DefaultAgentEngine.java",
                            "runningExecutions.putIfAbsent", "runningExecutions.remove"))),
            new EntryPoint("session-takeover-lock",
                    List.of(EntryPoint.in("engine/DefaultAgentEngine.java",
                            "tryAcquire", "releaseLockQuietly"))),
            new EntryPoint("heartbeat-renewal",
                    List.of(EntryPoint.in("engine/SessionLockRenewal.java",
                            "startLockRenewal", "cancelLockRenewalQuietly"))),
            new EntryPoint("engine-entry-inner-try",
                    List.of(EntryPoint.in("engine/DefaultAgentEngine.java",
                                    "createActor", "autoBindTeam", "finally"),
                            EntryPoint.in("engine/AgentSessionLifecycle.java",
                                    "createActor", "autoBindTeam", "finally"))),
            new EntryPoint("checkpoint-removal",
                    List.of(EntryPoint.in("engine/DefaultAgentEngine.java",
                                    "getCheckpointManager().remove"),
                            EntryPoint.in("reliability/ICheckpointManager.java",
                                    "default void remove"))),
            new EntryPoint("engine-lifecycle-close",
                    List.of(EntryPoint.in("engine/IAgentEngine.java",
                                    "extends AutoCloseable"),
                            EntryPoint.in("engine/DefaultAgentEngine.java",
                                    "public void close"))),
            new EntryPoint("resume-governance-reset",
                    List.of(EntryPoint.in("engine/AgentSessionLifecycle.java",
                            "getDenialLedger().reset", "getPostDenialGuard().reset"))),
            // ---- Plan 2519 (裁定② gate3 反向钉死改写, additive only): F4 terminal
            // ---- cleanup list expansion — sessionStore + injection window rows.
            new EntryPoint("cancel-without-handle",
                    List.of(EntryPoint.in("engine/DefaultAgentEngine.java",
                            "setStatus(AgentExecStatus.cancelled)", "getCheckpointManager().remove"))),
            new EntryPoint("session-store-terminal-cleanup",
                    List.of(EntryPoint.in("engine/DefaultAgentEngine.java",
                                    "removeTerminalSessionFromStore"),
                            EntryPoint.in("engine/AgentSessionLifecycle.java",
                                    "sessionStore.remove"),
                            EntryPoint.in("session/InMemorySessionStore.java",
                                    "public void remove"),
                            EntryPoint.in("session/DBSessionStore.java",
                                    "public void remove"),
                            EntryPoint.in("session/FileBackedSessionStore.java",
                                    "public void remove"))),
            new EntryPoint("injection-target-registration-window",
                    List.of(EntryPoint.in("engine/DefaultAgentEngine.java",
                                    "runningInjectionTargets.put", "runningInjectionTargets.remove"),
                            EntryPoint.in("engine/AgentSessionLifecycle.java",
                                    "registerInjectionTarget", "unregisterInjectionTarget")))
    );

    // ========================================================================
    // Gate assertions
    // ========================================================================

    @Test
    void tableEntryPointsHaveSymmetricAcquireReleasePairs() {
        Set<String> gapped = InvariantGateSupport.loadGappedInstances(GAP_FAMILY);
        List<String> violations = new ArrayList<>();
        for (EntryPoint row : TABLE) {
            if (gapped.contains(row.id)) {
                continue;
            }
            for (FileMarkers check : row.checks) {
                String source = InvariantGateSupport.readSource(MODULE, check.file);
                for (String marker : check.markers) {
                    if (!source.contains(marker)) {
                        violations.add(row.id + ": marker '" + marker + "' not found in " + check.file);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Gate 3 (entry-point cleanup symmetry) violations:\n" + String.join("\n", violations));
    }

    @Test
    void tableCompleteness_reproductionCommandsStillHitExpectedFiles() throws IOException {
        // §3.4 five command families — the marker must still hit the expected file.
        List<String[]> families = List.of(
                new String[]{"runningExecutions.putIfAbsent", "engine/DefaultAgentEngine.java"},
                new String[]{"runningExecutions.remove", "engine/DefaultAgentEngine.java"},
                new String[]{"tryAcquire", "engine/DefaultAgentEngine.java"},
                new String[]{"releaseLockQuietly", "engine/DefaultAgentEngine.java"},
                new String[]{"startLockRenewal", "engine/SessionLockRenewal.java"},
                new String[]{"cancelLockRenewalQuietly", "engine/SessionLockRenewal.java"},
                new String[]{"default void remove", "reliability/ICheckpointManager.java"},
                new String[]{"public void close", "engine/DefaultAgentEngine.java"},
                new String[]{"extends AutoCloseable", "engine/IAgentEngine.java"}
        );
        List<String> dead = new ArrayList<>();
        for (String[] family : families) {
            String source = InvariantGateSupport.readSource(MODULE, family[1]);
            if (!source.contains(family[0])) {
                dead.add(family[0] + " in " + family[1]);
            }
        }
        assertTrue(dead.isEmpty(),
                "§3.4 reproduction markers no longer hit their files (entry-point drift): " + dead);
    }

    // ========================================================================
    // Anti-hollow: the gate must actually intercept (no silent pass)
    // ========================================================================

    /**
     * Negative: an entry-point row whose acquire/release marker is missing
     * from its file must be reported — a refactor that drops the symmetric
     * cleanup turns the gate red.
     */
    @Test
    void negative_missingCleanupMarkerIsRejected() {
        EntryPoint fake = new EntryPoint("fake-entry-point",
                List.of(EntryPoint.in("engine/DefaultAgentEngine.java",
                        "definitely.not.a.real.marker.xyz")));
        List<String> violations = new ArrayList<>();
        for (FileMarkers check : fake.checks) {
            String source = InvariantGateSupport.readSource(MODULE, check.file);
            for (String marker : check.markers) {
                if (!source.contains(marker)) {
                    violations.add(fake.id + ": marker '" + marker + "' not found in " + check.file);
                }
            }
        }
        assertTrue(violations.stream().anyMatch(v -> v.contains("fake-entry-point")),
                "an entry-point without its acquire/release marker must be a violation; got: " + violations);
    }

    // ========================================================================
    // Plan 2519 (裁定②/C10-003): parametrised runtime terminal-cleanup pin —
    // assembly-level engine driven to a terminal status must actually empty
    // the F4 container list (sessionStore + checkpoint), not merely declare
    // the wiring. Additive pin: put-only (pre-fix) state is RED here.
    // ========================================================================

    @Test
    void runtimeTerminalCleanup_emptiesSessionStoreAndCheckpointContainers() throws Exception {
        io.nop.ai.agent.session.InMemorySessionStore store =
                new io.nop.ai.agent.session.InMemorySessionStore();
        java.nio.file.Path checkpointRoot =
                java.nio.file.Files.createTempDirectory("gate3-terminal-cleanup");
        io.nop.ai.agent.reliability.FileBackedCheckpointManager checkpoints =
                new io.nop.ai.agent.reliability.FileBackedCheckpointManager(checkpointRoot);
        io.nop.ai.agent.engine.DefaultAgentEngine engine =
                new io.nop.ai.agent.engine.DefaultAgentEngine.Builder(null, null)
                        .sessionStore(store)
                        .checkpointManager(checkpoints)
                        .build();
        try {
            String sessionId = "gate3-terminal-probe";
            // Create a live session and a checkpoint container entry, then
            // drive the engine to a terminal status via the
            // cancel-without-handle convergence point (no execution needed).
            store.getOrCreate(sessionId, "gate3-agent");
            io.nop.ai.agent.reliability.Checkpoint cp =
                    io.nop.ai.agent.reliability.Checkpoint.of(sessionId, "wm-gate3", 1,
                            System.currentTimeMillis(), io.nop.ai.agent.reliability.CheckpointType.TOOL_EXECUTION,
                            "tool", "call-1", null, null, 1, 10, null, null);
            checkpoints.saveCheckpoint(cp);

            engine.cancelSession(sessionId, "gate3 terminal cleanup probe", false)
                    .toCompletableFuture().join();

            assertTrue(store.getAll().isEmpty(),
                    "裁定② wiring: terminal execution must remove the session from the store "
                            + "(put-only pre-fix state is red here)");
            assertTrue(checkpoints.getCheckpoints(sessionId).isEmpty(),
                    "裁定② wiring: terminal execution must clear the checkpoint containers");
        } finally {
            engine.close();
        }
    }
}
