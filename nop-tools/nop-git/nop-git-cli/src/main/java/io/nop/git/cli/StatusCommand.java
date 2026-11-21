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
        List<String> pathspecs = new java.util.ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("--nop-git-internal"))
                continue;
            if (!arg.equals("--porcelain") && !arg.equals("-s") && !arg.startsWith("-")) {
                // native accepts pathspecs, limiting the report to matching paths
                pathspecs.add(arg);
            } else if (!arg.equals("--porcelain") && !arg.equals("-s")) {
                ctx.err().println("error: unknown option `"
                        + (arg.startsWith("--") ? arg.substring(2) : arg.substring(1)) + "'");
                return GitCompatCli.EXIT_USAGE;
            }
        }
        boolean short_ = args.contains("--porcelain") || args.contains("-s");

        try (var svc = ctx.openRepo()) {
            NopGitStatus status = svc.status();
            if (!pathspecs.isEmpty())
                status = filterByPathspecs(status,
                        canonical(java.nio.file.Path.of(svc.topLevel())),
                        canonical(ctx.workDir().toPath()),
                        pathspecs);
            boolean unborn = svc.resolveRevision("HEAD") == null;
            List<String> lines = short_ ? statusPorcelain(status)
                    : statusLong(status, unborn);
            lines.forEach(ctx.out()::println);
            return GitCompatCli.EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    /**
     * Keep only entries matching one of the given pathspecs. Status paths are
     * repository-relative; a spec given relative to the current directory is
     * resolved against the repository top level for matching (native prefix
     * semantics: exact path or directory prefix).
     */
    /** Canonical form so symlinked cwd (e.g. /var vs /private/var on macOS)
     * matches the top level JGit reports. */
    private static java.nio.file.Path canonical(java.nio.file.Path path) {
        try {
            return path.toRealPath();
        } catch (java.io.IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    private static NopGitStatus filterByPathspecs(NopGitStatus status, java.nio.file.Path repoTop,
                                                  java.nio.file.Path cwd, List<String> pathspecs) {
        List<String> specs = pathspecs.stream()
                .map(p -> repoTop.resolve(cwd.resolve(p).normalize()).normalize().toString())
                .toList();
        java.util.function.Predicate<String> matches = path -> {
            String abs = repoTop.resolve(path).normalize().toString();
            for (String spec : specs) {
                if (abs.equals(spec) || abs.startsWith(spec + "/"))
                    return true;
            }
            return false;
        };
        return new NopGitStatus(
                status.branch(),
                status.stagedAdded().stream().filter(matches).toList(),
                status.stagedChanged().stream().filter(matches).toList(),
                status.stagedRemoved().stream().filter(matches).toList(),
                status.stagedRenames().stream()
                        .filter(r -> matches.test(r.oldPath()) || matches.test(r.newPath()))
                        .toList(),
                status.modified().stream().filter(matches).toList(),
                status.deleted().stream().filter(matches).toList(),
                status.untracked().stream().filter(matches).toList());
    }
}
