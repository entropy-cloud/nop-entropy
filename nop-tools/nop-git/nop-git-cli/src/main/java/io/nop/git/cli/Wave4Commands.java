package io.nop.git.cli;

import io.nop.git.api.AddWorktreeOptions;
import io.nop.git.api.CleanOptions;
import io.nop.git.api.GitResetMode;
import io.nop.git.api.NopGitCommit;
import io.nop.git.api.NopGitException;
import io.nop.git.api.NopGitWorktree;
import io.nop.git.core.JGitRepositoryService;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static io.nop.git.cli.GitCompatCli.CliContext;
import static io.nop.git.cli.GitCompatCli.EXIT_FAILURE;
import static io.nop.git.cli.GitCompatCli.EXIT_FATAL;
import static io.nop.git.cli.GitCompatCli.EXIT_OK;

/**
 * Wave 4 command handlers (P2/P3 + worktree): behavior aligned with native
 * git (native-style messages, exit codes; silent success where native is
 * silent on non-tty).
 */
final class Wave4Commands {

    private Wave4Commands() {
    }

    // ------------------------------------------------------------------
    // P2
    // ------------------------------------------------------------------

    static int checkout(CliContext ctx, List<String> args, boolean isSwitch) {
        boolean create = false;
        boolean pathMode = false;
        String branch = null;
        List<String> paths = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals("--")) {
                pathMode = true;
            } else if (pathMode) {
                paths.add(arg);
            } else if (arg.equals("-b") || arg.equals("-c") || arg.equals("-B"))
                create = true;
            else if (!arg.startsWith("-"))
                branch = arg;
            else
                return unknownOption(ctx, arg);
        }
        try (var svc = ctx.openRepo()) {
            // checkout -- <paths> discards worktree changes of tracked paths
            // (native semantics; restore is the same operation)
            if (pathMode) {
                for (String path : paths) {
                    svc.restore(path);
                }
                return EXIT_OK;
            }
            if (branch == null) {
                ctx.err().println("fatal: branch name required");
                return EXIT_FATAL;
            }
            if (!create && branch.equals(svc.currentBranch())) {
                ctx.err().println("Already on '" + branch + "'");
                return EXIT_OK;
            }
            // unknown ref: native checkout treats it as a pathspec miss (exit
            // 1), switch as an invalid reference (exit 128) — audit bug #7
            if (!create && svc.resolveRevision(branch) == null
                    && !isLocalBranch(svc, branch)) {
                if (isSwitch) {
                    ctx.err().println("fatal: invalid reference: " + branch);
                    return EXIT_FATAL;
                }
                ctx.err().println("error: pathspec '" + branch
                        + "' did not match any file(s) known to git");
                return EXIT_FAILURE;
            }
            svc.checkout(branch, create);
            ctx.err().println(create
                    ? "Switched to a new branch '" + branch + "'"
                    : "Switched to branch '" + branch + "'");
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    private static boolean isLocalBranch(io.nop.git.api.IGitRepositoryService svc, String name) {
        return svc.branchList().stream()
                .anyMatch(ref -> ref.shortName().equals(name) || ref.name().endsWith("/" + name));
    }

    static int reset(CliContext ctx, List<String> args) {
        GitResetMode mode = GitResetMode.MIXED;
        String rev = null;
        for (String arg : args) {
            switch (arg) {
                case "--soft" -> mode = GitResetMode.SOFT;
                case "--mixed" -> {
                }
                case "--hard" -> mode = GitResetMode.HARD;
                default -> {
                    if (arg.startsWith("-"))
                        return unknownOption(ctx, arg);
                    rev = arg;
                }
            }
        }
        try (var svc = ctx.openRepo()) {
            svc.reset(mode, rev);
            if (mode == GitResetMode.HARD) {
                // native prints the new HEAD to stdout (audit bug #4)
                String head = svc.resolveRevision("HEAD");
                String subject = headSubject(svc, head);
                ctx.out().println("HEAD is now at " + abbrev(head) + " " + subject);
            }
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    static int clean(CliContext ctx, List<String> args) {
        boolean force = false;
        boolean directories = false;
        boolean dryRun = false;
        for (String arg : args) {
            if (arg.equals("-n") || arg.equals("--dry-run"))
                dryRun = true;
            else if (arg.equals("-f") || arg.equals("--force"))
                force = true;
            else if (arg.equals("-d"))
                directories = true;
            else if (arg.equals("-fd") || arg.equals("-df"))
                directories = force = true;
            else if (!arg.startsWith("-"))
                return unknownOption(ctx, arg);
        }
        try (var svc = ctx.openRepo()) {
            List<String> paths = svc.clean(new CleanOptions(force, directories, dryRun));
            for (String path : paths) {
                ctx.out().println((force && !dryRun ? "Removing " : "Would remove ") + path);
            }
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    static int restore(CliContext ctx, List<String> args) {
        List<String> paths = positional(args);
        if (paths.isEmpty()) {
            ctx.err().println("fatal: pathspec required");
            return EXIT_FATAL;
        }
        try (var svc = ctx.openRepo()) {
            for (String path : paths) {
                svc.restore(path);
            }
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    static int rm(CliContext ctx, List<String> args) {
        boolean cached = false;
        boolean force = false;
        List<String> paths = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals("--cached"))
                cached = true;
            else if (arg.equals("-f") || arg.equals("--force"))
                force = true;
            else if (!arg.startsWith("-"))
                paths.add(arg);
            else
                return unknownOption(ctx, arg);
        }
        if (paths.isEmpty()) {
            ctx.err().println("fatal: pathspec required");
            return EXIT_FATAL;
        }
        try (var svc = ctx.openRepo()) {
            for (String path : paths) {
                svc.rm(path, cached, force);
                if (!cached)
                    ctx.out().println("rm '" + path + "'");
            }
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    static int mv(CliContext ctx, List<String> args) {
        List<String> positional = positional(args);
        if (positional.size() != 2) {
            ctx.err().println("fatal: mv requires <source> <destination>");
            return EXIT_FATAL;
        }
        try (var svc = ctx.openRepo()) {
            svc.mv(positional.get(0), positional.get(1));
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    static int stash(CliContext ctx, List<String> args) {
        String sub = args.isEmpty() ? "push" : args.get(0);
        try (var svc = ctx.openRepo()) {
            switch (sub) {
                case "push", "save" -> {
                    String sha = svc.stashPush();
                    if (sha == null) {
                        ctx.out().println("No local changes to save");
                        return EXIT_OK;
                    }
                    // native prints the WIP line to stderr with sha+subject
                    List<String> log = svc.stashList();
                    if (!log.isEmpty())
                        ctx.err().println("Saved working directory and index state "
                                + log.get(0).replaceFirst("^stash@\\{0\\}: ", ""));
                    return EXIT_OK;
                }
                case "list" -> {
                    svc.stashList().forEach(ctx.out()::println);
                    return EXIT_OK;
                }
                case "pop", "apply" -> {
                    // native: empty stash prints the bare message to stderr and
                    // exits 1, without the fatal: prefix (audit bug #5)
                    if (svc.stashList().isEmpty()) {
                        ctx.err().println("No stash entries found.");
                        return EXIT_FAILURE;
                    }
                    svc.stashPop();
                    return EXIT_OK;
                }
                default -> {
                    ctx.err().println("git: 'stash " + sub + "' is not a nop-git command.");
                    return EXIT_FAILURE;
                }
            }
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    // ------------------------------------------------------------------
    // worktree
    // ------------------------------------------------------------------

    static int worktree(CliContext ctx, List<String> args) {
        String sub = args.isEmpty() ? "" : args.get(0);
        List<String> rest = args.subList(Math.min(1, args.size()), args.size());
        try (var svc = ctx.openRepo()) {
            switch (sub) {
                case "add" -> {
                    String newBranch = null;
                    boolean detach = false;
                    String commitish = null;
                    String path = null;
                    for (int i = 0; i < rest.size(); i++) {
                        String arg = rest.get(i);
                        if (arg.equals("-b") || arg.equals("-c")) {
                            if (i + 1 >= rest.size())
                                return ctx.fatal("switch `b' requires a value");
                            newBranch = rest.get(++i);
                        } else if (arg.equals("--detach"))
                            detach = true;
                        else if (arg.startsWith("-"))
                            return unknownOption(ctx, arg);
                        else if (path == null)
                            path = arg;
                        else
                            commitish = arg;
                    }
                    if (path == null)
                        return ctx.fatal("worktree add requires a path");
                    NopGitWorktree added = svc.addWorktree(new AddWorktreeOptions(
                            Path.of(path), newBranch, commitish, detach));
                    if (newBranch != null)
                        ctx.err().println("Preparing worktree (new branch '" + newBranch + "')");
                    else if (detach)
                        ctx.err().println("HEAD is now at " + abbrev(added.head())
                                + " " + headSubject(svc, added.head()));
                    return EXIT_OK;
                }
                case "list" -> {
                    boolean porcelain = rest.contains("--porcelain");
                    List<NopGitWorktree> trees = svc.listWorktrees();
                    if (porcelain) {
                        for (NopGitWorktree tree : trees) {
                            ctx.out().println("worktree " + tree.path());
                            if (tree.head() != null)
                                ctx.out().println("HEAD " + tree.head());
                            if (tree.bare())
                                ctx.out().println("bare");
                            else if (tree.detached())
                                ctx.out().println("detached");
                            else if (tree.branch() != null)
                                ctx.out().println("branch refs/heads/" + tree.branch());
                            if (tree.locked())
                                ctx.out().println(tree.lockedReason() == null || tree.lockedReason().isEmpty()
                                        ? "locked" : "locked " + tree.lockedReason());
                            if (tree.prunable())
                                ctx.out().println("prunable " + tree.prunableReason());
                            ctx.out().println();
                        }
                    } else {
                        for (NopGitWorktree tree : trees) {
                            String ref = tree.detached() ? "(" + abbrev(tree.head()) + ")"
                                    : "[" + tree.branch() + "]";
                            ctx.out().println(" ".repeat(0) + tree.path() + " "
                                    + abbrev(tree.head()) + " " + ref
                                    + (tree.locked() ? " locked" : "")
                                    + (tree.prunable() ? " prunable" : ""));
                        }
                    }
                    return EXIT_OK;
                }
                case "remove" -> {
                    boolean force = rest.contains("--force") || rest.contains("-f");
                    String path = rest.stream().filter(a -> !a.startsWith("-")).findFirst()
                            .orElse(null);
                    if (path == null)
                        return ctx.fatal("worktree remove requires a path");
                    svc.removeWorktree(path, force);
                    return EXIT_OK;
                }
                case "prune" -> {
                    int pruned = svc.pruneWorktrees();
                    if (pruned > 0)
                        ctx.out().println("Pruning " + pruned + " worktree(s)");
                    return EXIT_OK;
                }
                default -> {
                    ctx.err().println("git: 'worktree " + sub
                            + "' is not a nop-git command. See 'git --help'.");
                    return EXIT_FAILURE;
                }
            }
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    private static String abbrev(String sha) {
        return sha == null ? "" : sha.substring(0, Math.min(7, sha.length()));
    }

    private static String headSubject(io.nop.git.api.IGitRepositoryService svc, String sha) {
        if (sha == null)
            return "";
        return svc.log(new io.nop.git.api.LogOptions(sha, 1)).stream().findFirst()
                .map(NopGitCommit::shortMessage).orElse("");
    }

    // ------------------------------------------------------------------
    // P3 local
    // ------------------------------------------------------------------

    static int init(CliContext ctx, List<String> args) {
        String branch = null;
        String dir = ".";
        boolean bare = false;
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.equals("-b")) {
                if (i + 1 >= args.size())
                    return ctx.fatal("switch `b' requires a value");
                branch = args.get(++i);
            } else if (arg.equals("--bare")) {
                bare = true;
            } else if (!arg.startsWith("-")) {
                dir = arg;
            }
        }
        File target;
        try {
            target = new File(ctx.workDir(), dir).getCanonicalFile();
        } catch (java.io.IOException e) {
            target = new File(ctx.workDir(), dir).getAbsoluteFile();
        }
        boolean existing = bare
                ? new File(target, "HEAD").exists()
                : new File(target, ".git").exists();
        if (existing) {
            // native re-init keeps the existing repo and reports it (audit
            // bug #11); an --initial-branch value is ignored with a warning
            if (branch != null)
                ctx.err().println("warning: re-init: ignored --initial-branch=" + branch);
            ctx.out().println("Reinitialized existing Git repository in "
                    + (bare ? target.getPath() + "/" : new File(target, ".git").getPath() + "/"));
            return EXIT_OK;
        }
        if (branch == null) {
            // native falls back to the init.defaultBranch config chain
            // (system -> global), defaulting to master (audit bug #12);
            // the JGit config chain lives behind the core adapter
            branch = JGitRepositoryService.defaultBranchFromConfig();
        }
        if (bare) {
            // bare layout: HEAD/refs/objects live at the top level, no worktree
            try {
                JGitRepositoryService.initBare(target, branch);
            } catch (NopGitException e) {
                return GitCompatCli.nativeFatal(ctx, e);
            }
            ctx.out().println("Initialized empty Git repository in "
                    + target.getPath() + "/");
            return EXIT_OK;
        }
        try (var svc = JGitRepositoryService.init(target, branch)) {
            ctx.out().println("Initialized empty Git repository in "
                    + new File(target, ".git").getPath() + "/");
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    static int remote(CliContext ctx, List<String> args) {
        String sub = args.isEmpty() ? "" : args.get(0);
        List<String> rest = args.subList(Math.min(1, args.size()), args.size());
        try (var svc = ctx.openRepo()) {
            switch (sub) {
                case "", "show" -> {
                    svc.remoteList().forEach(ctx.out()::println);
                    return EXIT_OK;
                }
                case "-v", "--verbose" -> {
                    svc.remoteListVerbose().forEach(ctx.out()::println);
                    return EXIT_OK;
                }
                case "add" -> {
                    if (rest.size() != 2)
                        return ctx.fatal("usage: git remote add <name> <url>");
                    svc.remoteAdd(rest.get(0), rest.get(1));
                    return EXIT_OK;
                }
                case "set-url" -> {
                    if (rest.size() != 2)
                        return ctx.fatal("usage: git remote set-url <name> <url>");
                    svc.remoteSetUrl(rest.get(0), rest.get(1));
                    return EXIT_OK;
                }
                case "rm", "remove" -> {
                    if (rest.size() != 1)
                        return ctx.fatal("usage: git remote rm <name>");
                    svc.remoteRemove(rest.get(0));
                    return EXIT_OK;
                }
                default -> {
                    ctx.err().println("git: 'remote " + sub + "' is not a nop-git command.");
                    return EXIT_FAILURE;
                }
            }
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    // ------------------------------------------------------------------
    // P3 network
    // ------------------------------------------------------------------

    static int push(CliContext ctx, List<String> args) {
        boolean setUpstream = false;
        List<String> positional = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.equals("-u") || arg.equals("--set-upstream"))
                setUpstream = true;
            else if (arg.startsWith("-"))
                return unknownOption(ctx, arg);
            else
                positional.add(arg);
        }
        String remote = positional.isEmpty() ? "origin" : positional.get(0);
        String refspec = positional.size() > 1 ? positional.get(1) : null;
        try (var svc = ctx.openRepo()) {
            // native reports "Everything up-to-date" on stderr when the remote
            // tracking ref already matches the local tip (audit bug #9)
            String branch = refspec != null && !refspec.isEmpty()
                    ? refspec.split(":")[0] : svc.currentBranch();
            String tracking = svc.resolveRevision("refs/remotes/" + remote + "/" + branch);
            String head = svc.resolveRevision("HEAD");
            if (tracking != null && tracking.equals(head)) {
                ctx.err().println("Everything up-to-date");
                return EXIT_OK;
            }
            svc.push(remote, refspec, setUpstream);
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    static int fetch(CliContext ctx, List<String> args) {
        List<String> positional = positional(args);
        String remote = positional.isEmpty() ? "origin" : positional.get(0);
        try (var svc = ctx.openRepo()) {
            svc.fetch(remote);
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    static int pull(CliContext ctx, List<String> args) {
        List<String> positional = positional(args);
        String remote = positional.isEmpty() ? "origin" : positional.get(0);
        String branch = positional.size() > 1 ? positional.get(1) : null;
        try (var svc = ctx.openRepo()) {
            String before = svc.resolveRevision("HEAD");
            svc.pull(remote, branch);
            String after = svc.resolveRevision("HEAD");
            // native reports "Already up to date." on stdout when the merge
            // does not move HEAD (audit bug #10)
            if (before != null && before.equals(after)) {
                ctx.out().println("Already up to date.");
            }
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    static int clone(CliContext ctx, List<String> args) {
        List<String> positional = positional(args);
        if (positional.isEmpty())
            return ctx.fatal("usage: git clone <repository> [<directory>]");
        File from = new File(positional.get(0));
        File to = positional.size() > 1
                ? new File(positional.get(1))
                : new File(ctx.workDir(), from.getName().replace(".git", ""));
        ctx.err().println("Cloning into '" + to.getName() + "'...");
        try {
            JGitRepositoryService.cloneRepository(from, to);
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    // ------------------------------------------------------------------
    // P3 history
    // ------------------------------------------------------------------

    static int merge(CliContext ctx, List<String> args) {
        boolean noFf = args.contains("--no-ff");
        String ref = args.stream().filter(a -> !a.startsWith("-")).findFirst().orElse(null);
        if (ref == null)
            return ctx.fatal("merge requires a commit/branch");
        try (var svc = ctx.openRepo()) {
            String before = svc.resolveRevision("HEAD");
            svc.merge(ref, noFf);
            String after = svc.resolveRevision("HEAD");
            if (!after.equals(before)) {
                ctx.out().println("Updating " + abbrev(before) + ".." + abbrev(after)
                        + "\nFast-forward");
            }
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    static int rebase(CliContext ctx, List<String> args) {
        String upstream = args.stream().filter(a -> !a.startsWith("-")).findFirst().orElse(null);
        if (upstream == null)
            return ctx.fatal("rebase requires an upstream branch");
        try (var svc = ctx.openRepo()) {
            svc.rebase(upstream);
            ctx.err().println("Successfully rebased and updated refs/heads/"
                    + svc.currentBranch() + ".");
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    static int cherryPick(CliContext ctx, List<String> args) {
        String commit = args.stream().filter(a -> !a.startsWith("-")).findFirst().orElse(null);
        if (commit == null)
            return ctx.fatal("cherry-pick requires a commit");
        try (var svc = ctx.openRepo()) {
            NopGitCommit picked = svc.cherryPick(commit);
            if (picked != null) {
                ctx.out().println("[" + svc.currentBranch() + " " + picked.abbrevId() + "] "
                        + picked.shortMessage());
            }
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static List<String> positional(List<String> args) {
        List<String> result = new ArrayList<>();
        for (String arg : args) {
            if (!arg.startsWith("-"))
                result.add(arg);
        }
        return result;
    }

    private static int unknownOption(CliContext ctx, String arg) {
        ctx.err().println("error: unknown option `"
                + (arg.startsWith("--") ? arg.substring(2) : arg.substring(1)) + "'");
        return GitCompatCli.EXIT_USAGE;
    }
}
