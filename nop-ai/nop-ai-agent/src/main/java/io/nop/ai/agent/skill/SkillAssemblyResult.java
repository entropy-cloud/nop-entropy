package io.nop.ai.agent.skill;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Result of skill assembly (design {@code skill-system-design.md} §5.4).
 * Carries the merged contribution of all activated skills:
 * <ul>
 *   <li>{@link #getInstructions()} — each activated skill's {@code goal},
 *       injected as instruction fragments into the system-prompt context.</li>
 *   <li>{@link #getToolDependencies()} — merged set of tool-name dependencies
 *       across activated skills, resolved through {@code IToolManager.loadTool()}
 *       at execution setup.</li>
 *   <li>{@link #getResourceScope()} — merged resource-scope declaration,
 *       collected for tracing/observability (not enforced in phase 1).</li>
 *   <li>{@link #getActivatedSkillNames()} — names of activated skills, for
 *       tracing/logging.</li>
 * </ul>
 *
 * <p>An empty assembly (no activated skills) is a valid, explicit result — it
 * means no skill instructions or tools are injected. It is never produced by
 * swallowing an error.
 */
public final class SkillAssemblyResult {

    private final List<String> instructions;
    private final Set<String> toolDependencies;
    private final Set<SkillResourceScope> resourceScope;
    private final Set<String> activatedSkillNames;

    public SkillAssemblyResult(List<String> instructions,
                               Set<String> toolDependencies,
                               Set<SkillResourceScope> resourceScope,
                               Set<String> activatedSkillNames) {
        this.instructions = instructions != null
                ? Collections.unmodifiableList(new ArrayList<>(instructions))
                : Collections.emptyList();
        this.toolDependencies = toolDependencies != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(toolDependencies))
                : Collections.emptySet();
        this.resourceScope = resourceScope != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(resourceScope))
                : Collections.emptySet();
        this.activatedSkillNames = activatedSkillNames != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(activatedSkillNames))
                : Collections.emptySet();
    }

    /**
     * An empty assembly: no instructions, no tool dependencies, no resource
     * scope, no activated skills.
     */
    public static SkillAssemblyResult empty() {
        return new SkillAssemblyResult(Collections.emptyList(), null, null, null);
    }

    /**
     * Instruction fragments (one per activated skill's goal, non-null/non-empty
     * goals only), in activation order. Never null.
     */
    public List<String> getInstructions() {
        return instructions;
    }

    /**
     * Merged tool-name dependencies across all activated skills. Never null.
     */
    public Set<String> getToolDependencies() {
        return toolDependencies;
    }

    /**
     * Merged resource-scope declarations across all activated skills. Never
     * null.
     */
    public Set<SkillResourceScope> getResourceScope() {
        return resourceScope;
    }

    /**
     * Names of activated skills, for tracing/logging. Never null.
     */
    public Set<String> getActivatedSkillNames() {
        return activatedSkillNames;
    }

    /**
     * Whether any skill was activated.
     */
    public boolean isEmpty() {
        return activatedSkillNames.isEmpty();
    }

    @Override
    public String toString() {
        return "SkillAssemblyResult{activated=" + activatedSkillNames
                + ", instructions=" + instructions.size()
                + ", toolDependencies=" + toolDependencies
                + ", resourceScope=" + resourceScope + "}";
    }

    /**
     * Builder used by {@link SkillResolver} to accumulate contributions from
     * each activated skill.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<String> instructions = new ArrayList<>();
        private final Set<String> toolDependencies = new LinkedHashSet<>();
        private final Set<SkillResourceScope> resourceScope = new LinkedHashSet<>();
        private final Set<String> activatedSkillNames = new LinkedHashSet<>();

        private Builder() {
        }

        public Builder addSkill(SkillModel skill) {
            activatedSkillNames.add(skill.getName());
            String goal = skill.getGoal();
            if (goal != null && !goal.isEmpty()) {
                instructions.add(goal);
            }
            skill.collectToolDependencies(toolDependencies);
            skill.collectResourceScope(resourceScope);
            return this;
        }

        public Builder addAllInstructions(Collection<String> extra) {
            if (extra != null) {
                instructions.addAll(extra);
            }
            return this;
        }

        public Builder addAllToolDependencies(Collection<String> extra) {
            if (extra != null) {
                for (String dep : extra) {
                    if (dep != null && !dep.isEmpty()) {
                        toolDependencies.add(dep);
                    }
                }
            }
            return this;
        }

        public Builder addActivatedName(String name) {
            if (name != null && !name.isEmpty()) {
                activatedSkillNames.add(name);
            }
            return this;
        }

        public SkillAssemblyResult build() {
            return new SkillAssemblyResult(instructions, toolDependencies, resourceScope, activatedSkillNames);
        }
    }
}
