package io.nop.ai.agent.contribution;

/**
 * The seven contribution types a plugin can register into the agent engine via
 * {@link IContributionRegistry} (design {@code nop-ai-agent-hook-skill-engine.md}
 * §8 / analysis {@code 2026-06-07-agent-design-patterns-for-nop.md} §2.8).
 *
 * <p>Each type maps to an existing engine extension point. Only {@link #HOOK}
 * and {@link #PROMPT} are auto-resolved by the engine at assembly time in this
 * version; the remaining five are stored and queryable but their deep engine
 * integration is an explicit successor (see plan 217 Non-Goals).
 *
 * <ul>
 *   <li>{@link #TOOL} — register a new tool into the tool set</li>
 *   <li>{@link #COMMAND} — register a slash command</li>
 *   <li>{@link #HOOK} — register a lifecycle hook (payload carries
 *       {@code AgentLifecyclePoint} + {@code IAgentLifecycleHook})</li>
 *   <li>{@link #MCP_SERVER} — provide an MCP service surface</li>
 *   <li>{@link #PERMISSION_RULE} — register a custom permission rule</li>
 *   <li>{@link #PROMPT} — inject a prompt fragment (payload is a String)</li>
 *   <li>{@link #ROUTER} — register a custom routing strategy</li>
 * </ul>
 */
public enum ContributionType {
    TOOL,
    COMMAND,
    HOOK,
    MCP_SERVER,
    PERMISSION_RULE,
    PROMPT,
    ROUTER
}
