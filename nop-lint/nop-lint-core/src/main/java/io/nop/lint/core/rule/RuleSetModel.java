package io.nop.lint.core.rule;

import io.nop.commons.util.StringHelper;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.lint.core.NopLintException;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The typed ruleset model (roadmap item 27, design 09 §4 / design 10 §3):
 * a bundled id, inline rules carrying the full lint-rule surface, and
 * run-level exemptions. Parsed from the DynamicObject the XDSL pipeline
 * produces for {@code *.ruleset.yml} (registered through
 * {@code lint.register-model.xml}); every inner rule goes through
 * {@link RuleDslParser#parseRuleModel(Object)}, so inline rules validate
 * exactly like standalone rule files.
 *
 * <p>Fail-closed parsing (no silent skip): a missing ruleset id, an
 * exemption without a non-blank {@code reason}, or a {@code files} glob
 * that does not compile is a load failure. The exemption {@code rule} id is
 * NOT validated here — the unknown-rule-id check needs the run's full rule
 * id set (inline rules ∪ standalone {@code *.rule.yml} files), which only
 * exists after the whole prefix scan; {@code RuleSetLoader} performs it
 * then (checking earlier would reject legal references to rules loaded
 * later). The {@code ranges} field is parsed and kept for rule-defined
 * semantics; the v1 engine never matches it.</p>
 */
public record RuleSetModel(String id, List<RuleDslModel> rules, List<Exemption> exemptions) {

    /**
     * VFS path of the ruleset DSL metamodel.
     */
    public static final String RULESET_XDEF_PATH = "/nop/lint/schema/lint-ruleset.xdef";

    /**
     * One design 09 §4 exemption: a rule id, a mandatory documentation
     * reason, the file glob surface the v1 engine matches, and the optional
     * ranges keys kept for rule-defined semantics. The glob patterns are
     * compiled at parse time — a pattern that cannot compile fails the
     * ruleset load instead of never matching at run time.
     */
    public record Exemption(String ruleId, String reason, List<String> files, List<String> ranges,
                            List<PathMatcher> matchers) {
    }

    /**
     * Loads a ruleset model from a VFS resource path through the registered
     * component loader (xdef structural validation + x:extends delta merge)
     * and parses it into a typed {@link RuleSetModel}.
     *
     * @throws NopLintException when the model cannot be loaded or violates
     *                          the fail-closed parsing rules
     */
    public static RuleSetModel load(String resourcePath) {
        Object model;
        try {
            model = ResourceComponentManager.instance().loadComponentModel(resourcePath);
        } catch (Exception e) {
            throw new NopLintException("Failed to load lint ruleset model from resource '" + resourcePath
                    + "': " + e.getMessage(), e);
        }
        return parse(model);
    }

    /**
     * Parses the dynamic model produced by the XDSL pipeline into a typed,
     * immutable ruleset model.
     */
    public static RuleSetModel parse(Object model) {
        if (!(model instanceof DynamicObject dyn))
            throw new NopLintException("Unsupported lint ruleset model type: "
                    + (model == null ? "null" : model.getClass().getName())
                    + " (expected the DynamicObject produced by the XDSL pipeline)");

        String id = nonBlank(dyn.obj_propValues().get("id"));
        if (id == null)
            throw new NopLintException("Lint ruleset model is missing a non-empty 'id'");

        RuleDslParser parser = new RuleDslParser();
        List<RuleDslModel> rules = new ArrayList<>();
        Object rulesValue = dyn.obj_propValues().get("rules");
        if (rulesValue instanceof Collection<?> items) {
            for (Object item : items) {
                rules.add(parser.parseRuleModel(item));
            }
        }

        List<Exemption> exemptions = new ArrayList<>();
        Object exemptionsValue = dyn.obj_propValues().get("exemptions");
        if (exemptionsValue instanceof Collection<?> items) {
            int index = 0;
            for (Object item : items) {
                index++;
                exemptions.add(parseExemption(id, index, asProps(item)));
            }
        }

        return new RuleSetModel(id, List.copyOf(rules), List.copyOf(exemptions));
    }

    private static Exemption parseExemption(String rulesetId, int index, Map<String, Object> props) {
        String at = "ruleset '" + rulesetId + "' exemption #" + index;
        if (props == null)
            throw new NopLintException(at + " is not a mapping (the exemption surface is "
                    + "rule/reason/files/ranges)");

        String ruleId = nonBlank(props.get("rule"));
        if (ruleId == null)
            throw new NopLintException(at + " is missing a non-empty 'rule'");

        String reason = nonBlank(props.get("reason"));
        if (reason == null)
            throw new NopLintException(at + " (rule '" + ruleId + "') is missing a non-empty 'reason' "
                    + "(design 09 §4: an undocumented exemption must not load)");

        List<String> files = csv(props.get("files"));
        List<PathMatcher> matchers = new ArrayList<>(files.size());
        for (String glob : files) {
            try {
                matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + glob));
            } catch (Exception e) {
                throw new NopLintException(at + " (rule '" + ruleId + "') has an uncompilable files "
                        + "glob '" + glob + "': " + e.getMessage(), e);
            }
        }

        List<String> ranges = csv(props.get("ranges"));
        return new Exemption(ruleId, reason, List.copyOf(files), List.copyOf(ranges), List.copyOf(matchers));
    }

    private static List<String> csv(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Collection<?> items) {
            for (Object item : items) {
                String text = nonBlank(item);
                if (text != null)
                    result.add(text);
            }
        } else {
            String text = nonBlank(value);
            if (text != null)
                result.add(text);
        }
        return List.copyOf(result);
    }

    private static String nonBlank(Object value) {
        if (value == null)
            return null;
        String str = String.valueOf(value);
        return StringHelper.isEmpty(str) || str.isBlank() ? null : str;
    }

    private static Map<String, Object> asProps(Object value) {
        if (value instanceof DynamicObject dyn)
            return dyn.obj_propValues();
        if (value instanceof Map<?, ?> map)
            return (Map<String, Object>) map;
        return null;
    }
}
