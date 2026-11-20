package io.nop.git.cli;

import io.nop.git.api.DiffOptions;
import io.nop.git.api.NopGitDiffEntry;
import io.nop.git.api.NopGitException;

import java.util.ArrayList;
import java.util.List;

import static io.nop.git.cli.GitCompatCli.CliContext;
import static io.nop.git.cli.GitCompatCli.EXIT_OK;
import static io.nop.git.cli.GitOutputFormatter.diffStat;

/**
 * {@code git diff}: unified diff text for the index-vs-worktree /
 * {@code --cached} / {@code <rev>}-vs-worktree shapes, {@code --stat} for the
 * diffstat layout. Exit 0 regardless of whether a diff exists (native
 * behavior without --exit-code).
 */
final class DiffCommand {

    private DiffCommand() {
    }

    static int run(CliContext ctx, List<String> args) {
        boolean cached = false;
        boolean stat = false;
        String rev = null;
        List<String> paths = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("--nop-git-internal"))
                continue;
            switch (arg) {
                case "--cached" -> cached = true;
                case "--stat" -> stat = true;
                case "--" -> { /* path separator, remaining are paths */ }
                default -> {
                    if (arg.startsWith("-")) {
                        ctx.err().println("error: unknown option `" + strip(arg) + "'");
                        return GitCompatCli.EXIT_USAGE;
                    }
                    if (rev == null && paths.isEmpty()) {
                        rev = arg; // first non-option arg: <rev> shape
                    } else {
                        paths.add(arg);
                    }
                }
            }
        }

        DiffOptions options = cached ? new DiffOptions(true, null, stat)
                : new DiffOptions(false, rev, stat);

        try (var svc = ctx.openRepo()) {
            if (stat) {
                List<NopGitDiffEntry> entries = svc.diff(options);
                diffStat(entries).forEach(ctx.out()::println);
            } else {
                svc.unifiedDiff(options).forEach(ctx.out()::println);
            }
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    private static String strip(String arg) {
        return arg.startsWith("--") ? arg.substring(2) : arg.substring(1);
    }
}
