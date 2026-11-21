package io.nop.git.cli;

import io.nop.git.api.NopGitCommit;
import io.nop.git.api.NopGitException;
import io.nop.git.api.NopGitStatus;

import java.util.List;

import static io.nop.git.cli.GitCompatCli.CliContext;
import static io.nop.git.cli.GitCompatCli.EXIT_FAILURE;
import static io.nop.git.cli.GitCompatCli.EXIT_OK;
import static io.nop.git.cli.GitCompatCli.EXIT_USAGE;
import static io.nop.git.cli.GitOutputFormatter.statusLong;

/**
 * {@code git commit -m <msg> [-a]}: success prints the native
 * {@code [branch sha] message} line (with {@code (root-commit)} on the first
 * commit); nothing-to-commit renders the native multi-section advice text and
 * exits 1.
 */
final class CommitCommand {

    private CommitCommand() {
    }

    static int run(CliContext ctx, List<String> args) {
        String message = null;
        boolean all = false;
        List<String> paths = new java.util.ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.startsWith("--nop-git-internal"))
                continue;
            if (arg.equals("-m") || arg.equals("--message")) {
                if (i + 1 >= args.size()) {
                    ctx.err().println("error: switch `m' requires a value");
                    return EXIT_USAGE;
                }
                message = args.get(++i);
            } else if (arg.startsWith("--message=")) {
                message = arg.substring("--message=".length());
            } else if (arg.equals("-a") || arg.equals("--all")) {
                all = true;
            } else if (arg.startsWith("-") && !arg.startsWith("--") && arg.length() > 2) {
                // combined short-flag cluster: native git accepts -qm, -am, -ma,
                // -qam ... (live audit: -qm exited 129 while native commits fine —
                // the single most common AI-tool invocation shape). -q is accepted
                // as a no-op like native quiet mode.
                String chars = arg.substring(1);
                for (int ci = 0; ci < chars.length(); ci++) {
                    char c = chars.charAt(ci);
                    if (c == 'm') {
                        String rest = chars.substring(ci + 1);
                        if (!rest.isEmpty()) {
                            message = rest;
                        } else if (i + 1 < args.size()) {
                            message = args.get(++i);
                        } else {
                            ctx.err().println("error: switch `m' requires a value");
                            return EXIT_USAGE;
                        }
                        break;
                    } else if (c == 'a') {
                        all = true;
                    } else if (c == 'q') {
                        // quiet: accepted, output unchanged (subset contract)
                    } else {
                        ctx.err().println("error: unknown option `" + c + "'");
                        return EXIT_USAGE;
                    }
                }
            } else if (arg.equals("--")) {
                // remaining are paths (unused subset)
            } else if (arg.startsWith("-")) {
                ctx.err().println("error: unknown option `"
                        + (arg.startsWith("--") ? arg.substring(2) : arg.substring(1)) + "'");
                return EXIT_USAGE;
            } else {
                paths.add(arg);
            }
        }
        if (!paths.isEmpty()) {
            // native: extra positional args are pathspecs; nop-git does not
            // support pathspec commits, so mirror the native miss error
            ctx.err().println("error: pathspec '" + paths.get(0)
                    + "' did not match any file(s) known to git");
            return EXIT_FAILURE;
        }

        try (var svc = ctx.openRepo()) {
            NopGitStatus status = svc.status();

            if (all) {
                // -a stages modifications and deletions of tracked files,
                // never untracked files
                status.modified().forEach(svc::add);
                status.deleted().forEach(path ->
                        svc.add(path)); // re-add deletes record deletion in the index
                status = svc.status();
            }

            boolean unborn = status.branch() == null;
            boolean hasStaged = !status.stagedAdded().isEmpty() || !status.stagedChanged().isEmpty()
                    || !status.stagedRemoved().isEmpty() || !status.stagedRenames().isEmpty();
            if (!hasStaged) {
                // native: nothing-to-commit renders the status and exits 1 —
                // this check precedes the editor/message handling (audit bug #8)
                statusLong(status, unborn).forEach(ctx.out()::println);
                return EXIT_FAILURE;
            }

            if (message == null) {
                ctx.err().println("fatal: no commit message given (only -m/--message is supported)");
                return GitCompatCli.EXIT_FATAL;
            }

            NopGitCommit commit = svc.commit(message, null, null);

            // native success line: [branch (root-commit)? abbrev] message
            String branch = status.branch() != null ? status.branch() : "HEAD";
            String root = commit.parentIds().isEmpty() ? " (root-commit)" : "";
            ctx.out().println("[" + branch + root + " " + commit.abbrevId() + "] "
                    + commit.shortMessage());
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }
}
