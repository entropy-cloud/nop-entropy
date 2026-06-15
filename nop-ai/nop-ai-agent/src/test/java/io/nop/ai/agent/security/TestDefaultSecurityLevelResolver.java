package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plan 200 focused tests for {@link DefaultSecurityLevelResolver}:
 * verifies the trusted-by-default variant of the design §5.1 rule table.
 */
public class TestDefaultSecurityLevelResolver {

    private final DefaultSecurityLevelResolver resolver = new DefaultSecurityLevelResolver();

    // trusted source → STANDARD baseline (no risk signals)
    @Test
    void trustedNoRiskResolvesToStandard() {
        assertEquals(SecurityLevel.STANDARD,
                resolver.resolve("fs.read", new LevelHints(true, false, false, false, false)));
        assertEquals(SecurityLevel.STANDARD,
                resolver.resolve("echo", new LevelHints(true, false, false, false, false)));
    }

    // trusted + highImpact → ELEVATED (not RESTRICTED)
    @Test
    void trustedHighImpactResolvesToElevated() {
        assertEquals(SecurityLevel.ELEVATED,
                resolver.resolve("shell.exec", new LevelHints(true, false, false, false, true)));
        assertEquals(SecurityLevel.ELEVATED,
                resolver.resolve("code.exec", new LevelHints(true, false, false, false, true)));
    }

    // trusted + writesOutsideWorkspace → ELEVATED
    @Test
    void trustedWritesOutsideResolvesToElevated() {
        assertEquals(SecurityLevel.ELEVATED,
                resolver.resolve("fs.write", new LevelHints(true, true, false, false, false)));
    }

    // untrusted + highImpact → RESTRICTED
    @Test
    void untrustedHighImpactResolvesToRestricted() {
        assertEquals(SecurityLevel.RESTRICTED,
                resolver.resolve("shell.exec", new LevelHints(false, false, false, false, true)));
        assertEquals(SecurityLevel.RESTRICTED,
                resolver.resolve("unknown_tool", new LevelHints(false, false, false, false, true)));
    }

    // untrusted + network → RESTRICTED
    @Test
    void untrustedNetworkResolvesToRestricted() {
        assertEquals(SecurityLevel.RESTRICTED,
                resolver.resolve("network.fetch", new LevelHints(false, false, false, false, false)));
        assertEquals(SecurityLevel.RESTRICTED,
                resolver.resolve("web.fetch", new LevelHints(false, false, false, false, false)));
    }

    // untrusted, no high impact → ELEVATED
    @Test
    void untrustedNoHighImpactResolvesToElevated() {
        assertEquals(SecurityLevel.ELEVATED,
                resolver.resolve("fs.read", new LevelHints(false, false, false, false, false)));
    }

    // null hints treated as defaults → STANDARD (trusted default = true in defaults? no, defaults are all false)
    @Test
    void nullHintsTreatedAsDefaults() {
        // LevelHints.defaults() = all false = untrusted, no risk → ELEVATED
        assertEquals(SecurityLevel.ELEVATED,
                resolver.resolve("fs.read", null));
    }

    // tool name normalization: shell_exec == shell.exec
    @Test
    void toolNameNormalizationUnderscoreAndCase() {
        assertEquals(SecurityLevel.ELEVATED,
                resolver.resolve("SHELL_EXEC", new LevelHints(true, false, false, false, true)));
        assertEquals(SecurityLevel.ELEVATED,
                resolver.resolve("Shell_Exec", new LevelHints(true, false, false, false, true)));
    }
}
