package io.nop.ib.cli;

import io.nop.ib.compile.MavenFallback;
import io.nop.ib.engine.IbEngine;
import io.nop.ib.json.JsonOut;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wave 11 item 2 (owner adjudication 2026-11-17): whitelisted real-Maven
 * passthrough. Every build operation should flow through the service's
 * per-project lane — a bare {@code mvn install} outside the service is the
 * remaining same-project concurrency trampling surface. This command keeps
 * the "don't replace the build system" semantics (real Maven runs, exit code
 * passed through verbatim) while the caller stays inside the queue.
 *
 * <p>Whitelist: {@code install | package | clean | dependency:* | surefire:*}.
 * Anything else (deploy, release, site, ...) is rejected with the usage exit
 * code 3 and the allowed set is echoed — passthrough must not become an
 * unguarded remote-mvn endpoint.
 *
 * <p>Goal detection: a non-flag token is a goal when it is one of the exact
 * whitelist phases, or carries a plugin prefix before ':' (dependency:*,
 * surefire:*). Reactor selectors like {@code :module-a} (the value of -pl)
 * start with ':' and are never goals.
 */
public final class MvnCommand {
    public static final Set<String> WHITELIST_EXACT = Set.of("install", "package", "clean");
    public static final List<String> WHITELIST_PREFIXES = List.of("dependency:", "surefire:");
    public static final String ALLOWED_DESC = "install | package | clean | dependency:* | surefire:*";
    /** summary passthrough: only the last N lines of Maven output survive. */
    public static final int TAIL_LINES = 50;

    /** injectable seam for pins: pins swap this so no real install ever runs. */
    public interface Runner {
        int run(Path repoRoot, List<String> args, Appendable out, Appendable err);
    }

    /** pin seam: swap the default MavenFallback delegation (tests only). */
    public static Runner runner = (repoRoot, args, out, err) ->
            new MavenFallback(repoRoot).delegate(args, out, err);

    /**
     * Wave 12 defect fix (coblab-rpg observation 2026-11-19): {@code surefire:test}
     * is a DIRECT goal invocation — it runs whatever target/test-classes already
     * contain and never compiles, so a newly added or edited test source is
     * silently absent from the run (fake green: the module reports exit 0 with a
     * stale test count). The {@code test}/{@code check} commands close this with
     * the wave-11 pre-compile (TestCommand wave 11, owner directive 2026-11-17);
     * the passthrough must honor the same zero-fake-green rule. Before delegating
     * any {@code surefire:*} goal, run the SAME incremental compile channel
     * ({@link CompileCommand#runCompile}) over the -pl module scope (or the repo
     * change set when no -pl is given). {@code install}/{@code package} compile
     * themselves and {@code clean} deletes, so they need no pre-compile.
     * A red pre-compile ends the passthrough with the compile exit code — no
     * dispatch on stale classes.
     */
    public interface PreCompileFn {
        int run(Path repoRoot, String plModules, PrintWriter out, PrintWriter err);
    }

    /** pin seam: swap the default incremental-compile pre-step (tests only). */
    public static PreCompileFn preCompileFn = MvnCommand::defaultPreCompile;

    static int defaultPreCompile(Path repoRoot, String plModules,
                                 PrintWriter out, PrintWriter err) {
        IbEngine engine = new IbEngine(repoRoot);
        RunLog log = RunLog.start(repoRoot, "mvn");
        try {
            int code = CompileCommand.runCompile(engine, null, plModules,
                    "maven", false, false, out, err, log);
            log.exitCode(code);
            return code;
        } finally {
            log.close();
        }
    }

    /** -pl value from the passthrough args, or null when absent. */
    static String plModulesOf(List<String> mvnArgs) {
        for (int i = 0; i < mvnArgs.size(); i++) {
            String arg = mvnArgs.get(i);
            if (arg.equals("-pl") && i + 1 < mvnArgs.size())
                return mvnArgs.get(i + 1);
            if (arg.startsWith("-pl="))
                return arg.substring(4);
        }
        return null;
    }

    static boolean hasSurefireGoal(List<String> mvnArgs) {
        for (String arg : mvnArgs)
            if (!arg.startsWith("-") && arg.startsWith("surefire:"))
                return true;
        return false;
    }

    private MvnCommand() {
    }

    public static boolean isWhitelistedGoal(String token) {
        if (WHITELIST_EXACT.contains(token))
            return true;
        for (String prefix : WHITELIST_PREFIXES)
            if (token.startsWith(prefix))
                return true;
        return false;
    }

    public static int mvn(Path repoRoot, List<String> mvnArgs, boolean json,
                          PrintStream out, PrintStream err) {
        List<String> goals = new ArrayList<>();
        for (String arg : mvnArgs) {
            if (arg.startsWith("-") || arg.startsWith(":"))
                continue; // flags and -pl reactor selectors are not goals
            if (!isWhitelistedGoal(arg)) {
                err.println("usage error: mvn goal '" + arg + "' is not whitelisted for the "
                        + "service passthrough (allowed: " + ALLOWED_DESC
                        + "); run it manually outside nop-ib if really needed");
                return Commands.EXIT_USAGE;
            }
            goals.add(arg);
        }
        if (hasSurefireGoal(mvnArgs)) {
            java.io.StringWriter preOut = new java.io.StringWriter();
            java.io.StringWriter preErr = new java.io.StringWriter();
            int compileExit = preCompileFn.run(repoRoot, plModulesOf(mvnArgs),
                    new PrintWriter(preOut, true), new PrintWriter(preErr, true));
            for (String line : preOut.toString().split("\n"))
                if (!line.isBlank())
                    out.println(line);
            String preErrText = preErr.toString().trim();
            if (!preErrText.isEmpty())
                err.println(preErrText);
            if (compileExit != Commands.EXIT_OK) {
                out.println("[nop-ib] surefire passthrough blocked: pre-compile failed (exit "
                        + compileExit + ") — no dispatch on stale classes");
                return compileExit;
            }
        }
        StringBuilder buf = new StringBuilder();
        StringBuilder errBuf = new StringBuilder();
        int exit = runner.run(repoRoot, mvnArgs, buf, errBuf);
        String output = buf.toString();
        List<String> tail = tailLines(output, TAIL_LINES);
        if (json) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cmd", "mvn");
            m.put("args", mvnArgs);
            m.put("exit", exit);
            m.put("outputTail", tail);
            m.put("truncated", countLines(output) > TAIL_LINES);
            out.println(JsonOut.write(m));
        } else {
            tail.forEach(out::println);
            String stderrText = errBuf.toString().trim();
            if (!stderrText.isEmpty())
                err.println(stderrText);
        }
        return exit;
    }

    static List<String> tailLines(String text, int n) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            lines.add(line);
        }
        while (!lines.isEmpty() && lines.getLast().isEmpty())
            lines.removeLast();
        int from = Math.max(0, lines.size() - n);
        return lines.subList(from, lines.size());
    }

    static int countLines(String text) {
        int n = 0;
        for (String line : text.split("\n", -1))
            if (!line.isEmpty())
                n++;
        return n;
    }
}
