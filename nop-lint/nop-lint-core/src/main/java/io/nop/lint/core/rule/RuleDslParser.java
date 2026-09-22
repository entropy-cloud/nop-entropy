package io.nop.lint.core.rule;

import io.nop.commons.util.StringHelper;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.lint.core.NopLintException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses the dynamic model produced by the XDSL pipeline (registered via
 * {@code lint.register-model.xml} for fileType {@code rule.yml}) into an
 * immutable {@link RuleDslModel}, enforcing matcher uniqueness fail-closed:
 *
 * <ul>
 *   <li>the rule container declares exactly one matcher among
 *       {@code pattern|kind|regex|any|all|not|inside|has|follows|precedes}
 *       (XOR)</li>
 *   <li>each {@code any} branch declares at least one matcher among
 *       {@code pattern|kind|regex}; several fields on one branch form a
 *       conjunction (ast-grep superset, backward compatible)</li>
 *   <li>each {@code all} element and each {@code not} inner declares exactly
 *       one matcher among {@code pattern|kind|regex|not|inside|has|follows|
 *       precedes} / {@code pattern|kind|regex|inside|has|follows|precedes};
 *       {@code any}/{@code all} below the container and {@code not} below an
 *       {@code all} element are rejected as out of the supported surface
 *       (roadmap item 24 / bounded nesting, fail-closed)</li>
 *   <li>every relational matcher ({@code inside}/{@code has}/{@code follows}/
 *       {@code precedes}) declares exactly one pattern form — a non-blank
 *       {@code pattern}, or the contextual {@code context}+{@code selector}
 *       pair (design 01 §1; both or neither are rejected); {@code stopBy}
 *       is one of {@code neighbor|end|rule} (default {@code end});
 *       {@code stopBy=rule} requires a non-blank {@code stopByRule} and vice
 *       versa; {@code field} is only legal on {@code inside}/{@code has}</li>
 * </ul>
 *
 * <p>The {@code xdef:check-mutex} declarations in {@code lint-rule.xdef}
 * record the same intent declaratively; this class is the runtime authority
 * (see design doc ai-dev/design/nop-lint/10-xdef-metamodel.md section 2).
 * The FQCN of this class is referenced by the {@code xdef:parser-class}
 * attribute of the metamodel.</p>
 */
public final class RuleDslParser {

    /**
     * VFS path of the rule DSL metamodel.
     */
    public static final String RULE_XDEF_PATH = "/nop/lint/schema/lint-rule.xdef";

    /**
     * The per-match xscript budget applied when a rule declares no
     * {@code xscriptTimeoutMs} (design 07 §3).
     */
    public static final int DEFAULT_XSCRIPT_TIMEOUT_MS = 100;

    /**
     * The maximum per-match xscript budget a rule may declare (design 07 §3);
     * larger values are rejected at parse time, fail-closed.
     */
    public static final int MAX_XSCRIPT_TIMEOUT_MS = 1000;

    private static final String[] RULE_MATCHERS =
            {"pattern", "kind", "regex", "any", "all", "not", "inside", "has", "follows", "precedes"};
    private static final String[] NESTED_MATCHERS =
            {"pattern", "kind", "regex", "not", "inside", "has", "follows", "precedes"};
    private static final String[] NOT_INNER_MATCHERS =
            {"pattern", "kind", "regex", "inside", "has", "follows", "precedes"};
    private static final String[] BRANCH_MATCHERS = {"pattern", "kind", "regex"};
    private static final Set<String> STOP_BY_VALUES = Set.of("neighbor", "end", "rule");

    /**
     * Loads a rule model from a VFS resource path through the registered
     * component loader (xdef structural validation + x:extends delta merge)
     * and parses it into a typed {@link RuleDslModel} in a single call.
     *
     * @param resourcePath VFS path of a {@code *.rule.yml} / {@code *.rule.json} resource
     * @return the validated, typed rule model
     * @throws NopLintException when the model cannot be loaded, violates the
     *                          metamodel, or violates matcher uniqueness
     */
    public RuleDslModel loadRuleModel(String resourcePath) {
        Object model;
        try {
            model = ResourceComponentManager.instance().loadComponentModel(resourcePath);
        } catch (Exception e) {
            throw new NopLintException("Failed to load lint rule model from resource '" + resourcePath
                    + "': " + e.getMessage(), e);
        }
        return parseRuleModel(model);
    }

