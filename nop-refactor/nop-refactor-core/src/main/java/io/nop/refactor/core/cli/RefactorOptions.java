package io.nop.refactor.core.cli;

import io.nop.refactor.core.NopRefactorException;

import java.util.ArrayList;
import java.util.List;

/**
 * The refactor CLI's parsed command line (nop-refactor WI7): one subcommand
 * (preview | apply), the ruleset VFS prefix, an optional {@code --json}
 * machine-readable render switch, and the positional target files or
 * directories. Hand-written fail-closed parsing along the NopLintCli
 * precedent — any illegal input throws with the usage line attached, which
 * the CLI maps to exit code 2.
 *
 * @param mode          preview (dry-run, nothing written) or apply (atomic write)
 * @param rulesetPrefix VFS prefix the {@code RuleSetLoader} loads the rules from
 * @param json          render the same payload as machine-readable JSON
 * @param targets       positional target files or directories (at least one)
 */
public record RefactorOptions(Mode mode, String rulesetPrefix, boolean json, List<String> targets) {

    public enum Mode {
        PREVIEW, APPLY
    }

    public RefactorOptions {
        if (mode == null)
            throw new NopRefactorException(usage());
        if (rulesetPrefix == null || rulesetPrefix.isBlank())
            throw new NopRefactorException("--rules <vfs-prefix> is required (the ruleset is the "
                    + "verified safe surface: template rendering, fail-closed matrix, resource "
                    + "gates)\n" + usage());
        if (targets == null || targets.isEmpty())
            throw new NopRefactorException("at least one target file or directory is required\n"
                    + usage());
        targets = List.copyOf(targets);
    }

    /**
     * Parses the argument vector fail-closed: unknown flags, a missing
     * subcommand, or a missing value for {@code --rules} all throw with the
     * usage line (exit code 2 territory, never a guessed default).
     */
    public static RefactorOptions parse(String... args) {
        if (args == null || args.length == 0)
            throw new NopRefactorException(usage());
        int i = 0;
        Mode mode;
        if ("preview".equals(args[i]))
            mode = Mode.PREVIEW;
        else if ("apply".equals(args[i]))
            mode = Mode.APPLY;
        else
            throw new NopRefactorException("unknown subcommand '" + args[i]
                    + "' (expected preview|apply)\n" + usage());
        i++;

        String rulesetPrefix = null;
        boolean json = false;
        List<String> targets = new ArrayList<>();
        for (; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--rules" -> {
                    if (i + 1 >= args.length)
                        throw new NopRefactorException("--rules requires a VFS prefix value\n"
                                + usage());
                    rulesetPrefix = args[++i];
                }
                case "--json" -> json = true;
                default -> {
                    if (arg.startsWith("--"))
                        throw new NopRefactorException("unknown option '" + arg + "'\n" + usage());
                    targets.add(arg);
                }
            }
        }
        return new RefactorOptions(mode, rulesetPrefix, json, targets);
    }

    public static String usage() {
        return "usage: nop-refactor preview|apply --rules <vfs-prefix> [--json] <file|dir>...";
    }
}
