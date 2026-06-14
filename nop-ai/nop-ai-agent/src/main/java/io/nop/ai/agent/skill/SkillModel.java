package io.nop.ai.agent.skill;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Scheduling-layer representation of a skill (design {@code skill-system-design.md}
 * §4.1 first-phase subset). A skill is a structured capability definition that
 * the engine discovers via an {@link ISkillProvider}, matches against an
 * agent's {@code availableSkills} / {@code requiredSkills} declarations, and
 * assembles into instruction fragments + tool-name dependencies at execution
 * setup.
 *
 * <p>This is a hand-written POJO loaded from {@code *.skill.yaml} by
 * {@link FileSystemSkillProvider} (decision: hand-written model for phase 1 —
 * no xdef/codegen dependency; can migrate to an xdef-generated model later).
 * Tool-name {@link #getDependencies() dependencies} reference existing
 * registry tools and are resolved through the existing
 * {@code IToolManager.loadTool()} pipeline (no parallel tool type — consistent
 * with the talent tool contract from plan 160).
 *
 * <p>Fields marked "stored for future use" are populated and round-tripped but
 * not consulted by the phase-1 declaration-based matcher:
 * <ul>
 *   <li>{@link #getTopPattern()} — reserved for phase-2 coarse filtering.</li>
 *   <li>{@link #getIntentSignature()} — reserved for phase-3 exact matching.</li>
 *   <li>{@link #getResourceScope()} — collected for tracing, not enforced.</li>
 * </ul>
 */
public class SkillModel {

    private String name;
    private String goal;
    private List<String> intentSignature;
    private SkillTopPattern topPattern;
    private List<String> dependencies;
    private Set<String> tags;
    private Set<SkillResourceScope> resourceScope;

    /**
     * Required unique key identifying this skill. Referenced by
     * {@code AgentModel.availableSkills} / {@code requiredSkills}.
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Skill description, injected into the system prompt as an instruction
     * fragment when the skill is activated.
     */
    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    /**
     * Matching signatures. Stored but not used in phase-1 declaration matching
     * (reserved for phase-3 exact matching, design §5.2 Phase 3). Normalized
     * from a YAML scalar or list into a list.
     */
    public List<String> getIntentSignature() {
        return intentSignature;
    }

    public void setIntentSignature(List<String> intentSignature) {
        this.intentSignature = intentSignature;
    }

    /**
     * Top-level behavioural pattern. Stored for future use (phase-2 coarse
     * filtering); the phase-1 matcher is purely declaration-based.
     */
    public SkillTopPattern getTopPattern() {
        return topPattern;
    }

    public void setTopPattern(SkillTopPattern topPattern) {
        this.topPattern = topPattern;
    }

    /**
     * Tool and/or skill names this skill depends on. Tool names are used at
     * assembly time: they are resolved through {@code IToolManager.loadTool()}
     * and merged into the active tool definitions (design §7.4 — skill only
     * declares tool names, definitions remain in {@code tool.xdef}).
     */
    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    /**
     * Resource boundary declared by this skill. Collected in the assembly
     * result for tracing; not enforced against the permission system in
     * phase 1.
     */
    public Set<SkillResourceScope> getResourceScope() {
        return resourceScope;
    }

    public void setResourceScope(Set<SkillResourceScope> resourceScope) {
        this.resourceScope = resourceScope;
    }

    /**
     * Collect this skill's tool-name dependencies into the given sink. Only
     * non-null, non-empty dependency names are added.
     */
    public void collectToolDependencies(Collection<String> sink) {
        if (dependencies == null || sink == null) {
            return;
        }
        for (String dep : dependencies) {
            if (dep != null && !dep.isEmpty()) {
                sink.add(dep);
            }
        }
    }

    /**
     * Collect this skill's resource scopes into the given sink.
     */
    public void collectResourceScope(Collection<SkillResourceScope> sink) {
        if (resourceScope == null || sink == null) {
            return;
        }
        sink.addAll(resourceScope);
    }

    /**
     * Helper for tests/providers that want a mutable copy of the tags.
     */
    public static Set<String> copyTags(Set<String> source) {
        if (source == null) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(source);
    }
}