    /**
     * Parses the dynamic model returned by the XDSL pipeline into a typed
     * {@link RuleDslModel} and enforces matcher uniqueness fail-closed.
     *
     * @param model dynamic model object (DynamicObject produced by DslModelParser)
     * @return the typed, immutable rule model
     * @throws NopLintException when the model shape is unexpected or matcher
     *                          uniqueness is violated; no validation failure is
     *                          silently dropped
     */
    public RuleDslModel parseRuleModel(Object model) {
        if (!(model instanceof DynamicObject dyn))
            throw new NopLintException("Unsupported lint rule model type: "
                    + (model == null ? "null" : model.getClass().getName())
                    + " (expected the DynamicObject produced by the XDSL pipeline)");

        String id = text(dyn, "id");
        if (StringHelper.isEmpty(id))
            throw new NopLintException("Lint rule model is missing a non-empty 'id'");

        Map<String, Object> rule = objectProps(dyn, "rule");
        RuleDslModel.Matcher matcher = parseMatcher(id, rule);

        return new RuleDslModel(id,
                text(dyn, "language"),
                text(dyn, "severity"),
                text(dyn, "message"),
                matcher,
                text(dyn, "xscript"),
                timeoutMs(id, dyn),
                csvSet(dyn, "requires"),
                optionMap(id, dyn, "options"),
                optionMap(id, dyn, "settings"),
                parseMetadata(id, dyn),
                parseFiles(dyn));
    }

    private RuleDslModel.Matcher parseMatcher(String id, Map<String, Object> rule) {
        if (rule == null)
            throw new NopLintException("Rule '" + id + "' has no rule container (exactly one of "
                    + String.join("|", RULE_MATCHERS) + " is required)");

        List<String> present = presentMatchers(rule, RULE_MATCHERS);
        if (present.isEmpty())
            throw new NopLintException("Rule '" + id + "' declares no matcher in its rule container "
                    + "(exactly one of " + String.join("|", RULE_MATCHERS) + " is required)");
        if (present.size() > 1)
            throw new NopLintException("Rule '" + id + "' declares multiple matchers in its rule "
                    + "container: " + StringHelper.join(present, ", ") + " (exactly one of "
                    + String.join("|", RULE_MATCHERS) + " is allowed)");

        return buildMatcher(id, present.get(0), rule, "'rule' container");
    }

    /**
     * Builds one matcher object from its XOR-selected form. Used at the
     * container level and for nested composite members.
     */
    private RuleDslModel.Matcher buildMatcher(String id, String only, Map<String, Object> props,
                                              String location) {
        switch (only) {
            case "pattern":
            case "kind":
            case "regex":
                return textOnlyMatcher(only, text(props.get(only)));
            case "any":
                return new RuleDslModel.Matcher(null, null, null, parseAnyBranches(id, props.get("any")));
            case "all":
                return new RuleDslModel.Matcher(null, null, null, null,
                        parseAllElements(id, props.get("all")), null, null, null, null, null);
            case "not":
                return new RuleDslModel.Matcher(null, null, null, null, null,
                        parseNotInner(id, props.get("not"), location + " 'not'"), null, null, null, null);
            case "inside":
            case "has":
            case "follows":
            case "precedes":
                RuleDslModel.Relational relational =
                        parseRelational(id, only, asProps(props.get(only)), location);
                return relationalMatcher(only, relational);
            default:
                throw new NopLintException("Rule '" + id + "' declares unknown matcher '" + only
                        + "' in " + location + " (fail-closed)");
        }
    }

    private RuleDslModel.Matcher textOnlyMatcher(String name, String value) {
        return switch (name) {
            case "pattern" -> new RuleDslModel.Matcher(value, null, null, null);
            case "kind" -> new RuleDslModel.Matcher(null, value, null, null);
            default -> new RuleDslModel.Matcher(null, null, value, null);
        };
    }

    private RuleDslModel.Matcher relationalMatcher(String name, RuleDslModel.Relational relational) {
        return switch (name) {
            case "inside" -> new RuleDslModel.Matcher(null, null, null, null, null, null,
                    relational, null, null, null);
            case "has" -> new RuleDslModel.Matcher(null, null, null, null, null, null,
                    null, relational, null, null);
            case "follows" -> new RuleDslModel.Matcher(null, null, null, null, null, null,
                    null, null, relational, null);
            default -> new RuleDslModel.Matcher(null, null, null, null, null, null,
                    null, null, null, relational);
        };
    }

