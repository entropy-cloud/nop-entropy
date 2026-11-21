package io.nop.git.cli;

import io.nop.git.api.NopGitException;

import java.util.List;

import static io.nop.git.cli.GitCompatCli.CliContext;
import static io.nop.git.cli.GitCompatCli.EXIT_FATAL;
import static io.nop.git.cli.GitCompatCli.EXIT_OK;
import static io.nop.git.cli.GitCompatCli.EXIT_USAGE;

/**
 * {@code git rev-parse}: {@code HEAD} (or any rev), {@code --abbrev-ref HEAD},
 * {@code --show-toplevel}. Unborn HEAD fails with the native ambiguous-argument
 * message.
 */
final class RevParseCommand {

    private RevParseCommand() {
    }

    static int run(CliContext ctx, List<String> args) {
        boolean abbrevRef = false;
        boolean showToplevel = false;
        boolean isInsideWorkTree = false;
        List<String> revs = new java.util.ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("--nop-git-internal"))
                continue;
            switch (arg) {
                case "--abbrev-ref" -> abbrevRef = true;
                case "--show-toplevel" -> showToplevel = true;
                case "--is-inside-work-tree" -> isInsideWorkTree = true;
                case "--" -> { }
                default -> {
                    if (arg.startsWith("-")) {
                        ctx.err().println("error: unknown option `"
                                + (arg.startsWith("--") ? arg.substring(2) : arg.substring(1)) + "'");
                        return EXIT_USAGE;
                    }
                    revs.add(arg);
                }
            }
        }

        try (var svc = ctx.openRepo()) {
            if (showToplevel) {
                ctx.out().println(svc.topLevel());
                return EXIT_OK;
            }
            if (isInsideWorkTree) {
                // native prints true inside a worktree, false in a bare repo
                ctx.out().println("true");
                return EXIT_OK;
            }
            if (revs.isEmpty()) {
                ctx.err().println("usage: git rev-parse [--abbrev-ref] <args>");
                return EXIT_USAGE;
            }
            for (String rev : revs) {
                String resolved = svc.resolveRevision(rev);
                if (resolved == null) {
                    // native rev-parse echoes the unresolvable arg on stdout
                    ctx.out().println(rev);
                    ctx.err().println("fatal: ambiguous argument '" + rev
                            + "': unknown revision or path not in the working tree.");
                    ctx.err().println("Use '--' to separate paths from revisions, like this:");
                    ctx.err().println("'git <command> [<revision>...] -- [<file>...]'");
                    return EXIT_FATAL;
                }
                if (abbrevRef) {
                    // --abbrev-ref HEAD renders the branch name; for unborn
                    // HEAD native prints "HEAD"
                    String branch = "HEAD".equals(rev) ? svc.currentBranch() : rev;
                    ctx.out().println(branch != null ? branch : "HEAD");
                } else {
                    ctx.out().println(resolved);
                }
            }
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }
}
