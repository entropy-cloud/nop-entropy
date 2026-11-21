package io.nop.git.cli;

import io.nop.git.api.IGitRepositoryService;
import io.nop.git.api.NopGitException;
import io.nop.git.api.NopGitRename;
import io.nop.git.api.NopGitStatus;

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
                if (failsPathspecCheck(ctx, svc, pattern)) {
                    ctx.err().println("fatal: pathspec '" + pattern + "' did not match any files");
                    return GitCompatCli.EXIT_FATAL;
                }
                svc.add(pattern);
            }
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    /**
     * Native {@code git add} fails when a pathspec matches nothing — neither a
     * worktree file/directory nor an index entry. The silent-success variant
     * silently loses AI tool staging requests (audit bug 2026-11-21 #3).
     */
    private static boolean failsPathspecCheck(CliContext ctx, IGitRepositoryService svc,
                                              String pattern) {
        if (pattern.equals(".") || pattern.contains("*"))
            return false;
        java.io.File file = new java.io.File(ctx.workDir(), pattern);
        if (file.exists())
            return false;
        // deleted-but-staged paths only exist in the index
        NopGitStatus status = svc.status();
        for (List<String> group : List.of(status.stagedAdded(), status.stagedChanged(),
                status.stagedRemoved(), status.modified(), status.deleted(), status.untracked())) {
            for (String path : group) {
                if (path.equals(pattern) || path.startsWith(pattern + "/"))
                    return false;
            }
        }
        for (NopGitRename rename : status.stagedRenames()) {
            if (rename.oldPath().equals(pattern) || rename.newPath().equals(pattern))
                return false;
        }
        return true;
    }

    private static String strip(String arg) {
        return arg.startsWith("--") ? arg.substring(2) : arg.substring(1);
    }
}