    private List<RuleDslModel.Matcher> parseAllElements(String id, Object allValue) {
        if (!(allValue instanceof Collection<?> items) || items.isEmpty())
            throw new NopLintException("Rule '" + id + "' declares an 'all' matcher without elements "
                    + "(at least one conjunct is required)");

        List<RuleDslModel.Matcher> elements = new ArrayList<>(items.size());
        int index = 0;
        for (Object item : items) {
            index++;
            Map<String, Object> props = asProps(item);
            if (props == null)
                throw new NopLintException("Rule '" + id + "' declares a non-object 'all' element #"
                        + index + " (each conjunct must be a matcher object)");
            if (props.containsKey("any"))
                throw new NopLintException("Rule '" + id + "' declares 'any' inside 'all' element #"
                        + index + " (any-nesting refinement is roadmap item 24; fail-closed)");
            if (props.containsKey("all"))
                throw new NopLintException("Rule '" + id + "' declares 'all' inside 'all' element #"
                        + index + " (nested composites beyond all-element 'not' are out of the "
                        + "supported surface; fail-closed)");

            List<String> present = presentMatchers(props, NESTED_MATCHERS);
            if (present.isEmpty())
                throw new NopLintException("Rule '" + id + "' declares no matcher in 'all' element #"
                        + index + " (exactly one of " + String.join("|", NESTED_MATCHERS) + " is required)");
            if (present.size() > 1)
                throw new NopLintException("Rule '" + id + "' declares multiple matchers in 'all' element #"
                        + index + ": " + StringHelper.join(present, ", ") + " (exactly one of "
                        + String.join("|", NESTED_MATCHERS) + " is allowed)");

            elements.add(buildMatcher(id, present.get(0), props, "'all' element #" + index));
        }
        return elements;
    }

    private RuleDslModel.Matcher parseNotInner(String id, Object notValue, String location) {
        Map<String, Object> props = asProps(notValue);
        if (props == null)
            throw new NopLintException("Rule '" + id + "' declares " + location + " without a matcher "
                    + "object (exactly one of " + String.join("|", NOT_INNER_MATCHERS) + " is required)");
        if (props.containsKey("any"))
            throw new NopLintException("Rule '" + id + "' declares 'any' inside " + location
                    + " (any-nesting refinement is roadmap item 24; fail-closed)");
        if (props.containsKey("all"))
            throw new NopLintException("Rule '" + id + "' declares 'all' inside " + location
                    + " (nested composites below 'not' are out of the supported surface; fail-closed)");
        if (props.containsKey("not"))
            throw new NopLintException("Rule '" + id + "' declares 'not' inside " + location
                    + " (negation nesting beyond an all-element 'not' is out of the supported "
                    + "surface; fail-closed)");

        List<String> present = presentMatchers(props, NOT_INNER_MATCHERS);
        if (present.isEmpty())
            throw new NopLintException("Rule '" + id + "' declares no matcher in " + location
                    + " (exactly one of " + String.join("|", NOT_INNER_MATCHERS) + " is required)");
        if (present.size() > 1)
            throw new NopLintException("Rule '" + id + "' declares multiple matchers in " + location
                    + ": " + StringHelper.join(present, ", ") + " (exactly one of "
                    + String.join("|", NOT_INNER_MATCHERS) + " is allowed)");

        String only = present.get(0);
        if ("inside".equals(only) || "has".equals(only) || "follows".equals(only) || "precedes".equals(only)) {
            RuleDslModel.Relational relational =
                    parseRelational(id, only, asProps(props.get(only)), location);
            return relationalMatcher(only, relational);
        }
        return textOnlyMatcher(only, text(props.get(only)));
    }

