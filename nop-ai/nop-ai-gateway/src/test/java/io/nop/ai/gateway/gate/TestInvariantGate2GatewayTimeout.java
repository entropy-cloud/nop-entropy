package io.nop.ai.gateway.gate;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate ② (INV-2) — nop-ai-gateway subset: the channel dispatch entry
 * {@code ChannelMessageServiceImpl.dispatchInbound} added to the §3.2
 * orchestration table by I3 R-2-1.
 *
 * <p>This test is <b>self-contained</b> (does not reuse the nop-ai-agent
 * {@code InvariantGateSupport}): that support hard-codes the
 * {@code src/main/java/io/nop/ai/agent} source prefix
 * (InvariantGateSupport.java:35-37) and cannot read gateway sources, so the
 * agent-module TABLE branch is not feasible for this entry (I3 R-2-1
 * adjudication, cross-module test-placement ruling).
 *
 * <p>Same contract as {@code TestInvariantGate2OrchestrationTimeout} in
 * nop-ai-agent: verdict {@code declared} = the evidence file must contain the
 * marker; a {@code missing}/{@code not-applicable} verdict must be registered
 * in gate-gaps.yaml family {@code gate-2-orchestration-timeout}.
 *
 * <p>Branch-level judgment (plan 2026-08-12-2050-2 / WS4): the entry is
 * split by mode — mode-1 ({@code fanOutToListeners}' per-listener
 * {@code runAsync(...).orTimeout(...)}) and mode-2 ({@code dispatchInbound}'s
 * {@code sendAsync(...).orTimeout(...)}) — each waiting surface requires its
 * own marker ({@code minOccurrences=1} each), because the combined count was
 * green before the AR-4 fix (both markers already existed) and is therefore
 * zero-discriminative for the fix. The AR-4 cancel semantics (raw future
 * cancel, not just the marker) are NOT detectable by any marker count — they
 * are enforced by the WS3 behavior test {@code TestChannelFanOutTimeoutCancel}.
 * Table completeness is a mechanical derivation over the gateway channel
 * package; the negative tests prove the gate intercepts.
 */
public class TestInvariantGate2GatewayTimeout {

    private static final String GAP_FAMILY = "gate-2-orchestration-timeout";
    private static final String MODULE = "nop-ai-gateway";

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
     * I3 R-2-1 + plan 2026-08-12-2050-2 / WS4: the gateway-channel dispatch
     * entry is split per waiting surface — mode-1 per-listener fan-out and
     * mode-2 {@code sendAsync} publish — each requiring its own timeout
     * marker (branch-level judgment; the unsplit combined count has zero
     * discriminative power for the AR-4 fix because both markers already
     * existed pre-fix).
     */
    static final List<Entry> TABLE = List.of(
            new Entry("ChannelMessageServiceImpl.dispatchInbound[mode-1]",
                    "channel/ChannelMessageServiceImpl.java", "orTimeout", "declared"),
            new Entry("ChannelMessageServiceImpl.dispatchInbound[mode-2]",
                    "channel/ChannelMessageServiceImpl.java", "orTimeout", "declared")
    );

    // ========================================================================
    // Path helpers (self-contained; no InvariantGateSupport dependency)
    // ========================================================================

    static Path repoRoot() {
        String basedir = System.getProperty("basedir");
        Path base = basedir != null ? Path.of(basedir) : Path.of("").toAbsolutePath();
        return base.normalize().resolve("../..").normalize();
    }

    static Path moduleRoot() {
        return repoRoot().resolve("nop-ai").resolve(MODULE);
    }

