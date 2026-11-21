package io.nop.git.cli;

import io.nop.git.api.LogOptions;
import io.nop.git.api.NopGitCommit;
import io.nop.git.api.NopGitException;

import java.util.List;

import static io.nop.git.cli.GitCompatCli.CliContext;
import static io.nop.git.cli.GitCompatCli.EXIT_OK;
import static io.nop.git.cli.GitOutputFormatter.logLong;
import static io.nop.git.cli.GitOutputFormatter.logOneline;

/**
 * {@code git log --oneline [-n <num>]}; long format when --oneline is absent.
 */
final class LogCommand {

    private LogCommand() {
    }

    static int run(CliContext ctx, List<String> args) {
        boolean oneline = false;
        int maxCount = -1;
        String rev = "HEAD";
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.startsWith("--nop-git-internal"))
                continue;
            if (arg.equals("--oneline")) {
                oneline = true;
            } else if (arg.equals("-n") || arg.startsWith("--max-count=")) {
                if (arg.equals("-n")) {
                    if (i + 1 >= args.size()) {
                        ctx.err().println("error: switch `n' requires a value");
                        return GitCompatCli.EXIT_USAGE;
                    }
                    maxCount = Integer.parseInt(args.get(++i));
                } else {
                    maxCount = Integer.parseInt(arg.substring("--max-count=".length()));
                }
            } else if (arg.startsWith("-")) {
                ctx.err().println("error: unknown option `"
                        + (arg.startsWith("--") ? arg.substring(2) : arg.substring(1)) + "'");
                return GitCompatCli.EXIT_USAGE;
            } else {
                rev = arg;
            }
        }

        try (var svc = ctx.openRepo()) {
            // unborn HEAD: native log fails with the dedicated message
            if ("HEAD".equals(rev) && svc.resolveRevision("HEAD") == null) {
                ctx.err().println("fatal: your current branch '"
                        + svc.status().branch() + "' does not have any commits yet");
                return GitCompatCli.EXIT_FATAL;
            }
            // unknown rev: the core log() returns an empty list for unresolvable
            // revs (API contract), but native git fails loudly — a silent empty
            // log with exit 0 is a fake-green hazard for AI consumers
            if (svc.resolveRevision(rev) == null) {
                ctx.err().println("fatal: ambiguous argument '" + rev
                        + "': unknown revision or path not in the working tree.");
                ctx.err().println("Use '--' to separate paths from revisions, like this:");
                ctx.err().println("'git <command> [<revision>...] -- [<file>...]'");
                return GitCompatCli.EXIT_FATAL;
            }
            List<NopGitCommit> commits = svc.log(new LogOptions(rev, maxCount));
            List<String> lines = oneline ? logOneline(commits) : logLong(commits);
            lines.forEach(ctx.out()::println);
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }
}