    private RuleDslModel.Relational parseRelational(String id, String name, Map<String, Object> props,
                                                    String location) {
        if (props == null)
            throw new NopLintException("Rule '" + id + "' declares " + location + " '" + name
                    + "' without an object value (a pattern, or the context+selector pair, is required)");

        String pattern = text(props.get("pattern"));
        String context = text(props.get("context"));
        String selector = text(props.get("selector"));

        // Exactly one pattern form (fail-closed): the plain snippet or the
        // contextual source + selector pair (design 01 §1 contextual patterns
        // — the disambiguation for shapes a bare snippet parses as the wrong
        // kind, e.g. Java `A.B` reading as scoped_type_identifier).
        if (!StringHelper.isEmpty(pattern)) {
            if (!StringHelper.isEmpty(context) || !StringHelper.isEmpty(selector))
                throw new NopLintException("Rule '" + id + "' declares " + location + " '" + name
                        + "' with both 'pattern' and 'context'/'selector' (the plain and contextual "
                        + "pattern forms are exclusive; fail-closed)");
        } else {
            if (StringHelper.isEmpty(context) && StringHelper.isEmpty(selector))
                throw new NopLintException("Rule '" + id + "' declares " + location + " '" + name
                        + "' without a pattern (declare 'pattern', or the 'context'+'selector' pair)");
            if (StringHelper.isEmpty(context))
                throw new NopLintException("Rule '" + id + "' declares " + location + " '" + name
                        + "' with 'selector' but no 'context' (the contextual form requires both; "
                        + "fail-closed)");
            if (StringHelper.isEmpty(selector))
                throw new NopLintException("Rule '" + id + "' declares " + location + " '" + name
                        + "' with 'context' but no 'selector' (the contextual form requires both; "
                        + "fail-closed)");
        }

        String stopBy = text(props.get("stopBy"));
        if (StringHelper.isEmpty(stopBy))
            stopBy = "end";
        if (!STOP_BY_VALUES.contains(stopBy))
            throw new NopLintException("Rule '" + id + "' declares '" + name + "' with stopBy '" + stopBy
                    + "' (allowed: neighbor|end|rule)");

        String stopByRule = text(props.get("stopByRule"));
        if ("rule".equals(stopBy) && StringHelper.isEmpty(stopByRule))
            throw new NopLintException("Rule '" + id + "' declares '" + name + "' with stopBy=rule "
                    + "but no 'stopByRule' (the util rule name is mandatory at the rule horizon; "
                    + "fail-closed)");
        if (!"rule".equals(stopBy) && !StringHelper.isEmpty(stopByRule))
            throw new NopLintException("Rule '" + id + "' declares '" + name + "' with 'stopByRule' "
                    + "but stopBy=" + stopBy + " (stopByRule is only legal at the rule horizon; "
                    + "fail-closed)");

        String field = text(props.get("field"));
        if (!StringHelper.isEmpty(field) && ("follows".equals(name) || "precedes".equals(name)))
            throw new NopLintException("Rule '" + id + "' declares 'field' on '" + name
                    + "' (field is only supported on inside/has; sibling relations have no field slot)");

        return new RuleDslModel.Relational(pattern, stopBy, stopByRule, field, context, selector);
    }

    private List<RuleDslModel.Branch> parseAnyBranches(String id, Object anyValue) {
        if (!(anyValue instanceof Collection<?> items) || items.isEmpty())
            throw new NopLintException("Rule '" + id + "' declares an 'any' matcher without branches "
                    + "(at least one branch with a matcher is required)");

        List<RuleDslModel.Branch> branches = new ArrayList<>(items.size());
        int index = 0;
        for (Object item : items) {
            index++;
            Map<String, Object> branchProps = asProps(item);
            List<String> present = presentMatchers(branchProps, BRANCH_MATCHERS);
            if (present.isEmpty())
                throw new NopLintException("Rule '" + id + "' declares no matcher in 'any' branch #" + index
                        + " (at least one of " + String.join("|", BRANCH_MATCHERS) + " is required)");
            branches.add(new RuleDslModel.Branch(
                    nonBlankOrNullableText(branchProps.get("pattern")),
                    nonBlankOrNullableText(branchProps.get("kind")),
                    nonBlankOrNullableText(branchProps.get("regex"))));
        }
        return branches;
    }