    static String readSource(String relativePath) {
        Path file = moduleRoot().resolve("src/main/java/io/nop/ai/gateway").resolve(relativePath);
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read source file: " + file, e);
        }
    }

    static Set<String> loadGappedInstances(String family) {
        Path gapFile = repoRoot().resolve("ai-dev/audits/nop-ai-invariants/gate-gaps.yaml");
        if (!Files.exists(gapFile)) {
            throw new IllegalStateException("known-gaps file missing: " + gapFile
                    + " (gate cannot be evaluated without the registry)");
        }
        try {
            Map<String, Object> root = new Yaml().load(Files.readString(gapFile));
            Object fam = root.get(family);
            if (!(fam instanceof List)) {
                return Set.of();
            }
            Set<String> instances = new HashSet<>();
            for (Object raw : (List<?>) fam) {
                if (raw instanceof Map) {
                    Object instance = ((Map<?, ?>) raw).get("instance");
                    if (instance != null) {
                        instances.add(String.valueOf(instance));
                    }
                }
            }
            return instances;
        } catch (IOException e) {
            throw new IllegalStateException("failed to read known-gaps file: " + gapFile, e);
        }
    }

    // ========================================================================
    // Gate assertions
    // ========================================================================

    @Test
    void tableEntriesDeclareTimeoutOrAreRegistered() {
        Set<String> gapped = loadGappedInstances(GAP_FAMILY);
        List<String> violations = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (Entry e : TABLE) {
            boolean registered = gapped.contains(e.id);
            if ("declared".equals(e.verdict)) {
                String source = readSource(e.evidenceFile);
                // Branch-level judgment (plan 2026-08-12-2050-2 / WS4):
                // CODE-level occurrence count (comments and string literals
                // stripped) must reach the entry's minimum.
                int occurrences = countCodeOccurrences(source, e.marker);
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
            System.out.println("[gate-2-gateway] stale gap entries (declared now): " + stale);
        }
        assertTrue(violations.isEmpty(),
                "Gate 2 (gateway dispatch timeout declaration) violations:\n"
                        + String.join("\n", violations));
    }

    /**
     * Table completeness: the mechanical derivation over the gateway channel
     * package must match the gate table — a new channel dispatch entry
     * without a timeout declaration is a hard failure (Loop Rule trigger).
     */
    @Test
    void tableCompleteness_mechanicalDerivationMatchesTable() {
        Set<String> derived = deriveEntryIds();
        Set<String> tableSet = new TreeSet<>();
        for (Entry e : TABLE) {
            tableSet.add(e.id);
        }
        Set<String> unregistered = new TreeSet<>(derived);
        unregistered.removeAll(tableSet);
        assertTrue(unregistered.isEmpty(),
                "derived gateway dispatch entries NOT in the gate table — must be added to "
                        + "invariant-catalog §3.2 before the gate can cover them (Loop Rule): " + unregistered);
        Set<String> missing = new TreeSet<>(tableSet);
        missing.removeAll(derived);
        assertTrue(missing.isEmpty(),
                "gate table entries missing from the mechanical derivation: " + missing);
    }

    /**
     * Anti-hollow: an entry whose declared marker has vanished from its
     * evidence file must be reported — a refactor that removes the timeout
     * declaration turns the gate red.
     */
    @Test
    void negative_missingTimeoutDeclarationMarkerIsRejected() {
        Entry fake = new Entry("ChannelMessageServiceImpl.dispatchInbound",
                "channel/ChannelMessageServiceImpl.java", "orTimeout.definitely.not.present", "declared");
        List<String> violations = new ArrayList<>();
        String source = readSource(fake.evidenceFile);
        if (countCodeOccurrences(source, fake.marker) < fake.minOccurrences) {
            violations.add(fake.id + ": timeout declaration marker '" + fake.marker
                    + "' not found in " + fake.evidenceFile);
        }
        assertTrue(violations.stream().anyMatch(v -> v.contains("ChannelMessageServiceImpl.dispatchInbound")),
                "a declared entry without its marker must be a violation; got: " + violations);
    }

    /**
     * Negative (branch-level, plan 2026-08-12-2050-2 / WS4): a marker count
     * below a multi-surface requirement must be reported — mirror of the
     * agent-module {@code minOccurrences} mechanism. The fake requires 3
     * code-level markers; the evidence file has exactly 2 (mode-1 + mode-2),
     * so the branch-level judgment catches a naked waiting surface that a
     * {@code contains()} check would pass.
     */
    @Test
    void negative_insufficientBranchLevelMarkerCountIsRejected() {
        Entry fake = new Entry("ChannelMessageServiceImpl.dispatchInbound[all-modes]",
                "channel/ChannelMessageServiceImpl.java", "orTimeout", "declared", 3);
        String source = readSource(fake.evidenceFile);
        int occurrences = countCodeOccurrences(source, fake.marker);
        List<String> violations = new ArrayList<>();
        if (occurrences < fake.minOccurrences) {
            violations.add(fake.id + ": timeout declaration marker '" + fake.marker
                    + "' found " + occurrences + " code occurrence(s) but " + fake.minOccurrences
                    + " required (branch-level judgment)");
        }
        assertTrue(violations.stream().anyMatch(v -> v.contains("all-modes")),
                "an entry with insufficient branch-level marker count must be a violation; got: "
                        + violations);
    }

    /**
     * Mechanical derivation (I3 R-2-1 + plan 2026-08-12-2050-2 / WS4): the
     * gateway channel dispatch waiting surfaces exist iff the evidence file
     * contains each mode's timeout marker in CODE (comments/strings
     * stripped): mode-1 (per-listener fan-out) requires the marker in
     * {@code fanOutToListeners}, mode-2 ({@code sendAsync} publish) requires
     * it in {@code dispatchInbound}. A mode whose marker disappeared from
     * its method is no longer derived and the gate goes red (Loop Rule).
     */
    static Set<String> deriveEntryIds() {
        Set<String> derived = new TreeSet<>();
        String source = readSource("channel/ChannelMessageServiceImpl.java");
        if (source.contains("public void dispatchInbound(InboundChannelMessage message)")) {
            // Mode-2 waiting surface: sendAsync(...).orTimeout in the
            // dispatchInbound method body (before the fan-out helper).
            String mode2Body = source.substring(
                    source.indexOf("public void dispatchInbound(InboundChannelMessage message)"),
                    source.indexOf("private void fanOutToListeners"));
            if (countCodeOccurrences(mode2Body, "orTimeout") >= 1) {
                derived.add("ChannelMessageServiceImpl.dispatchInbound[mode-2]");
            }
            // Mode-1 waiting surface: the shared fan-out loop's per-listener
            // orTimeout lives in fanOutToListeners.
            String fanOutBody = source.substring(source.indexOf("private void fanOutToListeners"));
            if (countCodeOccurrences(fanOutBody, "orTimeout") >= 1) {
                derived.add("ChannelMessageServiceImpl.dispatchInbound[mode-1]");
            }
        }
        return derived;
    }

    /**
     * Count occurrences of {@code marker} in the <b>code</b> of the given
     * source: {@code //} line comments, {@code /* *}{@code /} block
     * comments, string literals and char literals are stripped before
     * counting (semantics must stay in sync with the agent-module
     * {@code InvariantGateSupport.countCodeOccurrences} — the agent version
     * is authoritative). Comments / log text cannot satisfy a declaration
     * marker. Limitation (recorded in the plan): counting cannot detect the
     * AR-4 raw-future-cancel regression (the marker stays while the cancel
     * disappears) — that contract is enforced by the WS3 behavior test.
     */
    static int countCodeOccurrences(String source, String marker) {
        StringBuilder code = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        boolean inBlockComment = false;
        while (i < n) {
            char c = source.charAt(i);
            char next = i + 1 < n ? source.charAt(i + 1) : '\0';
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i += 2;
                } else {
                    i++;
                }
                continue;
            }
            if (c == '/' && next == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                i += 2;
                continue;
            }
            if (c == '"' || c == '\'') {
                char quote = c;
                i++;
                while (i < n && source.charAt(i) != quote) {
                    if (source.charAt(i) == '\\') {
                        i++;
                    }
                    i++;
                }
                i++;
                continue;
            }
            code.append(c);
            i++;
        }
        int count = 0;
        int idx = 0;
        while ((idx = code.indexOf(marker, idx)) >= 0) {
            count++;
            idx += marker.length();
        }
        return count;
    }
}
