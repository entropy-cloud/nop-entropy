package io.nop.ai.agent.guardrail.rule;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.guardrail.GuardrailDirection;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads declarative YAML rule-set files into validated
 * {@link GuardrailRuleSet}s (design {@code guardrail-contract.md} §增量 2,
 * Decision A / Decision F). Uses nop's existing {@link ResourceHelper} +
 * {@link JsonTool#parseYaml} — the same pipeline as {@code CorpusLoader} and
 * {@code FileSystemSkillProvider}.
 *
 * <p>YAML format (root is a mapping with {@code id} and a {@code rules} list):
 *
 * <pre>
 * id: enterprise-compliance
 * rules:
 *   - id: fin-transaction
 *     direction: INPUT               # optional; null = both directions
 *     pattern: "(?i)transfer\\s+funds"
 *     action: BLOCK                  # BLOCK | MODIFY
 *     threatClass: FIN_COMPLIANCE    # optional
 *     excludes:                      # optional
 *       - general-chat
 *     dependsOn:                     # optional
 *       - audit-log
 *   - id: MODIFY-rule
 *     pattern: "secret-token-\\d+"
 *     action: MODIFY
 *     modifyReplacement: "[REDACTED]"
 * </pre>
 *
 * <p>Validation (id uniqueness / reference validity / dependsOn acyclic) runs
 * inside the {@link GuardrailRuleSet} constructor (fail-loud), per Minimum
 * Rules #24 — no silent skip of malformed rule sets.
 */
public class RuleSetLoader {

    /**
     * Load a single YAML rule-set file into a validated {@link GuardrailRuleSet}.
     */
    @SuppressWarnings("unchecked")
    public GuardrailRuleSet load(IResource resource) {
        if (resource == null) {
            throw new NopAiAgentException("RuleSetLoader.load: resource must not be null");
        }
        String text;
        try {
            text = ResourceHelper.readText(resource, null);
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "RuleSetLoader: failed to read rule-set file: " + resource.getPath(), e);
        }

        Object parsed;
        try {
            parsed = JsonTool.parseYaml(null, text);
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "RuleSetLoader: malformed YAML in rule-set file: " + resource.getPath(), e);
        }

        if (!(parsed instanceof Map)) {
            throw new NopAiAgentException(
                    "RuleSetLoader: rule-set root must be a YAML mapping with 'id' and 'rules', got "
                            + (parsed == null ? "null" : parsed.getClass().getName())
                            + ": " + resource.getPath());
        }

        Map<String, Object> root = (Map<String, Object>) parsed;
        String setId = readString(root, "id", resource.getPath(), true);

        Object rulesObj = root.get("rules");
        if (!(rulesObj instanceof List)) {
            throw new NopAiAgentException(
                    "RuleSetLoader: 'rules' must be a list in rule-set '" + setId
                            + "' (" + resource.getPath() + ")");
        }

        List<?> rawRules = (List<?>) rulesObj;
        List<GuardrailRule> rules = new ArrayList<>(rawRules.size());
        int index = 0;
        for (Object item : rawRules) {
            rules.add(parseRule((Map<String, Object>) item, setId, resource.getPath(), index));
            index++;
        }
        return new GuardrailRuleSet(setId, rules);
    }

    private static GuardrailRule parseRule(Map<String, Object> map, String setId,
                                           String source, int index) {
        if (map == null) {
            throw new NopAiAgentException(
                    "RuleSetLoader: rule #" + index + " is null in set '" + setId + "' (" + source + ")");
        }
        String id = readString(map, "id", source, true);
        String directionStr = readString(map, "direction", source, false);
        GuardrailDirection direction = directionStr == null
                ? null
                : parseDirection(directionStr, source, index);
        String pattern = readString(map, "pattern", source, true);
        String actionStr = readString(map, "action", source, true);
        RuleAction action = parseAction(actionStr, source, index);
        String modifyReplacement = readString(map, "modifyReplacement", source, false);
        List<String> dependsOn = readStringList(map, "dependsOn");
        List<String> excludes = readStringList(map, "excludes");
        String threatClass = readString(map, "threatClass", source, false);
        String description = readString(map, "description", source, false);

        return new GuardrailRule(id, direction, pattern, action, modifyReplacement,
                dependsOn, excludes, threatClass, description);
    }

    private static String readString(Map<String, Object> map, String key, String source,
                                     boolean required) {
        Object v = map.get(key);
        String s = v == null ? null : v.toString().trim();
        if (required && StringHelper.isEmpty(s)) {
            throw new NopAiAgentException(
                    "RuleSetLoader: missing required field '" + key + "' (" + source + ")");
        }
        return StringHelper.isEmpty(s) ? null : s;
    }

    @SuppressWarnings("unchecked")
    private static List<String> readStringList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        if (!(v instanceof List)) {
            throw new NopAiAgentException(
                    "RuleSetLoader: field '" + key + "' must be a list, got " + v.getClass().getName());
        }
        List<?> raw = (List<?>) v;
        List<String> out = new ArrayList<>(raw.size());
        for (Object o : raw) {
            if (o == null || StringHelper.isEmpty(o.toString().trim())) {
                throw new NopAiAgentException(
                        "RuleSetLoader: field '" + key + "' contains a null/empty entry");
            }
            out.add(o.toString().trim());
        }
        return out;
    }

    private static GuardrailDirection parseDirection(String value, String source, int index) {
        try {
            return GuardrailDirection.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new NopAiAgentException(
                    "RuleSetLoader: invalid direction '" + value + "' in rule #" + index
                            + " (" + source + "); expected INPUT or OUTPUT");
        }
    }

    private static RuleAction parseAction(String value, String source, int index) {
        try {
            return RuleAction.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new NopAiAgentException(
                    "RuleSetLoader: invalid action '" + value + "' in rule #" + index
                            + " (" + source + "); expected BLOCK or MODIFY");
        }
    }
}
