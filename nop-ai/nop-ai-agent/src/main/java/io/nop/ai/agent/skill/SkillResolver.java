package io.nop.ai.agent.skill;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentModel;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Matches an {@link AgentModel}'s {@code availableSkills} /
 * {@code requiredSkills} declarations against an {@link ISkillProvider}'s
 * registry using declaration filtering (design {@code skill-system-design.md}
 * §5.2 Phase 1, §5.3).
 *
 * <p>Matching rules:
 * <ul>
 *   <li><b>{@code requiredSkills}</b> — all must be present in the registry.
 *       If any is missing, {@link #resolve} throws {@link NopAiAgentException}
 *       fail-fast (the agent does not execute). All found ones are
 *       force-activated.</li>
 *   <li><b>{@code availableSkills}</b> — found ones are activated; missing
 *       ones are silently ignored (no error).</li>
 *   <li>Activated set = {@code requiredSkills ∪ (availableSkills ∩ registry)}.</li>
 * </ul>
 *
 * <p>Null {@code availableSkills} / {@code requiredSkills} on the agent model
 * are treated as empty sets (the {@code AgentModel} fields are nullable).
 *
 * <p>From the activated set, the resolver builds a {@link SkillAssemblyResult}
 * collecting each skill's {@code goal} (instruction fragment), tool-name
 * dependencies, and {@code resourceScope}.
 */
public class SkillResolver {

    private final ISkillProvider provider;

    public SkillResolver(ISkillProvider provider) {
        if (provider == null) {
            throw new NopAiAgentException("SkillResolver requires a non-null ISkillProvider");
        }
        this.provider = provider;
    }

    /**
     * Resolve skills for the given agent model and build the assembly result.
     *
     * @param agentModel the agent model; its {@code availableSkills} /
     *                   {@code requiredSkills} declarations drive matching. If
     *                   {@code null}, an empty assembly is returned.
     * @return the assembly result (never {@code null}; empty when no skills
     *         are activated)
     * @throws NopAiAgentException if a {@code requiredSkill} is not found in
     *         the registry (fail-fast, clear message naming the skill and agent)
     */
    public SkillAssemblyResult resolve(AgentModel agentModel) {
        if (agentModel == null) {
            return SkillAssemblyResult.empty();
        }

        Map<String, SkillModel> registry = loadRegistry();

        Set<String> available = agentModel.getAvailableSkills();
        Set<String> required = agentModel.getRequiredSkills();

        String agentName = agentModel.getName();

        // requiredSkills: all must be present — fail-fast.
        if (required != null && !required.isEmpty()) {
            for (String name : required) {
                if (!registry.containsKey(name)) {
                    throw new NopAiAgentException(
                            "Required skill '" + name + "' is not registered"
                                    + (agentName != null ? " for agent '" + agentName + "'" : "")
                                    + ". Available registered skills: " + registry.keySet());
                }
            }
        }

        SkillAssemblyResult.Builder builder = SkillAssemblyResult.builder();
        // Track activated names to avoid duplicate activation when requiredSkills
        // and availableSkills overlap (design §5.3: activated set is a set union).
        Set<String> activated = new LinkedHashSet<>();

        // requiredSkills are all present (verified above) → force-activate.
        if (required != null) {
            for (String name : required) {
                SkillModel skill = registry.get(name);
                if (skill != null && activated.add(name)) {
                    builder.addSkill(skill);
                }
            }
        }

        // availableSkills: found ones activated, missing ones silently ignored.
        if (available != null) {
            for (String name : available) {
                if (registry.containsKey(name) && activated.add(name)) {
                    builder.addSkill(registry.get(name));
                }
            }
        }

        return builder.build();
    }

    private Map<String, SkillModel> loadRegistry() {
        Collection<SkillModel> skills = provider.getSkills();
        Map<String, SkillModel> map = new LinkedHashMap<>();
        if (skills == null) {
            return map;
        }
        for (SkillModel skill : skills) {
            if (skill != null && skill.getName() != null) {
                map.putIfAbsent(skill.getName(), skill);
            }
        }
        return map;
    }
}
