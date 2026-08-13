package io.nop.ai.agent.gate;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate ② (INV-2, plan 2026-08-12-1120-2 Phase 3): every asynchronous
 * orchestration entry point of the I0 target set table §3.2 must declare a
 * timeout.
 *
 * <p>Declaration criteria (I1 Phase 1 Decision D6 — any one suffices):
 * <ol>
 *   <li>(a) the entry method signature has a timeout parameter
 *       ({@code long}/{@code Duration}/{@code *TimeoutMs});</li>
 *   <li>(b) the entry's implementation path file references a timeout
 *       mechanism marker ({@code orTimeout} / {@code callChatWithTimeout} /
 *       {@code get(..., TimeUnit} / {@code TimeoutException});</li>
 *   <li>(c) the entry's declaring class / engine config holds non-zero
 *       timeout configuration (e.g. {@code DefaultAgentEngineConfig}
 *       {@code callAgentTimeoutMs=120000}/{@code llmTimeoutMs}/{@code toolTimeoutMs}).</li>
 * </ol>
 * Entries without an async execution wait surface (no timeout contract:
 * {@code forkSession} session registration, {@code cancelSession}
 * cancellation primitive, {@code close} lifecycle termination) are registered
 * as {@code not-applicable} in the known-gaps list (I3 adjudication input,
 * never silently passed).
 *
 * <p>Table completeness: the §3.2 derivation is re-run mechanically —
 * (1) public methods of {@code IAgentEngine}, (2) engine-package classes not
 * implementing {@code IAgentEngine} with public {@code execute} /
 * {@code executeAllowedCalls} methods, (3) {@code CallAgentExecutor.executeAsync},
 * (4) the team/flow fan-out entries ({@code MemberFanOutDispatcher.dispatch} /
 * {@code TeamTaskFlowOrchestrator.executeAsync}, I3 R-2-3 — both launch the
 * agent execution path via {@code MemberFanOutDispatcher:305
 * agentEngine.execute}) — and diffed against the gate table; a new entry not
 * in the table is red. The gateway-channel dispatch entry
 * ({@code ChannelMessageServiceImpl.dispatchInbound}, I3 R-2-1) is covered by
 * a dedicated self-contained gate test in the nop-ai-gateway module
 * (the agent-module support hard-codes the {@code io/nop/ai/agent} source
 * prefix and cannot read gateway sources).
 *
 * <p>Verdicts (post-I4, live verified): 11 declared (engine config
 * {@code callAgentTimeoutMs} / {@code llmTimeoutMs} constructor params /
 * {@code orTimeout} / {@code TimeoutException} mechanism markers); 4
 * not-applicable (forkSession / cancelSession / close / getSessionStatus —
 * no async execution wait surface); 1 gateway entry covered in the gateway
 * module's own gate test.
 */
public class TestInvariantGate2OrchestrationTimeout {

    private static final String GAP_FAMILY = "gate-2-orchestration-timeout";
    private static final String MODULE = "nop-ai-agent";

    /** I0 target set table §3.2 entry (project source level = 11, no records). */
    static final class Entry {
        final String id;
        final String evidenceFile;
        final String marker;
        final String verdict;
        /** Minimum CODE-level (comments/strings stripped) marker occurrences
         * required for a declared entry; default 1 (plan
         * 2026-08-12-2050-2 / WS4 branch-level upgrade). */
        final int minOccurrences;

        Entry(String id, String evidenceFile, String marker, String verdict) {
            this(id, evidenceFile, marker, verdict, 1);
        }

        Entry(String id, String evidenceFile, String marker, String verdict, int minOccurrences) {
            this.id = id;
            this.evidenceFile = evidenceFile;
            this.marker = marker;
            this.verdict = verdict;
            this.minOccurrences = minOccurrences;
        }
    }

    /**
     * I0 target set table §3.2 (16 entries: 14 original + 2 team/flow
     * entries added by I3 R-2-3). verdict: {@code declared} =
     * evidence file must contain the marker; {@code not-applicable} /
     * {@code missing} = must be registered in gate-gaps.yaml family
     * gate-2-orchestration-timeout. The gateway-channel dispatch entry
     * (R-2-1) is covered by {@code TestInvariantGate2GatewayTimeout} in the
     * nop-ai-gateway module (self-contained, no agent source-path support).
     *
     * <p>Branch-level judgment (plan 2026-08-12-2050-2 / WS4):
     * {@code MemberFanOutDispatcher.dispatch} is a fan-out with TWO
     * execution branches (BOUND via {@code executeBoundMember} + SPAWN via
     * {@code spawnOneTarget}), each of which must declare its own timeout
     * marker — hence {@code minOccurrences=2} for CODE-level (comment and
     * string occurrences stripped) occurrences. {@code TeamTaskFlowOrchestrator
     * .executeAsync} keeps {@code minOccurrences=1} (single overall-deadline
     * marker).
     */
    static final List<Entry> TABLE = List.of(
            new Entry("IAgentEngine.sendMessage", "engine/DefaultAgentEngineConfig.java",
                    "callAgentTimeoutMs", "declared"),
            new Entry("IAgentEngine.execute", "engine/DefaultAgentEngineConfig.java",
                    "callAgentTimeoutMs", "declared"),
            new Entry("IAgentEngine.forkSession", null, null, "not-applicable"),
            new Entry("IAgentEngine.cancelSession", null, null, "not-applicable"),
            new Entry("IAgentEngine.resumeSession", "engine/DefaultAgentEngineConfig.java",
                    "callAgentTimeoutMs", "declared"),
            new Entry("IAgentEngine.restoreSession", "engine/DefaultAgentEngineConfig.java",
                    "callAgentTimeoutMs", "declared"),
            new Entry("IAgentEngine.wakeSession", "engine/DefaultAgentEngineConfig.java",
                    "callAgentTimeoutMs", "declared"),
            new Entry("IAgentEngine.restorePendingSessions", "engine/DefaultAgentEngineConfig.java",
                    "callAgentTimeoutMs", "declared"),
            new Entry("IAgentEngine.getSessionStatus", null, null, "not-applicable"),
            new Entry("IAgentEngine.close", null, null, "not-applicable"),
            new Entry("ReActAgentExecutor.execute", "engine/ReActAgentExecutor.java",
                    "llmTimeoutMs", "declared"),
            new Entry("SingleTurnExecutor.execute", "engine/SingleTurnExecutor.java",
                    "TimeoutException", "declared"),
            new Entry("AgentToolDispatcher.executeAllowedCalls", "engine/AgentToolDispatcher.java",
                    "orTimeout", "declared"),
            new Entry("CallAgentExecutor.executeAsync", "tool/CallAgentExecutor.java",
                    "orTimeout", "declared"),
            new Entry("MemberFanOutDispatcher.dispatch", "team/flow/MemberFanOutDispatcher.java",
                    "orTimeout", "declared", 2),
            new Entry("TeamTaskFlowOrchestrator.executeAsync", "team/flow/TeamTaskFlowOrchestrator.java",
                    "orTimeout", "declared")
    );

    // ========================================================================
    // Gate assertions
    // ========================================================================

    @Test
    void tableEntriesDeclareTimeoutOrAreRegistered() {
        Set<String> gapped = InvariantGateSupport.loadGappedInstances(GAP_FAMILY);
        List<String> violations = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (Entry e : TABLE) {
            boolean registered = gapped.contains(e.id);
            if ("declared".equals(e.verdict)) {
                String source = InvariantGateSupport.readSource(MODULE, e.evidenceFile);
                // Branch-level judgment (plan 2026-08-12-2050-2 / WS4):
                // CODE-level occurrence count (comments and string literals
                // stripped) must reach the entry's branch minimum — a
                // contains() check or raw occurrence count cannot distinguish
                // "one marker in a comment" from "one marker per execution
                // branch".
                int occurrences = InvariantGateSupport.countCodeOccurrences(source, e.marker);
                if (occurrences < e.minOccurrences) {
                    violations.add(e.id + ": timeout declaration marker '" + e.marker
                            + "' found " + occurrences + " code occurrence(s) in " + e.evidenceFile
                            + " but " + e.minOccurrences + " required (branch-level judgment)");
                } else if (registered) {
                    stale.add(e.id + ": declared but still registered in gate-gaps.yaml (stale entry,"
                            + " remove after I4-style verification)");
                }
            } else {
                if (!registered) {
                    violations.add(e.id + ": verdict '" + e.verdict
                            + "' but not registered in gate-gaps.yaml family '" + GAP_FAMILY + "'");
                }
            }
        }
        if (!stale.isEmpty()) {
            System.out.println("[gate-2] stale gap entries (declared now): " + stale);
        }
        assertTrue(violations.isEmpty(),
                "Gate 2 (orchestration timeout declaration) violations:\n" + String.join("\n", violations));
    }

    @Test
    void tableCompleteness_mechanicalDerivationMatchesTable() {
        try {
            Set<String> derived = deriveEntryIds();
            Set<String> tableSet = new TreeSet<>(TABLE.stream().map(e -> e.id).collect(Collectors.toSet()));
            Set<String> unregistered = new TreeSet<>(derived);
            unregistered.removeAll(tableSet);
            assertTrue(unregistered.isEmpty(),
                    "newly derived orchestration entries NOT in the gate table — must be added to "
                            + "invariant-catalog §3.2 before the gate can cover them (Loop Rule): " + unregistered);
            Set<String> missing = new TreeSet<>(tableSet);
            missing.removeAll(derived);
            assertTrue(missing.isEmpty(), "gate table entries missing from the mechanical derivation: " + missing);
        } catch (IOException e) {
            throw new IllegalStateException("derivation scan failed", e);
        }
    }

    // ========================================================================
    // Anti-hollow: the gate must actually intercept (no silent pass)
    // ========================================================================

    /**
     * Negative: an entry whose declared marker has vanished from its evidence
     * file must be reported — a refactor that removes the timeout declaration
     * (e.g. a new executor entry wired without any timeout marker) turns the
     * gate red.
     */
    @Test
    void negative_missingTimeoutDeclarationMarkerIsRejected() {
        Entry fake = new Entry("FakeExecutor.execute", "engine/ReActAgentExecutor.java",
                "orTimeout", "declared");
        List<String> violations = new ArrayList<>();
        for (Entry e : List.of(fake)) {
            String source = InvariantGateSupport.readSource(MODULE, e.evidenceFile);
            if (InvariantGateSupport.countCodeOccurrences(source, e.marker) < e.minOccurrences) {
                violations.add(e.id + ": timeout declaration marker '" + e.marker
                        + "' not found in " + e.evidenceFile);
            }
        }
        assertTrue(violations.stream().anyMatch(v -> v.contains("FakeExecutor.execute")),
                "a declared entry without its marker must be a violation; got: " + violations);
    }

    /**
     * Negative (branch-level, plan 2026-08-12-2050-2 / WS4): an entry whose
     * evidence file declares the marker only once, but which requires one
     * marker per execution branch ({@code minOccurrences=2}), must be
     * reported — this is exactly the "gate green but a sibling branch is
     * naked" blind spot the upgrade closes. The fake targets
     * {@code TeamTaskFlowOrchestrator.java}, whose CODE-level orTimeout
     * count is 1 (the overall deadline marker; javadoc/comment mentions are
     * stripped) — a {@code contains()} or raw occurrence count would
     * wrongly pass it.
     */
    @Test
    void negative_insufficientBranchLevelMarkerCountIsRejected() {
        Entry fake = new Entry("FakeFanOut.dispatch", "team/flow/TeamTaskFlowOrchestrator.java",
                "orTimeout", "declared", 2);
        int occurrences = InvariantGateSupport.countCodeOccurrences(
                InvariantGateSupport.readSource(MODULE, fake.evidenceFile), fake.marker);
        assertTrue(occurrences < fake.minOccurrences,
                "precondition: the fixture file must have fewer code occurrences than required, got "
                        + occurrences);
        List<String> violations = new ArrayList<>();
        if (occurrences < fake.minOccurrences) {
            violations.add(fake.id + ": timeout declaration marker '" + fake.marker
                    + "' found " + occurrences + " code occurrence(s) but " + fake.minOccurrences
                    + " required (branch-level judgment)");
        }
        assertTrue(violations.stream().anyMatch(v -> v.contains("FakeFanOut.dispatch")),
                "a declared entry with insufficient branch-level marker count must be a violation; got: "
                        + violations);
    }

    // ========================================================================
    // §3.2 mechanical derivation (reproduction of the I0 criterion)
    // ========================================================================

    private static final Pattern INTERFACE_METHOD =
            Pattern.compile("^\\s*(default\\s+)?[\\w.<>,\\[\\]\\s]+\\s+(\\w+)\\s*\\(");

    private static final Pattern ENGINE_METHOD =
            Pattern.compile("^\\s*public\\s+[\\w.<>,\\[\\]\\s]+\\s+(execute|executeAllowedCalls)\\s*\\(");

    /**
     * Re-runs the I0 §3.2 derivation: (1) IAgentEngine public methods,
     * (2) engine-package classes not implementing IAgentEngine with public
     * {@code execute} / {@code executeAllowedCalls} methods, (3) the
     * {@code CallAgentExecutor.executeAsync} special case, (4) the team/flow
     * fan-out entries (I3 R-2-3: {@code MemberFanOutDispatcher.dispatch} /
     * {@code TeamTaskFlowOrchestrator.executeAsync} — both start the agent
     * execution path and must declare a timeout).
     */
    static Set<String> deriveEntryIds() throws IOException {
        Set<String> derived = new TreeSet<>();

        Path iface = InvariantGateSupport.sourceFile(MODULE, "engine/IAgentEngine.java");
        for (String line : Files.readAllLines(iface)) {
            // skip default-method bodies (throw statements) and the interface declaration;
            // note: "throws Exception" (close signature) must NOT be skipped
            if (line.contains("throw new") || line.contains("static") || line.contains("interface")) {
                continue;
            }
            Matcher m = INTERFACE_METHOD.matcher(line);
            if (m.find()) {
                derived.add("IAgentEngine." + m.group(2));
            }
        }

        Path engineDir = InvariantGateSupport.moduleRoot(MODULE).resolve("src/main/java/io/nop/ai/agent/engine");
        try (Stream<Path> walk = Files.walk(engineDir)) {
            List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            for (Path file : files) {
                String content = Files.readString(file);
                if (content.contains("implements IAgentEngine")) {
                    continue;
                }
                String simpleName = file.getFileName().toString().replace(".java", "");
                for (String line : Files.readAllLines(file)) {
                    Matcher m = ENGINE_METHOD.matcher(line);
                    if (m.find()) {
                        derived.add(simpleName + "." + m.group(1));
                    }
                }
            }
        }

        String callAgent = InvariantGateSupport.readSource(MODULE, "tool/CallAgentExecutor.java");
        if (callAgent.contains("public CompletionStage<AiToolCallResult> executeAsync(")) {
            derived.add("CallAgentExecutor.executeAsync");
        }

        // I3 R-2-3: team/flow fan-out entries — both launch the agent
        // execution path (MemberFanOutDispatcher:305 agentEngine.execute).
        String fanOut = InvariantGateSupport.readSource(MODULE, "team/flow/MemberFanOutDispatcher.java");
        if (fanOut.contains("public static CompletableFuture<MemberDispatchOutcome> dispatch(")) {
            derived.add("MemberFanOutDispatcher.dispatch");
        }
        String orchestrator = InvariantGateSupport.readSource(MODULE, "team/flow/TeamTaskFlowOrchestrator.java");
        if (orchestrator.contains("public CompletableFuture<TeamTaskFlowResult> executeAsync(")) {
            derived.add("TeamTaskFlowOrchestrator.executeAsync");
        }
        return derived;
    }
}
