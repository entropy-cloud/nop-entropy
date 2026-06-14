package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Phase 1 tests for {@link NoOpSecurityLevelResolver} and the
 * {@link ISecurityLevelResolver} contract.
 *
 * <p>Covers three concerns:
 * <ol>
 *   <li>NoOp default: all action kinds × all hint combinations resolve to
 *       {@link SecurityLevel#STANDARD}.</li>
 *   <li>Anti-Hollow check: a custom rule-table resolver implementing the design
 *       §5.1 deterministic upgrade table proves the contract can produce real
 *       STANDARD/ELEVATED/RESTRICTED decisions (not a hollow shell that always
 *       returns STANDARD).</li>
 *   <li>Singleton identity: {@code noOp()} returns the same instance.</li>
 * </ol>
 */
public class TestNoOpSecurityLevelResolver {

    // ========================================================================
    // NoOp default: all action kinds × all hint combinations → STANDARD
    // ========================================================================

    @Test
    void noOpReturnsStandardForAllKnownAndUnknownActionKinds() {
        ISecurityLevelResolver resolver = NoOpSecurityLevelResolver.noOp();
        LevelHints hints = LevelHints.defaults();
        String[] actionKinds = {
                "fs.read", "fs.list", "fs.grep",
                "fs.write", "fs.edit", "patch.apply",
                "shell.exec", "code.exec",
                "network.fetch", "web.fetch",
                "unknown.action", "", null
        };
        for (String actionKind : actionKinds) {
            assertEquals(SecurityLevel.STANDARD, resolver.resolve(actionKind, hints),
                    "NoOp must return STANDARD for actionKind=" + actionKind);
        }
    }

    @Test
    void noOpReturnsStandardForAllHintCombinations() {
        ISecurityLevelResolver resolver = NoOpSecurityLevelResolver.noOp();
        String[] actionKinds = {"fs.read", "fs.write", "shell.exec", "network.fetch"};
        // Iterate over all 2^5 = 32 boolean combinations of the 5 hints
        for (String actionKind : actionKinds) {
            for (int mask = 0; mask < 32; mask++) {
                LevelHints hints = new LevelHints(
                        (mask & 1) != 0,
                        (mask & 2) != 0,
                        (mask & 4) != 0,
                        (mask & 8) != 0,
                        (mask & 16) != 0);
                assertEquals(SecurityLevel.STANDARD, resolver.resolve(actionKind, hints),
                        "NoOp must return STANDARD for actionKind=" + actionKind + ", hints=" + hints);
            }
        }
    }

    @Test
    void noOpTreatsNullHintsAsDefaults() {
        ISecurityLevelResolver resolver = NoOpSecurityLevelResolver.noOp();
        // Null hints should not cause an error; NoOp returns STANDARD regardless
        assertEquals(SecurityLevel.STANDARD, resolver.resolve("shell.exec", null));
        assertEquals(SecurityLevel.STANDARD, resolver.resolve(null, null));
    }

    @Test
    void noOpReturnsSingletonInstance() {
        ISecurityLevelResolver a = NoOpSecurityLevelResolver.noOp();
        ISecurityLevelResolver b = NoOpSecurityLevelResolver.noOp();
        assertSame(a, b, "noOp() must return the same singleton instance");
    }

    // ========================================================================
    // Anti-Hollow: custom rule-table resolver proves the contract is functional
    // ========================================================================

    /**
     * A rule-table resolver implementing the design §5.1 deterministic upgrade
     * table. This is a TEST-ONLY implementation (not shipped product code) that
     * proves the {@link ISecurityLevelResolver} contract can produce real
     * STANDARD/ELEVATED/RESTRICTED decisions based on action kind and hints.
     *
     * <table>
     *   <tr><th>action_kind</th><th>Default</th><th>Upgrade condition</th></tr>
     *   <tr><td>fs.read, fs.list, fs.grep</td><td>STANDARD</td><td>—</td></tr>
     *   <tr><td>fs.write, fs.edit, patch.apply</td><td>STANDARD</td><td>writesOutsideWorkspace → ELEVATED</td></tr>
     *   <tr><td>shell.exec, code.exec</td><td>STANDARD</td><td>!trustedSource → ELEVATED; highImpact → RESTRICTED</td></tr>
     *   <tr><td>network.fetch, web.fetch</td><td>STANDARD</td><td>!trustedSource → RESTRICTED</td></tr>
     *   <tr><td>other</td><td>STANDARD</td><td>!trustedSource → ELEVATED; highImpact → RESTRICTED</td></tr>
     * </table>
     */
    static final class DesignSpecRuleTableResolver implements ISecurityLevelResolver {
        @Override
        public SecurityLevel resolve(String actionKind, LevelHints hints) {
            LevelHints h = hints != null ? hints : LevelHints.defaults();

            // fs.read / fs.list / fs.grep → STANDARD (never upgraded)
            if (eq(actionKind, "fs.read") || eq(actionKind, "fs.list") || eq(actionKind, "fs.grep")) {
                return SecurityLevel.STANDARD;
            }

            // fs.write / fs.edit / patch.apply → STANDARD (writesOutsideWorkspace → ELEVATED)
            if (eq(actionKind, "fs.write") || eq(actionKind, "fs.edit") || eq(actionKind, "patch.apply")) {
                return h.isWritesOutsideWorkspace() ? SecurityLevel.ELEVATED : SecurityLevel.STANDARD;
            }

            // shell.exec / code.exec → STANDARD (!trustedSource → ELEVATED; highImpact → RESTRICTED)
            if (eq(actionKind, "shell.exec") || eq(actionKind, "code.exec")) {
                if (h.isHighImpact()) return SecurityLevel.RESTRICTED;
                if (!h.isTrustedSource()) return SecurityLevel.ELEVATED;
                return SecurityLevel.STANDARD;
            }

            // network.fetch / web.fetch → STANDARD (!trustedSource → RESTRICTED)
            if (eq(actionKind, "network.fetch") || eq(actionKind, "web.fetch")) {
                return h.isTrustedSource() ? SecurityLevel.STANDARD : SecurityLevel.RESTRICTED;
            }

            // other → STANDARD (!trustedSource → ELEVATED; highImpact → RESTRICTED)
            if (h.isHighImpact()) return SecurityLevel.RESTRICTED;
            if (!h.isTrustedSource()) return SecurityLevel.ELEVATED;
            return SecurityLevel.STANDARD;
        }

        private static boolean eq(String a, String b) {
            return b.equals(a);
        }
    }

    @Test
    void ruleTableFsReadIsAlwaysStandard() {
        DesignSpecRuleTableResolver resolver = new DesignSpecRuleTableResolver();
        LevelHints allRisk = new LevelHints(false, true, true, true, true);
        for (String kind : new String[]{"fs.read", "fs.list", "fs.grep"}) {
            assertEquals(SecurityLevel.STANDARD, resolver.resolve(kind, allRisk),
                    kind + " must remain STANDARD regardless of hints");
        }
    }

    @Test
    void ruleTableFsWriteUpgradesToElevatedWhenWritesOutsideWorkspace() {
        DesignSpecRuleTableResolver resolver = new DesignSpecRuleTableResolver();
        LevelHints safe = LevelHints.defaults();
        LevelHints outside = new LevelHints(true, true, false, false, false);

        assertEquals(SecurityLevel.STANDARD, resolver.resolve("fs.write", safe),
                "fs.write inside workspace → STANDARD");
        assertEquals(SecurityLevel.ELEVATED, resolver.resolve("fs.write", outside),
                "fs.write outside workspace → ELEVATED");
        assertEquals(SecurityLevel.STANDARD, resolver.resolve("fs.edit", safe));
        assertEquals(SecurityLevel.ELEVATED, resolver.resolve("patch.apply", outside));
    }

    @Test
    void ruleTableShellExecUpgradesByUntrustedSourceAndHighImpact() {
        DesignSpecRuleTableResolver resolver = new DesignSpecRuleTableResolver();
        // trusted = trustedSource=true, no other risk signals
        LevelHints trusted = new LevelHints(true, false, false, false, false);
        // untrusted = trustedSource=false (defaults() also has trustedSource=false)
        LevelHints untrusted = LevelHints.defaults();
        LevelHints highImpactTrusted = new LevelHints(true, false, false, false, true);
        LevelHints highImpactUntrusted = new LevelHints(false, false, false, false, true);

        assertEquals(SecurityLevel.STANDARD, resolver.resolve("shell.exec", trusted),
                "shell.exec trusted + low impact → STANDARD");
        assertEquals(SecurityLevel.ELEVATED, resolver.resolve("shell.exec", untrusted),
                "shell.exec untrusted source → ELEVATED");
        assertEquals(SecurityLevel.RESTRICTED, resolver.resolve("shell.exec", highImpactTrusted),
                "shell.exec high impact → RESTRICTED (overrides trusted source)");
        assertEquals(SecurityLevel.RESTRICTED, resolver.resolve("shell.exec", highImpactUntrusted),
                "shell.exec high impact + untrusted → RESTRICTED (highImpact dominates)");
        assertEquals(SecurityLevel.RESTRICTED, resolver.resolve("code.exec", highImpactTrusted),
                "code.exec high impact → RESTRICTED");
    }

    @Test
    void ruleTableNetworkFetchUpgradesToRestrictedForUntrustedSource() {
        DesignSpecRuleTableResolver resolver = new DesignSpecRuleTableResolver();
        // trusted = trustedSource=true, no other risk signals
        LevelHints trusted = new LevelHints(true, false, false, false, false);
        // untrusted = trustedSource=false
        LevelHints untrusted = LevelHints.defaults();

        assertEquals(SecurityLevel.STANDARD, resolver.resolve("network.fetch", trusted),
                "network.fetch trusted source → STANDARD");
        assertEquals(SecurityLevel.RESTRICTED, resolver.resolve("network.fetch", untrusted),
                "network.fetch untrusted source → RESTRICTED");
        assertEquals(SecurityLevel.STANDARD, resolver.resolve("web.fetch", trusted));
        assertEquals(SecurityLevel.RESTRICTED, resolver.resolve("web.fetch", untrusted));
    }

    @Test
    void ruleTableUnknownActionUpgradesByUntrustedSourceAndHighImpact() {
        DesignSpecRuleTableResolver resolver = new DesignSpecRuleTableResolver();
        // trusted = trustedSource=true, no other risk signals
        LevelHints trusted = new LevelHints(true, false, false, false, false);
        // untrusted = trustedSource=false
        LevelHints untrusted = LevelHints.defaults();
        LevelHints highImpactTrusted = new LevelHints(true, false, false, false, true);

        assertEquals(SecurityLevel.STANDARD, resolver.resolve("unknown.tool", trusted),
                "unknown action + trusted + low impact → STANDARD");
        assertEquals(SecurityLevel.ELEVATED, resolver.resolve("unknown.tool", untrusted),
                "unknown action + untrusted source → ELEVATED");
        assertEquals(SecurityLevel.RESTRICTED, resolver.resolve("unknown.tool", highImpactTrusted),
                "unknown action + high impact → RESTRICTED");
        // null action kind falls into "other"
        assertEquals(SecurityLevel.STANDARD, resolver.resolve(null, trusted));
        assertEquals(SecurityLevel.RESTRICTED, resolver.resolve(null, highImpactTrusted));
    }

    @Test
    void ruleTableTreatsNullHintsAsDefaults() {
        DesignSpecRuleTableResolver resolver = new DesignSpecRuleTableResolver();
        // Null hints = defaults (all false, incl. trustedSource=false).
        // fs.read is always STANDARD; fs.write is STANDARD when no outside-write;
        // but shell.exec/network.fetch/unknown UPGRADE because !trustedSource holds.
        assertEquals(SecurityLevel.STANDARD, resolver.resolve("fs.read", null),
                "fs.read with null hints → STANDARD (never upgraded)");
        assertEquals(SecurityLevel.STANDARD, resolver.resolve("fs.write", null),
                "fs.write with null hints (= defaults, no outside-write) → STANDARD");
        assertEquals(SecurityLevel.ELEVATED, resolver.resolve("shell.exec", null),
                "shell.exec with null hints (= defaults, untrusted) → ELEVATED");
        assertEquals(SecurityLevel.RESTRICTED, resolver.resolve("network.fetch", null),
                "network.fetch with null hints (= defaults, untrusted) → RESTRICTED");
        assertEquals(SecurityLevel.ELEVATED, resolver.resolve("unknown", null),
                "unknown action with null hints (= defaults, untrusted) → ELEVATED");
    }

    @Test
    void ruleTableCanDistinguishAllThreeLevels() {
        // This is the core Anti-Hollow proof: a single resolver instance
        // produces STANDARD, ELEVATED, and RESTRICTED depending on inputs.
        DesignSpecRuleTableResolver resolver = new DesignSpecRuleTableResolver();
        SecurityLevel standard = resolver.resolve("fs.read", LevelHints.defaults());
        SecurityLevel elevated = resolver.resolve("fs.write",
                new LevelHints(true, true, false, false, false));
        SecurityLevel restricted = resolver.resolve("shell.exec",
                new LevelHints(true, false, false, false, true));

        assertEquals(SecurityLevel.STANDARD, standard);
        assertEquals(SecurityLevel.ELEVATED, elevated);
        assertEquals(SecurityLevel.RESTRICTED, restricted);
    }
}
