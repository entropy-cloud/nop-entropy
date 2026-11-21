package io.nop.git.cli;

import io.nop.git.api.IGitRepositoryService;
import io.nop.git.api.NopGitException;
import io.nop.git.core.JdkBaseline;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * git-compatible command line entry (design 02-cli-compat-design).
 *
 * <p>Exit code contract: 0 success; 1 general failure (including
 * nothing-to-commit and unknown subcommands); 128 fatal errors (not a
 * repository, unknown revision); 129 usage errors (unknown option). Error
 * output uses native git prefixes ({@code fatal:}/{@code error:}) and never
 * prints a Java stack trace.
 */
public final class GitCompatCli {

    /**
     * Masqueraded version string (design: must be a real, existing git
     * semantic version so tool version checks don't break).
     */
    public static final String DEFAULT_GIT_VERSION = "2.45.0";

    static final int EXIT_OK = 0;
    static final int EXIT_FAILURE = 1;
    static final int EXIT_FATAL = 128;
    static final int EXIT_USAGE = 129;

    private GitCompatCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * CLI operation names are normalized to the facade operation table
     * (plan 2582: alias drift let policy rules be bypassed — {@code switch}
     * must evaluate as {@code checkout}, {@code worktree remove} as
     * {@code worktree-remove}, ...). The sub-command of grouped commands is
     * part of the operation name, not a path.
     */
    static String canonicalOperation(String command, List<String> args) {
        return switch (command) {
            case "switch" -> "checkout";
            case "worktree" -> args.isEmpty() ? "worktree"
                    : "worktree-" + args.get(0);
            default -> command;
        };
    }

