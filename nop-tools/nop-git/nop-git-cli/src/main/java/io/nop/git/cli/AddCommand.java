package io.nop.git.cli;

import io.nop.git.api.NopGitException;

import java.util.List;

import static io.nop.git.cli.GitCompatCli.CliContext;
import static io.nop.git.cli.GitCompatCli.EXIT_OK;

/**
 * {@code git add <pathspec>}: success prints nothing; stage-all via
 * {@code .} or {@code -A}.
 */
final class AddCommand {

    private AddCommand() {
    }

    static int run(CliContext ctx, List<String> args) {
        List<String> patterns = new java.util.ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("--nop-git-internal"))
                continue;
            if (arg.equals("-A") || arg.equals("--all")) {
                patterns.add(".");
            } else if (arg.startsWith("-")) {
                ctx.err().println("error: unknown option `" + strip(arg) + "'");
                return GitCompatCli.EXIT_USAGE;
            } else {
                patterns.add(arg);
            }
        }
        if (patterns.isEmpty()) {
            ctx.err().println("fatal: No paths with '--pathspec-from-file' specified.");
            return GitCompatCli.EXIT_FATAL;
        }

        try (var svc = ctx.openRepo()) {
            for (String pattern : patterns) {
                svc.add(pattern);
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
