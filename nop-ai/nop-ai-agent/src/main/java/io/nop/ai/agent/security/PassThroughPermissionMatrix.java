package io.nop.ai.agent.security;

/**
 * Pass-through {@link IPermissionMatrix} used as the default when no
 * restrictive matrix is registered. All channels, all security levels, and all
 * principals are allowed. Consistent with the {@code NoOpContentGuardrail} /
 * {@code PassThroughModelRouter} sibling pattern.
 *
 * <p>The pass-through allow-all semantics are semantically correct (design §5.3
 * default): the shipped default does not impose channel-based restrictions.
 * Restrictive matrix implementations (e.g. one encoding the design §5.3
 * channel × level table) are registered explicitly via
 * {@code DefaultAgentEngine.setPermissionMatrix}.
 */
public final class PassThroughPermissionMatrix implements IPermissionMatrix {

    private static final PassThroughPermissionMatrix INSTANCE = new PassThroughPermissionMatrix();

    private PassThroughPermissionMatrix() {
    }

    public static IPermissionMatrix passThrough() {
        return INSTANCE;
    }

    @Override
    public MatrixDecision check(ChannelKind channel, Principal principal, SecurityLevel level) {
        return MatrixDecision.allow();
    }
}
