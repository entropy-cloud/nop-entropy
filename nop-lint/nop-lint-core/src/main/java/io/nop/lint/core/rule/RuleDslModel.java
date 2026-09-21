package io.nop.lint.core.rule;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Typed, immutable carrier for a lint rule loaded from a {@code *.rule.yml}
 * (or {@code *.rule.json}) resource through the XDSL pipeline.
 *
 * <p>Carries the full Phase 1 field set of {@code /nop/lint/schema/lint-rule.xdef}.
 * Instances are produced only by {@link RuleDslParser} after matcher uniqueness
 * validation, so a successfully created model always satisfies the XOR
 * constraint on the rule container and the atLeastOne constraint on any
 * branches.
 */
public final class RuleDslModel {

    private final String id;
    private final String language;
    private final String severity;
    private final String message;
    private final Matcher matcher;
    private final String xscript;
    private final int xscriptTimeoutMs;
    private final Set<String> requires;
    private final Map<String, String> options;
    private final Map<String, String> settings;
    private final Metadata metadata;
    private final Files files;

    RuleDslModel(String id, String language, String severity, String message, Matcher matcher,
                 String xscript, int xscriptTimeoutMs, Set<String> requires,
                 Map<String, String> options, Map<String, String> settings,
                 Metadata metadata, Files files) {
        this.id = id;
        this.language = language;
        this.severity = severity;
        this.message = message;
        this.matcher = matcher;
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
     * Raw xpl fragment text; compilation to an executable action belongs to
     * the later RuleCompiler stage (roadmap item 14).
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
     * One branch of an {@code any} matcher: at least one of pattern/kind/regex
     * is present; several present fields form a conjunction (ast-grep superset).
     */
    public static final class Branch {
        private final String pattern;
        private final String kind;
        private final String regex;

        Branch(String pattern, String kind, String regex) {
            this.pattern = pattern;
            this.kind = kind;
            this.regex = regex;
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
    }

    /**
     * The matcher declared by the rule container: exactly one of the
     * single-text matchers (pattern/kind/regex) or an {@code any} matcher
     * with one or more branches.
     */
    public static final class Matcher {
        private final String pattern;
        private final String kind;
        private final String regex;
        private final List<Branch> any;

        Matcher(String pattern, String kind, String regex, List<Branch> any) {
            this.pattern = pattern;
            this.kind = kind;
            this.regex = regex;
            this.any = any;
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
