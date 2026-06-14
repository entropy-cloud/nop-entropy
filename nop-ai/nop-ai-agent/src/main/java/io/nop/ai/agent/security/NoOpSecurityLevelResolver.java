package io.nop.ai.agent.security;

/**
 * No-op {@link ISecurityLevelResolver} used as the default when no resolver is
 * registered. All action kinds and all hints resolve to
 * {@link SecurityLevel#STANDARD}, equivalent to no classification. Consistent
 * with the {@code NoOpContentGuardrail} / {@code PassThroughModelRouter} /
 * {@code PassThroughPermissionMatrix} sibling pattern.
 *
 * <p>The NoOp standard-only semantics are semantically correct (design §5.1
 * default): the shipped default does not impose security-level classification.
 * A functional rule-table resolver (encoding the design §5.1 deterministic
 * upgrade table) is registered explicitly via
 * {@code DefaultAgentEngine.setSecurityLevelResolver}.
 */
public final class NoOpSecurityLevelResolver implements ISecurityLevelResolver {

    private static final NoOpSecurityLevelResolver INSTANCE = new NoOpSecurityLevelResolver();

    private NoOpSecurityLevelResolver() {
    }

    public static ISecurityLevelResolver noOp() {
        return INSTANCE;
    }

    @Override
    public SecurityLevel resolve(String actionKind, LevelHints hints) {
        return SecurityLevel.STANDARD;
    }
}
