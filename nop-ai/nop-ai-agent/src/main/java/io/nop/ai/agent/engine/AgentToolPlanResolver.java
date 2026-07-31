package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.ParentPermissionConstraint;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolModel;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tool-visibility and permission-inheritance computation for the ReAct
 * loop (extracted from {@link ReActAgentExecutor}, MA4.2-05). Builds the
 * LLM-visible tool list with tag-based filtering ({@link #buildToolDefinitions})
 * and computes the effective (parent-clamped) allowed-tool set, path roots
 * and path-rule chain propagated to engine-aware tools.
 */
public class AgentToolPlanResolver {
    private final IToolManager toolManager;

    public AgentToolPlanResolver(IToolManager toolManager) {
        this.toolManager = toolManager;
    }

    // ---- moved verbatim from ReActAgentExecutor (MA4.2-05 split) ----
    /**
     * Plan 296 (WS2): build the LLM-visible tool list with tag-based visibility
     * filtering. Loads all tools from {@code toolManager.listTools()} and
     * applies, in order:
     * <ol>
     *   <li><b>_tools whitelist</b> (backward compat, D7): when
     *       {@code AgentModel.getTools()} is non-empty, only tools whose name
     *       is in the set survive. {@code null}/empty means no name
     *       restriction (all tools subject to further filtering).</li>
     *   <li><b>meta tools</b> (D10): tools with {@code meta=true} bypass
     *       denyTools/activeTags/denyTags filtering — they are always visible
     *       once they pass the _tools whitelist.</li>
     *   <li><b>denyTools</b>: remove tools by name (highest deny priority).</li>
     *   <li><b>activeTags</b>: when non-empty, keep only tools whose tags
     *       intersect the resolved active tag set. Empty = no tag filter
     *       (all visible).</li>
     *   <li><b>denyTags</b>: remove tools containing any denied tag.</li>
     * </ol>
     *
     * <p>The session's runtime activeTags override (set by the
     * {@code set-active-tags} meta-tool) takes precedence over the model's
     * declared activeTags.
     */
    public List<ChatToolDefinition> buildToolDefinitions(AgentModel agentModel, AgentSession session) {
        Set<String> declaredTools = agentModel.getTools();
        boolean hasDeclaredTools = declaredTools != null && !declaredTools.isEmpty();
        Set<String> denyTools = agentModel.getDenyTools();
        Set<String> denyTags = agentModel.getDenyTags();
        Set<String> activeTags = session != null
                ? session.resolveActiveTags(agentModel)
                : (agentModel.getActiveTags() != null ? agentModel.getActiveTags() : Collections.emptySet());

        // Build the candidate tool set. When the agent declares an explicit
        // _tools whitelist, load each by name via loadTool() (backward compat
        // with existing test stubs that override loadTool but return empty for
        // listTools). When no whitelist is declared, use listTools() for
        // tag-based discovery.
        List<AiToolModel> candidates = new ArrayList<>();
        if (hasDeclaredTools) {
            for (String name : declaredTools) {
                AiToolModel tool = toolManager.loadTool(name);
                if (tool != null) {
                    candidates.add(tool);
                }
            }
        } else {
            candidates.addAll(toolManager.listTools());
        }

        // Always merge in meta tools from the registry (D10: always visible,
        // bypass the _tools whitelist). They are discovered via listTools().
        // When listTools() returns empty (test stubs), no meta tools are added.
        if (!hasDeclaredTools) {
            // Already loaded all from listTools() above; meta tools are in candidates.
        } else {
            for (AiToolModel tool : toolManager.listTools()) {
                if (tool.isMeta()) {
                    boolean alreadyPresent = false;
                    for (AiToolModel c : candidates) {
                        if (c.getName() != null && c.getName().equals(tool.getName())) {
                            alreadyPresent = true;
                            break;
                        }
                    }
                    if (!alreadyPresent) {
                        candidates.add(tool);
                    }
                }
            }
        }

        List<ChatToolDefinition> defs = new ArrayList<>();
        for (AiToolModel tool : candidates) {
            // Step 2: meta tools bypass tag/deny filtering (D10)
            if (tool.isMeta()) {
                defs.add(toToolDefinition(tool));
                continue;
            }
            // Step 3: denyTools (name-based, highest deny priority)
            if (denyTools != null && denyTools.contains(tool.getName())) {
                continue;
            }
            // Step 4: activeTags intersection (empty = all visible)
            Set<String> toolTags = tool.getTags();
            if (activeTags != null && !activeTags.isEmpty()) {
                if (toolTags == null || toolTags.isEmpty() || !intersects(toolTags, activeTags)) {
                    continue;
                }
            }
            // Step 5: denyTags
            if (denyTags != null && !denyTags.isEmpty() && toolTags != null && !toolTags.isEmpty()) {
                if (intersects(toolTags, denyTags)) {
                    continue;
                }
            }
            defs.add(toToolDefinition(tool));
        }
        return defs;
    }
    /**
     * Return true if the two sets share at least one element.
     */
    public static boolean intersects(Set<?> a, Set<?> b) {
        Set<?> smaller = a.size() <= b.size() ? a : b;
        Set<?> larger = smaller == a ? b : a;
        for (Object o : smaller) {
            if (larger.contains(o)) {
                return true;
            }
        }
        return false;
    }
    /**
     * Compute the current agent's <b>effective (clamped)</b> allowed tool set,
     * propagated to engine-aware tools (e.g. {@code call-agent}) via
     * {@link AgentToolExecuteContext#getAllowedTools()} for sub-agent
     * permission inheritance (design §4.4: 工具权限 = 父权限 ∩ 子配置).
     *
     * <p>Clamping rule:
     * <ul>
     *   <li>If an incoming parent constraint is present in the execution
     *       context metadata (key
     *       {@link ParentPermissionConstraint#METADATA_KEY}), the effective set
     *       is the intersection of the parent's allowed tool set and the
     *       current agent's <b>declared</b> tool set
     *       ({@link AgentModel#getTools()}). This is what makes nested
     *       delegation safe: a middle agent B's effective set is already
     *       clamped to A's constraint, so when B delegates to C, C inherits
     *       B's clamped set rather than B's declared set.</li>
     *   <li>If no parent constraint is present (top-level agent), the
     *       effective set equals the declared set unchanged.</li>
     * </ul>
     *
     * @return the effective tool set; never null (an agent with no declared
     *         tools yields an empty set)
     */
    public Set<String> computeEffectiveAllowedTools(AgentModel agentModel, AgentExecutionContext ctx) {
        Set<String> declared = agentModel.getTools();
        if (declared == null) {
            declared = Collections.emptySet();
        }

        ParentPermissionConstraint parentConstraint = null;
        if (ctx.getMetadata() != null) {
            Object raw = ctx.getMetadata().get(ParentPermissionConstraint.METADATA_KEY);
            if (raw instanceof ParentPermissionConstraint) {
                parentConstraint = (ParentPermissionConstraint) raw;
            }
        }

        if (parentConstraint == null) {
            return new HashSet<>(declared);
        }

        Set<String> parentAllowed = parentConstraint.getAllowedTools();
        Set<String> effective = new HashSet<>(declared);
        effective.retainAll(parentAllowed);
        return effective;
    }
    /**
     * Resolve the agent's declared {@code workDir} (from
     * {@link AgentModel#getWorkDir()}) to a {@link File}, or {@code null} when
     * no workDir is declared (ABSENT). Replaces the hardcoded {@code new File(".")}
     * so each agent carries its own declared working directory — distinct agents
     * with distinct {@code workDir} values produce distinct effective path roots
     * rather than the shared JVM CWD (design §4.4).
     */
    public File resolveWorkDir(AgentModel agentModel) {
        String workDir = agentModel.getWorkDir();
        if (workDir == null || workDir.trim().isEmpty()) {
            return null;
        }
        return new File(workDir);
    }
    /**
     * Compute the current agent's <b>effective (clamped)</b> allowed path roots,
     * propagated to engine-aware tools (e.g. {@code call-agent}) via
     * {@link AgentToolExecuteContext#getAllowedPathRoots()} for sub-agent
     * path-permission inheritance (design §4.4:
     * 文件权限 = 父权限 ∩ 子配置).
     *
     * <p>Clamping rule (three-valued ABSENT/PRESENT semantics):
     * <ul>
     *   <li>If an incoming parent constraint is present with PRESENT path roots,
     *       the effective roots are the subset of the agent's own declared roots
     *       (from {@code workDir}) that fall UNDER any incoming parent root. This
     *       is what makes nested delegation safe: a middle agent B's effective
     *       roots are already clamped within A's scope, so when B delegates to C,
     *       C inherits a scope within A's. If none of the agent's own roots are
     *       under any incoming root, the effective set is empty (PRESENT({}) =
     *       deny all paths — maximum restriction, e.g. when the agent declares a
     *       workDir outside the parent's scope).</li>
     *   <li>If an incoming parent constraint is present but its path roots are
     *       ABSENT (null), the effective roots equal the agent's own declared
     *       roots (ABSENT acts as identity).</li>
     *   <li>If no parent constraint is present (top-level agent), the effective
     *       roots equal the agent's own declared roots
     *       (PRESENT({normalized workDir}) or ABSENT when workDir is null).</li>
     * </ul>
     *
     * @return the effective path roots; {@code null} (ABSENT) when the agent has
     *         no declared path scope and no incoming parent roots; a non-null
     *         Set (PRESENT, possibly empty) when path-scope confinement is active
     */
    public Set<String> computeEffectivePathRoots(AgentModel agentModel, AgentExecutionContext ctx) {
        Set<String> ownRoots = computeOwnDeclaredPathRoots(agentModel);

        ParentPermissionConstraint parentConstraint = null;
        if (ctx.getMetadata() != null) {
            Object raw = ctx.getMetadata().get(ParentPermissionConstraint.METADATA_KEY);
            if (raw instanceof ParentPermissionConstraint) {
                parentConstraint = (ParentPermissionConstraint) raw;
            }
        }

        if (parentConstraint == null || !parentConstraint.hasPathRoots()) {
            // No incoming parent roots (ABSENT) → effective = own declared roots
            // (ABSENT or PRESENT)
            return ownRoots;
        }

        Set<String> incomingRoots = parentConstraint.getAllowedPathRoots();

        if (ownRoots == null) {
            // No own declared roots → inherit parent's roots (ABSENT is identity)
            return new HashSet<>(incomingRoots);
        }

        // Both PRESENT → keep own roots that are under any incoming root
        Set<String> effective = new HashSet<>();
        for (String ownRoot : ownRoots) {
            if (isUnderAnyRoot(ownRoot, incomingRoots)) {
                effective.add(ownRoot);
            }
        }
        return effective;
    }
    /**
     * Compute the agent's own declared path roots from its {@code workDir}.
     *
     * @return {@code null} (ABSENT) when no workDir is declared; a non-null Set
     *         containing the normalized workDir as the single root when declared
     */
    public Set<String> computeOwnDeclaredPathRoots(AgentModel agentModel) {
        String workDir = agentModel.getWorkDir();
        if (workDir == null || workDir.trim().isEmpty()) {
            return null;
        }
        String normalized = DefaultPathAccessChecker.normalizePathStatic(workDir);
        if (normalized == null) {
            return null;
        }
        return new HashSet<>(Collections.singleton(normalized));
    }
    /**
     * Compute the current agent's <b>effective (clamped)</b> allowed path rules,
     * propagated to engine-aware tools (e.g. {@code call-agent}) via
     * {@link AgentToolExecuteContext#getAllowedPathRules()} for sub-agent
     * path-rule inheritance (design §4.4).
     *
     * <p>Rule-chain accumulation (design §4.3/§4.4):
     * <ul>
     *   <li>If no incoming parent constraint or parent rules ABSENT → effective
     *       = own declared rules (from {@link AgentModel#getPathRules()}).</li>
     *   <li>If incoming parent rules PRESENT → effective = accumulated chain
     *       (incoming parent rules + own declared rules, parent rules first).
     *       This accumulated chain is evaluated with deny-wins by the
     *       sub-agent's {@link ParentConstrainedPathAccessChecker}.</li>
     * </ul>
     *
     * @return the effective path-rule chain; {@code null} (ABSENT) when the
     *         agent has no own path-rules and no incoming parent rules; a
     *         non-null List (PRESENT) when path-rule confinement is active
     */
    public java.util.List<io.nop.ai.agent.model.PathRuleModel> computeEffectivePathRules(
            AgentModel agentModel, AgentExecutionContext ctx) {
        java.util.List<io.nop.ai.agent.model.PathRuleModel> ownRules = agentModel.getPathRules();
        boolean ownHasRules = ownRules != null && !ownRules.isEmpty();

        ParentPermissionConstraint parentConstraint = null;
        if (ctx.getMetadata() != null) {
            Object raw = ctx.getMetadata().get(ParentPermissionConstraint.METADATA_KEY);
            if (raw instanceof ParentPermissionConstraint) {
                parentConstraint = (ParentPermissionConstraint) raw;
            }
        }

        boolean parentHasRules = parentConstraint != null && parentConstraint.hasPathRules();

        if (!ownHasRules && !parentHasRules) {
            return null;
        }

        if (!parentHasRules) {
            return ownRules;
        }

        // Accumulate: incoming parent rules + own declared rules
        java.util.List<io.nop.ai.agent.model.PathRuleModel> incomingRules =
                parentConstraint.getAllowedPathRules();
        java.util.List<io.nop.ai.agent.model.PathRuleModel> effective = new ArrayList<>(incomingRules);
        if (ownHasRules) {
            effective.addAll(ownRules);
        }
        return effective;
    }
    /**
     * Check whether a normalized path root is "under" any of the given
     * (possibly non-normalized) root set. Uses the same normalization as
     * {@link DefaultPathAccessChecker#normalizePathStatic(String)}.
     */
    public boolean isUnderAnyRoot(String normalizedPath, Set<String> roots) {
        for (String root : roots) {
            if (root == null || root.trim().isEmpty()) {
                continue;
            }
            String normalizedRoot = DefaultPathAccessChecker.normalizePathStatic(root);
            if (normalizedRoot == null) {
                continue;
            }
            if (normalizedPath.equals(normalizedRoot)) {
                return true;
            }
            String rootWithSlash = normalizedRoot.endsWith("/")
                    ? normalizedRoot : normalizedRoot + "/";
            if (normalizedPath.startsWith(rootWithSlash)) {
                return true;
            }
        }
        return false;
    }
    public static ChatToolDefinition toToolDefinition(AiToolModel toolModel) {
        Map<String, Object> parameters = ToolSchemaConverter.convert(toolModel.getSchema());
        if (parameters != null) {
            return ChatToolDefinition.of(toolModel.getName(), toolModel.getDescription(), parameters);
        }
        return ChatToolDefinition.of(toolModel.getName(), toolModel.getDescription());
    }
}

