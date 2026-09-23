package io.nop.lint.core.cli;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.LintProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The {@code nop-lint check} parameter surface (design 03 §2.4 增注,
 * 2026-09-22; autofix switches per roadmap item 25): the subcommand
 * {@code check}, one or more target paths (file or directory), the optional
 * {@code --profile fast|standard} switch (default {@code standard}, the CI
 * mode), and the optional {@code --fix} / {@code --fix-dry-run} pair —
 * mutually exclusive, both given is a parse error. The fuller surface —
 * {@code --max-warnings}, {@code --rules} override, {@code --format}, the
 * match/test subcommands — is deferred to CLI completion (roadmap item 39)
 * and rejected here as an unknown option: a mistyped flag must fail
 * loudly, not vanish into a positional argument.
 *
 * <p>Parsing is fail-closed: an unknown subcommand, an unknown option, a
 * missing {@code --profile} value, an unknown profile name, a blank target,
 * a missing target path, or the {@code --fix}/{@code --fix-dry-run}
 * combination all throw {@link NopLintException}; the CLI entry point maps
 * that to exit code 2 with the usage line.</p>
 */
public record CliOptions(List<String> targets, LintProfile profile, FixMode fixMode) {

    /**
     * The fix flow the run drives: {@code NONE} reports only, {@code APPLY}
     * writes fixed content back to the files, {@code DRY_RUN} computes the
     * full fix loop in memory and prints the unified diff instead.
     */
    public enum FixMode {
        NONE, APPLY, DRY_RUN
    }

    private static final String USAGE =
            "usage: nop-lint check <path>... [--profile fast|standard] [--fix|--fix-dry-run]";

    /**
     * The usage line carried by every parse error message.
     */
    public static String usage() {
        return USAGE;
    }

    public CliOptions {
        targets = List.copyOf(targets);
    }

    /**
     * The report-only surface (no fix flow) — the pre-autofix shape.
     */
    public CliOptions(List<String> targets, LintProfile profile) {
        this(targets, profile, FixMode.NONE);
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
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("--profile".equals(arg)) {
                profile = parseProfile(args, ++i);
            } else if ("--fix".equals(arg)) {
                fixMode = withFixMode(fixMode, FixMode.APPLY, "--fix");
            } else if ("--fix-dry-run".equals(arg)) {
                fixMode = withFixMode(fixMode, FixMode.DRY_RUN, "--fix-dry-run");
            } else if (arg.startsWith("-")) {
                throw new NopLintException(USAGE + " (unknown option '" + arg + "'; v1 supports "
                        + "only --profile fast|standard and --fix|--fix-dry-run)");
            } else {
                if (arg.isBlank())
                    throw new NopLintException(USAGE + " (target path must not be blank)");
                targets.add(arg);
            }
        }

        if (targets.isEmpty())
            throw new NopLintException(USAGE + " (missing target path: at least one file or "
                    + "directory to check is required)");

        return new CliOptions(List.copyOf(targets), profile, fixMode);
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

    private static LintProfile parseProfile(String[] args, int valueIndex) {
        if (valueIndex >= args.length)
            throw new NopLintException(USAGE + " (--profile requires a value: fast|standard)");
        String value = args[valueIndex];
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("fast".equals(normalized))
            return LintProfile.FAST;
        if ("standard".equals(normalized))
            return LintProfile.STANDARD;
        throw new NopLintException(USAGE + " (unknown profile '" + value
                + "'; expected fast|standard)");
    }
}