    private RuleDslModel.Metadata parseMetadata(String id, DynamicObject dyn) {
        Map<String, Object> metadata = objectProps(dyn, "metadata");
        if (metadata == null)
            return null;

        String category = text(metadata.get("category"));
        if (StringHelper.isEmpty(category))
            throw new NopLintException("Rule '" + id + "' declares metadata without a non-empty 'category'");

        Collection<?> source = (Collection<?>) metadata.get("source");
        List<String> sources = new ArrayList<>();
        if (source != null) {
            for (Object item : source)
                sources.add(String.valueOf(item));
        }

        return new RuleDslModel.Metadata(category,
                text(metadata.get("severity")),
                Boolean.TRUE.equals(metadata.get("autoFixable")),
                text(metadata.get("version")),
                sources);
    }

    private RuleDslModel.Files parseFiles(DynamicObject dyn) {
        Map<String, Object> files = objectProps(dyn, "files");
        if (files == null)
            return null;

        return new RuleDslModel.Files(csvToList(files.get("include")), csvToList(files.get("exclude")));
    }

    private Map<String, String> optionMap(String id, DynamicObject dyn, String name) {
        Map<String, Object> entries = objectProps(dyn, name);
        if (entries == null)
            return Map.of();

        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : entries.entrySet()) {
            Map<String, Object> props = asProps(entry.getValue());
            Object value = props == null ? null : props.get("value");
            if (value == null)
                throw new NopLintException("Rule '" + id + "' declares option '" + entry.getKey()
                        + "' without a 'value'");
            result.put(entry.getKey(), String.valueOf(value));
        }
        return result;
    }

    private Set<String> csvSet(DynamicObject dyn, String name) {
        Object value = dyn.obj_propValues().get(name);
        Set<String> result = new LinkedHashSet<>();
        if (value instanceof Collection<?> items) {
            for (Object item : items)
                result.add(String.valueOf(item));
        } else if (value != null) {
            result.add(String.valueOf(value));
        }
        return result;
    }

    private List<String> csvToList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Collection<?> items) {
            for (Object item : items)
                result.add(String.valueOf(item));
        } else if (value != null) {
            result.add(String.valueOf(value));
        }
        return result;
    }

    private List<String> presentMatchers(Map<String, Object> props, String[] names) {
        List<String> present = new ArrayList<>();
        if (props == null) {
            return present;
        }
        for (String name : names) {
            Object value = props.get(name);
            if (name.equals("any")) {
                if (value != null)
                    present.add(name);
            } else if (value instanceof String str) {
                if (!str.isBlank())
                    present.add(name);
            } else if (value != null) {
                present.add(name);
            }
        }
        return present;
    }

    private Map<String, Object> objectProps(DynamicObject dyn, String name) {
        Object value = dyn.obj_propValues().get(name);
        return asProps(value);
    }

    private Map<String, Object> asProps(Object value) {
        if (value instanceof DynamicObject dyn)
            return dyn.obj_propValues();
        if (value instanceof Map<?, ?> map)
            return (Map<String, Object>) map;
        return null;
    }

    private String text(DynamicObject dyn, String name) {
        return nonBlankOrNullableText(dyn.obj_propValues().get(name));
    }

    private String text(Object value) {
        return nonBlankOrNullableText(value);
    }

    private String nonBlankOrNullableText(Object value) {
        if (value == null)
            return null;
        String str = String.valueOf(value);
        return str.isBlank() ? null : str;
    }

    /**
     * The rule's per-match xscript budget: the declared
     * {@code xscriptTimeoutMs}, defaulted when absent, and validated
     * fail-closed — a non-numeric, non-positive, or over-cap value rejects
     * the model instead of degrading to a guess (design 07 §3: cap 1000ms).
     */
    private int timeoutMs(String id, DynamicObject dyn) {
        Object value = dyn.obj_propValues().get("xscriptTimeoutMs");
        if (value == null)
            return DEFAULT_XSCRIPT_TIMEOUT_MS;
        if (!(value instanceof Number number))
            throw new NopLintException("Rule '" + id + "' has a non-numeric xscriptTimeoutMs value: " + value);
        int ms = number.intValue();
        if (ms <= 0)
            throw new NopLintException("Rule '" + id + "' has a non-positive xscriptTimeoutMs: " + ms
                    + " (a budget below 1ms can never run; fail-closed)");
        if (ms > MAX_XSCRIPT_TIMEOUT_MS)
            throw new NopLintException("Rule '" + id + "' has xscriptTimeoutMs " + ms
                    + " which exceeds the " + MAX_XSCRIPT_TIMEOUT_MS + "ms cap (design 07 §3; fail-closed)");
        return ms;
    }
}
