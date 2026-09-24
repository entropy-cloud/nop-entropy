package io.nop.lint.core.cli;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.LintProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The {@code nop-lint check} parameter surface (design 03 §2.4 增注,
 * 2026-09-22; autofix switches per roadmap item 25; completion per roadmap
 * item 39): the subcommand {@code check}, one or more target paths (file or
 * directory), the optional {@code --profile fast|standard|deep} switch
 * (default {@code standard}, the CI mode), the optional {@code --fix} /
 * {@code --fix-dry-run} pair — mutually exclusive, both given is a parse
 * error — and the item-39 surface: {@code --format
 * console|sarif|checkstyle-xml|json|junit-xml} (default console), {@code
 * --max-warnings N} (more than N warning-severity diagnostics force exit 1)
 * and {@code --rules <id,...>} (an explicit whitelist narrowing; unknown ids
 * fail the run, and a language whose rules are all filtered out no longer
 * triggers the unbound-language check — the R1 3.2 adjudication).
 *
 * <p>Parsing is fail-closed: an unknown subcommand, an unknown option, a
 * missing {@code --profile} value, an unknown profile name, a blank target,
 * a missing target path, the {@code --fix}/{@code --fix-dry-run}
 * combination, an unknown format, a non-numeric or negative
 * {@code --max-warnings}, or a blank {@code --rules} id all throw {@link
 * NopLintException}; the CLI entry point maps that to exit code 2 with the
 * usage line.</p>
 */
