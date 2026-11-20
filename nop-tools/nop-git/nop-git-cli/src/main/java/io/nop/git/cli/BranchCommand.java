package io.nop.git.cli;

import io.nop.git.api.NopGitException;
import io.nop.git.api.NopGitRef;

import java.util.List;

import static io.nop.git.cli.GitCompatCli.CliContext;
import static io.nop.git.cli.GitCompatCli.EXIT_OK;
import static io.nop.git.cli.GitOutputFormatter.branchList;

/**
 * {@code git branch}: bare list, {@code --show-current}, {@code -a}.
 */
final class BranchCommand {

    private BranchCommand() {
    }

    static int run(CliContext ctx, List<String> args) {
        boolean showCurrent = false;
        boolean all = false;
        String newBranch = null;
        for (String arg : args) {
            if (arg.startsWith("--nop-git-internal"))
                continue;
            switch (arg) {
                case "--show-current" -> showCurrent = true;
                case "-a", "--all" -> all = true;
                default -> {
                    if (!arg.startsWith("-")) {
                        // native `git branch <name>`: create at HEAD, silent
                        newBranch = arg;
                    } else {
                        ctx.err().println("error: unknown option `"
                                + (arg.startsWith("--") ? arg.substring(2) : arg.substring(1)) + "'");
                        return GitCompatCli.EXIT_USAGE;
                    }
                }
            }
        }

        try (var svc = ctx.openRepo()) {
            if (newBranch != null) {
                svc.createBranch(newBranch);
                return GitCompatCli.EXIT_OK;
            }
            if (showCurrent) {
                // native prints the symbolic branch even for unborn HEAD
                String branch = svc.status().branch();
                ctx.out().println(branch == null ? "" : branch);
                return EXIT_OK;
            }
            List<NopGitRef> refs = all ? svc.branchListAll() : svc.branchList();
            branchList(refs, svc.currentBranch()).forEach(ctx.out()::println);
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }
}
