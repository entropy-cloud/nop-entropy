package io.nop.git.cli;

import io.nop.git.api.NopGitException;
import io.nop.git.api.NopGitStatus;

import java.util.List;

import static io.nop.git.cli.GitCompatCli.CliContext;
import static io.nop.git.cli.GitOutputFormatter.statusLong;
import static io.nop.git.cli.GitOutputFormatter.statusPorcelain;

/**
 * {@code git status}: long format by default, porcelain v1 with
 * {@code --porcelain} or {@code -s}. Exit 0 even when the tree is dirty
 * (native behavior).
 */
final class StatusCommand {

    private StatusCommand() {
    }

    static int run(CliContext ctx, List<String> args) {
        for (String arg : args) {
            if (arg.startsWith("--nop-git-internal"))
                continue;
            if (!arg.equals("--porcelain") && !arg.equals("-s")) {
                ctx.err().println("error: unknown option `"
                        + (arg.startsWith("--") ? arg.substring(2) : arg.substring(1)) + "'");
                return GitCompatCli.EXIT_USAGE;
            }
        }
        boolean short_ = args.contains("--porcelain") || args.contains("-s");

        try (var svc = ctx.openRepo()) {
            NopGitStatus status = svc.status();
            boolean unborn = svc.resolveRevision("HEAD") == null;
            List<String> lines = short_ ? statusPorcelain(status)
                    : statusLong(status, unborn);
            lines.forEach(ctx.out()::println);
            return GitCompatCli.EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }
}
