package io.nop.ai.agent.security;

import java.util.Locale;
import java.util.Set;

/**
 * Shipped default {@link ISecurityLevelResolver} implementing a
 * <b>trusted-by-default</b> variant of the design §5.1 rule table (design
 * doc §4.9 decision 1). This is the engine default, replacing the former
 * {@link NoOpSecurityLevelResolver} default (plan 200).
 *
 * <p><b>Trusted-by-default variant</b>: the design §5.1 table marks
 * {@code shell.exec} with {@code highImpact → RESTRICTED}. However, the
 * shipped {@link DefaultLevelHintsProducer} produces {@code highImpact=true}
 * for shell.exec unconditionally (tool-name classification). A full
 * implementation of the table would classify every shell.exec as RESTRICTED
 * → {@link DefaultApprovalGate} denies RESTRICTED → the engine cannot
 * execute shell tools at all.
 *
 * <p>To preserve engine usability while still providing functional
 * classification, this resolver applies the following rule:
 *
 * <ul>
 *   <li>When {@code trustedSource=true} (the agent's own reasoning chain
 *       produced the tool call — the baseline scenario under
 *       {@link DefaultContentTrustEvaluator}): {@code highImpact} upgrades
 *       to {@link SecurityLevel#ELEVATED} (not RESTRICTED).</li>
 *   <li>When {@code trustedSource=false}: {@code highImpact} upgrades to
 *       {@link SecurityLevel#RESTRICTED}, and network fetch tools upgrade
 *       to RESTRICTED as well.</li>
 * </ul>
 *
 * <p>This means trusted high-impact operations (shell.exec from the agent)
 * are classified as ELEVATED (not blocked by the approval gate), while
 * untrusted high-impact operations are classified as RESTRICTED
 * (defense-in-depth deny by {@link DefaultApprovalGate}).
 *
 * <p>{@link NoOpSecurityLevelResolver} is retained as a public opt-in for
 * integrators who need the "all STANDARD" behavior (equivalent to no
 * classification).
 */
public final class DefaultSecurityLevelResolver implements ISecurityLevelResolver {

    private static final Set<String> NETWORK_TOOLS = Set.of(
            "network.fetch", "web.fetch"
    );

    private static final Set<String> SHELL_TOOLS = Set.of(
            "shell.exec", "code.exec"
    );

    @Override
    public SecurityLevel resolve(String actionKind, LevelHints hints) {
        LevelHints h = hints != null ? hints : LevelHints.defaults();
        String kind = normalize(actionKind);

        boolean trusted = h.isTrustedSource();

        if (!trusted) {
            if (h.isHighImpact()) {
                return SecurityLevel.RESTRICTED;
            }
            if (NETWORK_TOOLS.contains(kind)) {
                return SecurityLevel.RESTRICTED;
            }
            if (h.isWritesOutsideWorkspace()) {
                return SecurityLevel.ELEVATED;
            }
            return SecurityLevel.ELEVATED;
        }

        if (h.isHighImpact()) {
            return SecurityLevel.ELEVATED;
        }
        if (h.isWritesOutsideWorkspace()) {
            return SecurityLevel.ELEVATED;
        }
        return SecurityLevel.STANDARD;
    }

    private static String normalize(String actionKind) {
        if (actionKind == null || actionKind.isEmpty()) {
            return "";
        }
        return actionKind.replace('_', '.').toLowerCase(Locale.ROOT);
    }
}