    /**
     * CLI-level policy guard: deny audits and exits 1 with the native-style
     * fatal line; review resolves through the engine (env/interactive/timeout)
     * and exits 1 when not approved. Allow/approved passes through — the
     * facade writes the single audit line for the operation.
     */
    private static int policyGuard(CliContext ctx, io.nop.git.policy.IGitPolicyEngine engine,
                                   String command, List<String> args) {
        String operation = canonicalOperation(command, args);
        var builder = io.nop.git.policy.GitRequests.builder(operation,
                io.nop.git.policy.GitRequests.canonicalPath(ctx.workDir()), command + " " + String.join(" ", args));
        boolean firstPositional = true;
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if ("worktree".equals(command) && i == 0)
                continue; // sub-command: already folded into the operation name
            if (arg.startsWith("-")) {
                builder.flag(arg);
            } else if ("push".equals(operation) && firstPositional) {
                // "git push <remote> <refspec>": the first positional is the
                // remote, the rest are refspecs with force normalization
                // (plan 2582: a bare refspec must not fall into path rules)
                builder.remote(arg, null);
                firstPositional = false;
            } else if ("push".equals(operation)) {
                builder.refspec(arg);
            } else {
                builder.path(arg);
            }
        }
        var request = builder.build();
        io.nop.git.policy.GitVerdict verdict = engine.evaluate(request);
        if (engine.mode() == io.nop.git.policy.GitPolicyMode.OBSERVE) {
            return EXIT_OK; // observe never blocks; the facade records the audit line
        }
        if (verdict.effect() == io.nop.git.policy.GitVerdict.Effect.DENY) {
            engine.audit(request, verdict, "denied");
            ctx.err().println("fatal: policy denied: " + verdict.reason());
            return EXIT_FAILURE;
        }
        if (verdict.effect() == io.nop.git.policy.GitVerdict.Effect.REVIEW
                && !engine.resolveReview(request, verdict)) {
            engine.audit(request, verdict, "review-denied");
            ctx.err().println("fatal: policy denied: review not approved: " + verdict.reason());
            return EXIT_FAILURE;
        }
        return EXIT_OK;
    }

    /**
     * Run one CLI invocation; separated from {@link #main} for tests.
     */
    public static int run(String[] argv, PrintStream out, PrintStream err) {
        try {
            JdkBaseline.requireBaseline();
        } catch (NopGitException e) {
            // CLI contract (design 02 section 4): native-style message, no stack trace
            err.println("fatal: " + errorMessage(e));
            return EXIT_FATAL;
        }

        List<String> args = new ArrayList<>(List.of(argv));
        File dir = null;

        // global options
        while (!args.isEmpty()) {
            String arg = args.get(0);
            if (arg.equals("-C")) {
                if (args.size() < 2) {
                    err.println("fatal: option -C requires a value");
                    return EXIT_FATAL;
                }
                dir = new File(args.get(1));
                args.subList(0, 2).clear();
            } else if (arg.equals("--version") || arg.equals("-v")) {
                out.println("git version " + gitVersion());
                return EXIT_OK;
            } else if (arg.startsWith("--nop-git-internal")) {
                // platform-internal channel (audit switches arrive in Wave 3);
                // consumed here so git option parsing never sees them
                args.remove(0);
            } else if (arg.startsWith("-")) {
                err.println("error: unknown option `" + arg.substring(1) + "'");
                printUsage(err);
                return EXIT_USAGE;
            } else {
                break;
            }
        }

        if (args.isEmpty()) {
            printUsage(err);
            return EXIT_FAILURE;
        }

        String command = args.remove(0);
        File workDir = dir != null ? dir.getAbsoluteFile() : new File(".").getAbsoluteFile();

        // CLI interception point (design 03 section 6, point 1): parse argv
        // into a normalized request and consult the shared engine before any
        // facade call; the facade re-checks with the same engine instance
        io.nop.git.policy.IGitPolicyEngine engine =
                io.nop.git.policy.DefaultGitPolicyEngine.load(
                        java.nio.file.Path.of(io.nop.git.policy.GitRequests.canonicalPath(workDir)),
                        err);
        CliContext context = new CliContext(out, err, workDir, engine);

        if (engine.isActive()) {
            int policyExit = policyGuard(context, engine, command, args);
            if (policyExit != EXIT_OK)
                return policyExit;
        }

        return switch (command) {
            case "status" -> StatusCommand.run(context, args);
            case "diff" -> DiffCommand.run(context, args);
            case "add" -> AddCommand.run(context, args);
            case "commit" -> CommitCommand.run(context, args);
            case "log" -> LogCommand.run(context, args);
            case "branch" -> BranchCommand.run(context, args);
            case "rev-parse" -> RevParseCommand.run(context, args);
            case "config" -> ConfigCommand.run(context, args);
            case "checkout" -> Wave4Commands.checkout(context, args, false);
            case "switch" -> Wave4Commands.checkout(context, args, true);
            case "reset" -> Wave4Commands.reset(context, args);
            case "clean" -> Wave4Commands.clean(context, args);
            case "restore" -> Wave4Commands.restore(context, args);
            case "rm" -> Wave4Commands.rm(context, args);
            case "mv" -> Wave4Commands.mv(context, args);
            case "stash" -> Wave4Commands.stash(context, args);
            case "worktree" -> Wave4Commands.worktree(context, args);
            case "init" -> Wave4Commands.init(context, args);
            case "remote" -> Wave4Commands.remote(context, args);
            case "push" -> Wave4Commands.push(context, args);
            case "fetch" -> Wave4Commands.fetch(context, args);
            case "pull" -> Wave4Commands.pull(context, args);
            case "clone" -> Wave4Commands.clone(context, args);
            case "merge" -> Wave4Commands.merge(context, args);
            case "rebase" -> Wave4Commands.rebase(context, args);
            case "cherry-pick" -> Wave4Commands.cherryPick(context, args);
            case "help" -> {
                printUsage(out);
                yield EXIT_OK;
            }
            default -> {
                err.println("git: '" + command + "' is not a nop-git command."
                        + " See 'git --help'.");
                yield EXIT_FAILURE;
            }
        };
    }

    static String errorMessage(Exception e) {
        return e.getMessage();
    }

    static String gitVersion() {
        String version = System.getenv("NOP_GIT_VERSION");
        if (version == null || version.isBlank())
            version = System.getProperty("nop-git.version");
        return version == null || version.isBlank() ? DEFAULT_GIT_VERSION : version.trim();
    }

    private static void printUsage(PrintStream err) {
        err.println("usage: git [-C <path>] <command> [<args>]");
        err.println();
        err.println("nop-git commands:");
        err.println("   status     show the working tree status");
        err.println("   diff       show changes");
        err.println("   add        add file contents to the index");
        err.println("   commit     record changes to the repository");
        err.println("   log        show commit history");
        err.println("   branch     list branches");
        err.println("   rev-parse  resolve revisions");
        err.println("   config     read/write git config");
    }

    // ------------------------------------------------------------------
    // shared helpers for commands
    // ------------------------------------------------------------------

    /**
     * CLI-level policy guard: deny audits and exits 1 with the native-style
     * fatal line; review resolves through the engine (env/interactive/timeout)
     * and exits 1 when not approved. Allow/approved passes through — the
     * facade writes the single audit line for the operation.
     */

    /**
     * Render facade failures as native git fatal messages: "not a git
     * repository" and "unknown revision" have fixed native texts; exit 128.
     */
    static int nativeFatal(CliContext ctx, NopGitException e) {
        String message = e.getMessage();
        if (message != null && message.startsWith("merge: ")) {
            // native merge prints "merge: <ref> - not something we can merge"
            // without the fatal: prefix and exits 1 (audit bug #6)
            ctx.err().println(message);
            return EXIT_FAILURE;
        }
        if (message != null && message.startsWith("policy denied:")) {
            ctx.err().println("fatal: " + message);
            return EXIT_FAILURE;
        }
        if (message != null && message.contains("not a git repository")) {
            ctx.err().println("fatal: not a git repository (or any of the parent"
                    + " directories): .git");
            return EXIT_FATAL;
        }
        if (message != null && message.startsWith("unknown revision:")) {
            String rev = message.substring("unknown revision:".length()).trim();
            ctx.err().println("fatal: ambiguous argument '" + rev
                    + "': unknown revision or path not in the working tree.");
            ctx.err().println("Use '--' to separate paths from revisions, like this:");
            ctx.err().println("'git <command> [<revision>...] -- [<file>...]'");
            return EXIT_FATAL;
        }
        return ctx.fatal(message);
    }

    interface Command {
        int run(CliContext context, List<String> args);
    }

    record CliContext(PrintStream out, PrintStream err, File workDir,
                      io.nop.git.policy.IGitPolicyEngine policy) {

        IGitRepositoryService openRepo() {
            // same engine instance as the CLI guard: one configuration source
            return IGitRepositoryService.open(workDir, policy);
        }

        int fatal(String message) {
            err.println("fatal: " + message);
            return EXIT_FATAL;
        }

        int error(String message) {
            err.println("error: " + message);
            return EXIT_FAILURE;
        }
    }
}