public record CliOptions(List<String> targets, LintProfile profile, FixMode fixMode,
                         BaselineOp baselineOp, String baselineFile, OutputFormat format,
                         Integer maxWarnings, List<String> rules, String cacheFile) {

    /**
     * The report format (roadmap item 39): console is the human face, the
     * four machine formats render the diagnostic stream only.
     */
    public enum OutputFormat {
        CONSOLE, SARIF, CHECKSTYLE_XML, JSON, JUNIT_XML
    }


    /**
     * The fix flow the run drives: {@code NONE} reports only, {@code APPLY}
     * writes fixed content back to the files, {@code DRY_RUN} computes the
     * full fix loop in memory and prints the unified diff instead.
     */
    public enum FixMode {
        NONE, APPLY, DRY_RUN
    }

    /**
     * The baseline flow the run drives (roadmap item 27, design 09 §5):
     * {@code NONE} ignores baselines, {@code APPLY} suppresses
     * baseline-matched diagnostics (local/report dedup), {@code CHECK} is
     * the CI tightening mode (stale entries force exit code 1 — the "基线
     * 只减不增" enforcement), {@code WRITE} generates a baseline from the
     * run's residual diagnostics.
     */
    public enum BaselineOp {
        NONE, APPLY, CHECK, WRITE
    }

    private static final String USAGE =
            "usage: nop-lint check <path>... [--profile fast|standard|deep] [--fix|--fix-dry-run]"
                    + " [--baseline <file>|--baseline-check <file>|--write-baseline <file>]"
                    + " [--format console|sarif|checkstyle-xml|json|junit-xml]"
                    + " [--max-warnings <n>] [--rules <id,id,...>]";

    /**
     * The usage line carried by every parse error message.
     */
    public static String usage() {
        return USAGE;
    }

    public CliOptions {
        targets = List.copyOf(targets);
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /**
     * The report-only surface (no fix flow, no baseline) — the pre-autofix
     * shape.
     */
    public CliOptions(List<String> targets, LintProfile profile) {
        this(targets, profile, FixMode.NONE, BaselineOp.NONE, null);
    }

    /**
     * The fix-flow surface without a baseline (the pre-item-27 shape).
     */
    public CliOptions(List<String> targets, LintProfile profile, FixMode fixMode) {
        this(targets, profile, fixMode, BaselineOp.NONE, null);
    }

    /**
     * The pre-item-39 full shape: console format, no warning gate, no rule
     * filter.
     */
    public CliOptions(List<String> targets, LintProfile profile, FixMode fixMode,
                      BaselineOp baselineOp, String baselineFile) {
        this(targets, profile, fixMode, baselineOp, baselineFile,
                OutputFormat.CONSOLE, null, List.of(), null);
    }

    /**
     * Parses {@code nop-lint} arguments (the subcommand and everything
     * after it).
     *
     * @throws NopLintException on any malformed or unsupported input
     */
    public static CliOptions parse(String... args) {
        if (args == null || args.length == 0)
            throw new NopLintException(USAGE + " (missing subcommand; expected 'check')");

        if (!"check".equals(args[0]))
            throw new NopLintException(USAGE + " (unknown subcommand '" + args[0]
                    + "'; v1 supports only 'check')");

        List<String> targets = new ArrayList<>();
        LintProfile profile = LintProfile.STANDARD;
        FixMode fixMode = FixMode.NONE;
        BaselineOp baselineOp = BaselineOp.NONE;
        String baselineFile = null;
        OutputFormat format = OutputFormat.CONSOLE;
        Integer maxWarnings = null;
        List<String> rules = List.of();
        String cacheFile = null;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("--profile".equals(arg)) {
                profile = parseProfile(args, ++i);
            } else if ("--format".equals(arg)) {
                format = parseFormat(args, ++i);
            } else if ("--max-warnings".equals(arg)) {
                maxWarnings = parseMaxWarnings(args, ++i);
            } else if ("--rules".equals(arg)) {
                rules = parseRules(args, ++i);
            } else if ("--cache".equals(arg)) {
                cacheFile = parseCacheFile(args, ++i);
            } else if ("--fix".equals(arg)) {
                fixMode = withFixMode(fixMode, FixMode.APPLY, "--fix");
            } else if ("--fix-dry-run".equals(arg)) {
                fixMode = withFixMode(fixMode, FixMode.DRY_RUN, "--fix-dry-run");
            } else if ("--baseline".equals(arg)) {
                String file = parseBaselineFile(args, ++i, "--baseline");
                BaselineOp next = withBaselineOp(baselineOp, BaselineOp.APPLY);
                baselineOp = next;
                baselineFile = file;
            } else if ("--baseline-check".equals(arg)) {
                String file = parseBaselineFile(args, ++i, "--baseline-check");
                baselineOp = withBaselineOp(baselineOp, BaselineOp.CHECK);
                baselineFile = file;
            } else if ("--write-baseline".equals(arg)) {
                String file = parseBaselineFile(args, ++i, "--write-baseline");
                baselineOp = withBaselineOp(baselineOp, BaselineOp.WRITE);
                baselineFile = file;
            } else if (arg.startsWith("-")) {
                throw new NopLintException(USAGE + " (unknown option '" + arg + "'; supported:"
                        + " --profile fast|standard|deep, --fix|--fix-dry-run, the --baseline family,"
                        + " --format, --max-warnings, --rules)");
            } else {
                if (arg.isBlank())
                    throw new NopLintException(USAGE + " (target path must not be blank)");
                targets.add(arg);
            }
        }

        if (targets.isEmpty())
            throw new NopLintException(USAGE + " (missing target path: at least one file or "
                    + "directory to check is required)");

        if (baselineOp == BaselineOp.WRITE && fixMode != FixMode.NONE)
            throw new NopLintException(USAGE + " (--write-baseline cannot combine with --fix or "
                    + "--fix-dry-run: the generated baseline must describe either the pre-fix or "
                    + "the post-fix residual, and the run does not guess)");

        if (cacheFile != null && baselineOp != BaselineOp.NONE)
            throw new NopLintException(USAGE + " (--cache cannot combine with the --baseline"
                    + " family: replayed diagnostics cannot drive a baseline flow)");
        if (cacheFile != null && fixMode != FixMode.NONE)
            throw new NopLintException(USAGE + " (--cache cannot combine with --fix or"
                    + " --fix-dry-run: replayed diagnostics cannot drive a fix multipass)");

        return new CliOptions(List.copyOf(targets), profile, fixMode, baselineOp, baselineFile,
                format, maxWarnings, rules, cacheFile);
    }

    private static String parseCacheFile(String[] args, int valueIndex) {
        if (valueIndex >= args.length)
            throw new NopLintException(USAGE + " (--cache requires a cache file path)");
        String value = args[valueIndex];
        if (value.isBlank())
            throw new NopLintException(USAGE + " (--cache requires a non-blank cache file path)");
        return value;
    }

    /**
     * The format values are the five fixed faces; the misspelled format is a
     * parse error (exit 2), never a silent console fallback.
     */
    private static OutputFormat parseFormat(String[] args, int valueIndex) {
        if (valueIndex >= args.length)
            throw new NopLintException(USAGE + " (--format requires a value:"
                    + " console|sarif|checkstyle-xml|json|junit-xml)");
        String value = args[valueIndex];
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "console" -> OutputFormat.CONSOLE;
            case "sarif" -> OutputFormat.SARIF;
            case "checkstyle-xml" -> OutputFormat.CHECKSTYLE_XML;
            case "json" -> OutputFormat.JSON;
            case "junit-xml" -> OutputFormat.JUNIT_XML;
            default -> throw new NopLintException(USAGE + " (unknown format '" + value
                    + "'; expected console|sarif|checkstyle-xml|json|junit-xml)");
        };
    }

    /**
     * The warning gate: more warning-severity diagnostics than N force exit
     * 1 (the error semantics are unchanged). Zero is legal (any warning
     * fails); negatives are a mistyped value.
     */
    private static Integer parseMaxWarnings(String[] args, int valueIndex) {
        if (valueIndex >= args.length)
            throw new NopLintException(USAGE + " (--max-warnings requires a non-negative count)");
        String value = args[valueIndex];
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 0)
                throw new NumberFormatException("negative");
            return parsed;
        } catch (NumberFormatException e) {
            throw new NopLintException(USAGE + " (--max-warnings requires a non-negative integer,"
                    + " got '" + value + "')");
        }
    }

    /**
     * The explicit rule narrowing: comma-separated rule ids, each non-blank;
     * a blank element is a mistyped list, not an empty filter. Unknown ids
     * fail at load time (the CheckRunner sees the full loaded id set) — the
     * parse here only enforces the shape.
     */
    private static List<String> parseRules(String[] args, int valueIndex) {
        if (valueIndex >= args.length)
            throw new NopLintException(USAGE + " (--rules requires a comma-separated id list)");
        String value = args[valueIndex];
        if (value.isBlank())
            throw new NopLintException(USAGE + " (--rules requires a comma-separated id list)");
        List<String> ids = new ArrayList<>();
        for (String id : value.split(",")) {
            String trimmed = id.trim();
            if (trimmed.isEmpty())
                throw new NopLintException(USAGE + " (--rules contains a blank id in '" + value
                        + "')");
            ids.add(trimmed);
        }
        return ids;
    }

    /**
     * Rejects the second fix switch: {@code --fix} and {@code --fix-dry-run}
     * name different runs (write-back vs diff-only) and a silent merge of
     * the two would surprise the caller.
     */
    private static FixMode withFixMode(FixMode current, FixMode requested, String flag) {
        if (current != FixMode.NONE && current != requested) {
            throw new NopLintException(USAGE + " (--fix and --fix-dry-run are mutually exclusive)");
        }
        return requested;
    }

    /**
     * Rejects a second baseline switch: the three baseline ops name three
     * different runs (suppress / tighten / generate).
     */
    private static BaselineOp withBaselineOp(BaselineOp current, BaselineOp requested) {
        if (current != BaselineOp.NONE && current != requested) {
            throw new NopLintException(USAGE + " (--baseline, --baseline-check and"
                    + " --write-baseline are mutually exclusive)");
        }
        return requested;
    }

    private static String parseBaselineFile(String[] args, int valueIndex, String flag) {
        if (valueIndex >= args.length)
            throw new NopLintException(USAGE + " (" + flag + " requires a baseline file path)");
        String value = args[valueIndex];
        if (value.isBlank())
            throw new NopLintException(USAGE + " (" + flag + " requires a non-blank baseline file"
                    + " path)");
        return value;
    }

    private static LintProfile parseProfile(String[] args, int valueIndex) {
        if (valueIndex >= args.length)
            throw new NopLintException(USAGE + " (--profile requires a value: fast|standard|deep)");
        String value = args[valueIndex];
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("fast".equals(normalized))
            return LintProfile.FAST;
        if ("standard".equals(normalized))
            return LintProfile.STANDARD;
        if ("deep".equals(normalized))
            return LintProfile.DEEP;
        throw new NopLintException(USAGE + " (unknown profile '" + value
                + "'; expected fast|standard|deep)");
    }
}
