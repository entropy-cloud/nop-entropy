package io.nop.lint.core.rule;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Typed, immutable carrier for a lint rule loaded from a {@code *.rule.yml}
 * (or {@code *.rule.json}) resource through the XDSL pipeline.
 *
 * <p>Carries the field set of {@code /nop/lint/schema/lint-rule.xdef}.
 * Instances are produced only by {@link RuleDslParser} after matcher
 * uniqueness validation, so a successfully created model always satisfies
 * the XOR constraint on the rule container and the nested matcher objects,
 * the atLeastOne constraint on {@code any} branches and {@code all}
 * elements, the stopBy/stopByRule/field pairing rules on relational
 * matchers, and the constraints validation matrix (one known constraint key
 * per element, required sub-fields, capture name shape, typeOf's
 * {@code requires: "L2"} gate — design 01 §3.2/§3.3, design 10 §2).
 */
public final class RuleDslModel {

    private final String id;
    private final String language;
    private final String severity;
    private final String message;
    private final Map<String, Matcher> utils;
    private final Matcher matcher;
    private final List<Constraint> constraints;
    private final String xscript;
    private final int xscriptTimeoutMs;
    private final Set<String> requires;
    private final Map<String, String> options;
    private final Map<String, String> settings;
    private final Metadata metadata;
    private final Files files;

    RuleDslModel(String id, String language, String severity, String message, Matcher matcher,
                 List<Constraint> constraints,
                 String xscript, int xscriptTimeoutMs, Set<String> requires,
                 Map<String, String> options, Map<String, String> settings,
                 Metadata metadata, Files files) {
        this(id, language, severity, message, Map.of(), matcher, constraints, xscript,
                xscriptTimeoutMs, requires, options, settings, metadata, files);
    }

    RuleDslModel(String id, String language, String severity, String message,
                 Map<String, Matcher> utils, Matcher matcher,
                 List<Constraint> constraints,
                 String xscript, int xscriptTimeoutMs, Set<String> requires,
                 Map<String, String> options, Map<String, String> settings,
                 Metadata metadata, Files files) {
        this.id = id;
        this.language = language;
        this.severity = severity;
        this.message = message;
        this.utils = utils.isEmpty() ? Map.of() : Map.copyOf(utils);
        this.matcher = matcher;
        this.constraints = List.copyOf(constraints);
        this.xscript = xscript;
        this.xscriptTimeoutMs = xscriptTimeoutMs;
        this.requires = requires;
        this.options = options;
        this.settings = settings;
        this.metadata = metadata;
        this.files = files;
    }

    public String getId() {
        return id;
    }

    public String getLanguage() {
        return language;
    }

    public String getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    /**
     * The single matcher declared by the rule container.
     */
    public Matcher getMatcher() {
        return matcher;
    }

    /**
     * The shared util rules of this rule file (roadmap item 24, design 01
     * §3.4): id → the util's single matcher, referenced by {@code matches}
     * matchers and {@code stopBy=rule} horizons; never null, empty when the
     * file declares no utils. The reference graph was validated cycle-free at
     * parse time.
     */
    public Map<String, Matcher> getUtils() {
        return utils;
    }

    /**
     * The per-match value constraints declared by the top-level
     * {@code constraints} field (design 01 §3.2); never null, empty when the
     * rule declares none. A match is reported only when every constraint
     * holds; capture references were validated against the declared name
     * shape at parse time and against the matcher's meta-var set at rule
     * compile time.
     */
    public List<Constraint> getConstraints() {
        return constraints;
    }

    /**
     * Raw xscript fragment text; compiled through the xscript engine's
     * compile-time whitelist when the rule compiles (roadmap item 14), or
     * null when the rule has no xscript body.
     */
    public String getXscript() {
        return xscript;
    }

    public int getXscriptTimeoutMs() {
        return xscriptTimeoutMs;
    }

    /**
     * Analyzer dependency levels (e.g. {@code L1}, {@code L2}); never null.
     */
    public Set<String> getRequires() {
        return requires;
    }

    /**
     * Rule options keyed by option name; never null.
     */
    public Map<String, String> getOptions() {
        return options;
    }

