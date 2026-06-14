package io.nop.ai.agent.security;

/**
 * Layer 2 policy-extension contract: security-level resolution (design §5.1).
 * Given an action kind (a tool name / operation category such as
 * {@code fs.read}, {@code shell.exec}, {@code network.fetch}) and the auditable
 * {@link LevelHints}, the resolver returns the {@link SecurityLevel} for the
 * action.
 *
 * <p><b>Deterministic rule table</b> (design §5.1, no AI decision):
 *
 * <table>
 *   <tr><th>action_kind</th><th>Default level</th><th>Upgrade condition</th></tr>
 *   <tr><td>{@code fs.read}, {@code fs.list}, {@code fs.grep}</td><td>STANDARD</td><td>&mdash;</td></tr>
 *   <tr><td>{@code fs.write}, {@code fs.edit}, {@code patch.apply}</td><td>STANDARD</td><td>{@code writesOutsideWorkspace} &rarr; ELEVATED</td></tr>
 *   <tr><td>{@code shell.exec}, {@code code.exec}</td><td>STANDARD</td><td>{@code !trustedSource} &rarr; ELEVATED; {@code highImpact} &rarr; RESTRICTED</td></tr>
 *   <tr><td>{@code network.fetch}, {@code web.fetch}</td><td>STANDARD</td><td>{@code !trustedSource} &rarr; RESTRICTED</td></tr>
 *   <tr><td>other</td><td>STANDARD</td><td>{@code !trustedSource} &rarr; ELEVATED; {@code highImpact} &rarr; RESTRICTED</td></tr>
 * </table>
 *
 * <p><b>Default</b>: {@link NoOpSecurityLevelResolver} &mdash; all action kinds
 * and all hints resolve to {@link SecurityLevel#STANDARD}, equivalent to no
 * classification, so engine behaviour is unchanged unless a resolver is
 * explicitly registered.
 *
 * <p><b>Dispatch-path consultation</b>: this contract surface is landed now;
 * the actual consultation call in the ReAct / tool-dispatch path is deferred to
 * a successor plan (requires {@code AgentExecutionContext} channelKind/principal
 * fields + a {@link LevelHints} runtime-production chain). The NoOp default
 * makes the wiring transparent to runtime behaviour.
 *
 * <p>The {@link SecurityLevel} enum is reused from L2-14 (plan 172), where it
 * was defined because the {@link IPermissionMatrix} contract requires it as an
 * input parameter. This resolver is the producer of that value.
 */
public interface ISecurityLevelResolver {

    /**
     * Resolve the security level for the given action kind and hints.
     *
     * @param actionKind the tool name / operation category (e.g. {@code fs.read},
     *                   {@code shell.exec}); may be {@code null} or unknown —
     *                   implementations should treat unknown kinds per the
     *                   design §5.1 "other" row
     * @param hints      the auditable boolean hints about the action; may be
     *                   {@code null} — implementations should treat a null hints
     *                   as {@link LevelHints#defaults()} (no risk signals)
     * @return the resolved security level; never {@code null}
     */
    SecurityLevel resolve(String actionKind, LevelHints hints);
}