    /**
     * Shared settings keyed by name (injected by the ruleset layer); never null.
     */
    public Map<String, String> getSettings() {
        return settings;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public Files getFiles() {
        return files;
    }

    @Override
    public String toString() {
        return "RuleDslModel[" + id + "]";
    }

    /**
     * One per-match value constraint declared under the top-level
     * {@code constraints} field (design 01 §3.2/§3.3): exactly one
     * constraint kind per list element (enforced by the parser), carrying
     * only that kind's fields — the other slots stay null. Capture
     * references are normalized to bare names ({@code $A} and {@code A}
     * both mean the capture {@code A}); the shape was validated at parse
     * time. The {@code message} of {@code notExists} is a reserved field:
     * the v1 filter polarity never emits it (design 01 §3.2 Decision).
     */
    public static final class Constraint {
        private final String kind;
        private final List<String> captures;
        private final String capture;
        private final String pattern;
        private final List<String> values;
        private final String is;
        private final String message;
        private final Integer max;

        Constraint(String kind, List<String> captures, String capture, String pattern,
                   List<String> values, String is, String message, Integer max) {
            this.kind = kind;
            this.captures = captures == null ? null : List.copyOf(captures);
            this.capture = capture;
            this.pattern = pattern;
            this.values = values == null ? null : List.copyOf(values);
            this.is = is;
            this.message = message;
            this.max = max;
        }

        /**
         * The constraint kind: one of {@code sameText}, {@code differentText},
         * {@code regex}, {@code inList}, {@code typeOf}, {@code notExists},
         * {@code withinDepth}; never null.
         */
        public String getKind() {
            return kind;
        }

        /**
         * The normalized capture references of {@code sameText}/{@code
         * differentText} (at least two names); null on other kinds.
         */
        public List<String> getCaptures() {
            return captures;
        }

        /**
         * The single capture reference of {@code regex}/{@code inList}/
         * {@code typeOf}; null on other kinds.
         */
        public String getCapture() {
            return capture;
        }

        /**
         * The regex source of {@code regex}, or the inner pattern source of
         * {@code notExists}; null on other kinds.
         */
        public String getPattern() {
            return pattern;
        }

        /**
         * The allowed value set of {@code inList} (never empty); null on
         * other kinds.
         */
        public List<String> getValues() {
            return values;
        }

        /**
         * The required type name of {@code typeOf}; null on other kinds.
         */
        public String getIs() {
            return is;
        }

        /**
         * The reserved explanation message of {@code notExists}, or null.
         */
        public String getMessage() {
            return message;
        }

        /**
         * The depth cap of {@code withinDepth} (≥ 0); null on other kinds.
         */
        public Integer getMax() {
            return max;
        }

        @Override
        public String toString() {
            return "Constraint[" + kind + "]";
        }
    }

    /**
     * One branch of an {@code any} matcher: either a flat branch with at
     * least one of pattern/kind/regex present (several present fields form a
     * conjunction — ast-grep superset, backward compatible), or a nested
     * matcher object (roadmap item 24: any/all/not/matches/relational below
     * an {@code any} branch) carrying exactly one nested matcher. The two
     * forms are mutually exclusive on one branch.
     */
    public static final class Branch {
        private final String pattern;
        private final String kind;
        private final String regex;
        private final Matcher nested;

        Branch(String pattern, String kind, String regex) {
            this(pattern, kind, regex, null);
        }

        Branch(String pattern, String kind, String regex, Matcher nested) {
            this.pattern = pattern;
            this.kind = kind;
            this.regex = regex;
            this.nested = nested;
        }

        public String getPattern() {
            return pattern;
        }

        public String getKind() {
            return kind;
        }

        public String getRegex() {
            return regex;
        }

        /**
         * The nested matcher object of an object-form branch (roadmap item
         * 24); null on flat branches.
         */
        public Matcher getNested() {
            return nested;
        }
    }

    /**
     * The matcher declared by the rule container: exactly one of the
     * single-text matchers (pattern/kind/regex), an {@code any} matcher
     * with one or more branches, an {@code all}/{@code not} composite, or a
     * relational matcher ({@code inside}/{@code has}/{@code follows}/
     * {@code precedes}), or a {@code matches} reference to a shared util
     * rule — the full set the parser's XOR enforces. Composite members nest
     * recursively (roadmap item 24: any/all/not/matches are legal at every
     * matcher-object position); the reference graph over utils is
     * cycle-free by parse-time validation.
     */
    public static final class Matcher {
        private final String pattern;
        private final String kind;
        private final String regex;
        private final List<Branch> any;
        private final List<Matcher> all;
        private final Matcher not;
        private final String matches;
        private final Relational inside;
        private final Relational has;
        private final Relational follows;
        private final Relational precedes;

        Matcher(String pattern, String kind, String regex, List<Branch> any) {
            this(pattern, kind, regex, any, null, null, null, null, null, null, null);
        }

        Matcher(String pattern, String kind, String regex, List<Branch> any,
                List<Matcher> all, Matcher not,
                Relational inside, Relational has, Relational follows, Relational precedes) {
            this(pattern, kind, regex, any, all, not, null, inside, has, follows, precedes);
        }

        Matcher(String pattern, String kind, String regex, List<Branch> any,
                List<Matcher> all, Matcher not, String matches,
                Relational inside, Relational has, Relational follows, Relational precedes) {
            this.pattern = pattern;
            this.kind = kind;
            this.regex = regex;
            this.any = any;
            this.all = all;
            this.not = not;
            this.matches = matches;
            this.inside = inside;
            this.has = has;
            this.follows = follows;
            this.precedes = precedes;
        }

        public String getPattern() {
            return pattern;
        }

        public String getKind() {
            return kind;
        }

        public String getRegex() {
            return regex;
        }

        /**
         * Branches of an {@code any} matcher; null when the rule uses a
         * single-text matcher instead.
         */
        public List<Branch> getAny() {
            return any;
        }

        /**
         * Conjunctive elements of an {@code all} matcher; null when the rule
         * uses another matcher form.
         */
        public List<Matcher> getAll() {
            return all;
        }

        /**
         * The negated inner matcher of a {@code not} matcher; null when the
         * rule uses another matcher form.
         */
        public Matcher getNot() {
            return not;
        }

        /**
         * The referenced util id of a {@code matches} matcher (roadmap item
         * 24, design 04 §6); null on every other matcher form. The id was
         * validated against the rule's utils and the reference graph was
         * validated cycle-free at parse time.
         */
        public String getMatches() {
            return matches;
        }

        public Relational getInside() {
            return inside;
        }

        public Relational getHas() {
            return has;
        }

        public Relational getFollows() {
            return follows;
        }

        public Relational getPrecedes() {
            return precedes;
        }
    }

    /**
     * One relational matcher declaration: either a plain {@code pattern} or
     * the contextual pair {@code context} + {@code selector} (exactly one of
     * the two forms, enforced by the parser), the traversal horizon
     * ({@code neighbor|end|rule}, defaulted to {@code end} by the parser),
     * the util rule name required by the {@code rule} horizon, and the
     * optional child-field constraint ({@code inside}/{@code has} only).
     */
    public static final class Relational {
        private final String pattern;
        private final String stopBy;
        private final String stopByRule;
        private final String field;
        private final String context;
        private final String selector;

        Relational(String pattern, String stopBy, String stopByRule, String field,
                   String context, String selector) {
            this.pattern = pattern;
            this.stopBy = stopBy;
            this.stopByRule = stopByRule;
            this.field = field;
            this.context = context;
            this.selector = selector;
        }

        public String getPattern() {
            return pattern;
        }

        /**
         * The contextual pattern source, or null on the plain pattern form.
         */
        public String getContext() {
            return context;
        }

        /**
         * The node kind picked out of the contextual source, or null on the
         * plain pattern form.
         */
        public String getSelector() {
            return selector;
        }

        /**
         * The normalized horizon: {@code neighbor}, {@code end}, or
         * {@code rule}; never null.
         */
        public String getStopBy() {
            return stopBy;
        }

        /**
         * The util rule name for the {@code rule} horizon; null otherwise.
         */
        public String getStopByRule() {
            return stopByRule;
        }

        /**
         * The child-field constraint, or null when undeclared.
         */
        public String getField() {
            return field;
        }
    }

    /**
     * Rule metadata block ({@code metadata:} in the DSL).
     */
    public static final class Metadata {
        private final String category;
        private final String severity;
        private final boolean autoFixable;
        private final String version;
        private final List<String> source;

        Metadata(String category, String severity, boolean autoFixable, String version,
                 List<String> source) {
            this.category = category;
            this.severity = severity;
            this.autoFixable = autoFixable;
            this.version = version;
            this.source = source;
        }

        public String getCategory() {
            return category;
        }

        public String getSeverity() {
            return severity;
        }

        public boolean isAutoFixable() {
            return autoFixable;
        }

        public String getVersion() {
            return version;
        }

        /**
         * Origins of the rule (e.g. ported from eslint); never null.
         */
        public List<String> getSource() {
            return source;
        }
    }

    /**
     * File scope globs ({@code files:} in the DSL).
     */
    public static final class Files {
        private final List<String> include;
        private final List<String> exclude;

        Files(List<String> include, List<String> exclude) {
            this.include = include;
            this.exclude = exclude;
        }

        /**
         * Include globs; never null, empty means include everything.
         */
        public List<String> getInclude() {
            return include;
        }

        /**
         * Exclude globs; never null, empty means exclude nothing.
         */
        public List<String> getExclude() {
            return exclude;
        }
    }
}
