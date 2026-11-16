package io.nop.ib;

import io.nop.ib.cli.BenchCommand;
import io.nop.ib.cli.CompileCommand;
import io.nop.ib.cli.Commands;
import io.nop.ib.cli.ServeCommand;
import io.nop.ib.cli.TestCommand;
import io.nop.ib.engine.IbEngine;

import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI entry point for the nop-ib incremental build engine.
 *
 * <p>Wave 1 wires the read-only surface (model/status/plan/explain/cache).
 * compile/test/check/serve/bench land with Waves 2-4 and still fail loudly here.
 * Exit codes: 0 ok; 1 compile/test failure; 2 fallback rejected; 3 usage error;
 * 6 partial; 75 lock timeout (design 05 §九).
 */
public class NopIbMain {
    public static final String VERSION = "0.1.0";

    public static final String[] COMMANDS = {
            "status", "plan", "compile", "test", "check",
            "explain", "model", "serve", "cache", "bench"
    };

    private static final List<String> IMPLEMENTED =
            List.of("status", "plan", "explain", "model", "cache", "compile", "test", "check",
                    "serve", "bench");

    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        if (code != 0)
            System.exit(code);
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        // CLI entry: repo root discovered from the process cwd (unchanged behavior)
        return run(args, Paths.get("").toAbsolutePath(), out, err);
    }

    /**
     * Wave 10 root-aware entry (design 07 B1): the service layer calls this with
     * the requested projectDir's repo root — the service process cwd is NOT the
     * target project. Identical semantics otherwise; the CLI overload delegates
     * here with the cwd so one code path serves both.
     */
    public static int run(String[] args, java.nio.file.Path repoRoot, PrintStream out, PrintStream err) {
        return run(args, null, repoRoot, out, err);
    }

    /**
     * Engine-injecting entry (wave 10 IB-S6): the service layer passes a cached
     * IbEngine per repo root; the CLI overloads pass null and the engine is
     * constructed here, preserving per-invocation discovery semantics.
     */
    public static int run(String[] args, IbEngine injectedEngine, java.nio.file.Path repoRoot,
                          PrintStream out, PrintStream err) {
        if (args.length == 0) {
            usage(err);
            return 3;
        }
        String cmd = args[0];
        if ("--version".equals(cmd) || "-v".equals(cmd)) {
            out.println("nop-ib " + VERSION);
            return 0;
        }
        if ("--help".equals(cmd) || "-h".equals(cmd)) {
            usage(out);
            return 0;
        }
        if (!List.of(COMMANDS).contains(cmd)) {
            err.println("usage error: unknown command '" + cmd + "'");
            return 3;
        }
        if (!IMPLEMENTED.contains(cmd)) {
            err.println("not yet implemented: command '" + cmd
                    + "' lands in a later wave (see ai-dev/backlog/nop-ib-roadmap.md)");
            return 3;
        }

        // parse common flags
        boolean json = false;
        boolean allowPartial = false;
        String onFallback = "maven";
        String plModules = null;
        boolean rerunFailed = false;
        String runner = "junit";
        boolean useCache = false;
        boolean forceRun = false;
        List<String> explicitClasses = new ArrayList<>();
        List<String> positional = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("--json".equals(arg)) {
                json = true;
            } else if ("--allow-partial".equals(arg)) {
                allowPartial = true;
            } else if ("--on-fallback".equals(arg) && i + 1 < args.length) {
                onFallback = args[++i];
            } else if (arg.startsWith("--on-fallback=")) {
                onFallback = arg.substring("--on-fallback=".length());
            } else if ("-pl".equals(arg) && i + 1 < args.length) {
                // repeated -pl flags accumulate (wave6 6C-4): `test -pl a -pl b`
                // now equals `-pl a,b` — matching mvn's multi-flag semantics
                plModules = plModules == null ? args[++i] : plModules + "," + args[++i];
            } else if ("--class".equals(arg) && i + 1 < args.length) {
                explicitClasses.add(args[++i]);
            } else if ("--affected".equals(arg)) {
                // default selection mode; explicit for readability
            } else if ("--rerun-failed".equals(arg)) {
                rerunFailed = true;
            } else if ("--runner".equals(arg) && i + 1 < args.length) {
                runner = args[++i];
            } else if ("--cache".equals(arg)) {
                // wave 9: opt-in green-result cache (see TestCacheStore)
                useCache = true;
            } else if ("--force".equals(arg)) {
                forceRun = true;
            } else if ("--fork-parallelism".equals(arg)) {
                // legacy flag: one-fork-per-class execution was replaced by the
                // persistent worker pool (design 06) — reject loudly instead of
                // silently ignoring
                err.println("usage error: --fork-parallelism removed (worker pool replaced per-class forks; use nop.ib.workerBudget)");
                return 3;
            } else if ("--paths".equals(arg)) {
                // --paths <comma-separated> or remaining positional args
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    for (String p : args[++i].split(","))
                        if (!p.isBlank())
                            positional.add(p.trim());
                }
            } else {
                positional.add(arg);
            }
        }

        try {
            IbEngine engine = injectedEngine != null ? injectedEngine : new IbEngine(repoRoot);
            switch (cmd) {
                case "model":
                    return Commands.model(engine, positional, out, json);
                case "status":
                    return Commands.status(engine, out, json);
                case "plan":
                    return Commands.plan(engine, positional, allowPartial, out, json);
                case "compile":
                    return CompileCommand.compile(engine, positional, plModules, onFallback,
                            allowPartial, json,
                            new java.io.PrintWriter(out, true), new java.io.PrintWriter(err, true));
                case "test":
                case "check":
                    return TestCommand.test(engine, positional, plModules, explicitClasses,
                            allowPartial, json, "check".equals(cmd), onFallback, rerunFailed, runner,
                            useCache, forceRun,
                            new java.io.PrintWriter(out, true), new java.io.PrintWriter(err, true));
                case "serve":
                    return ServeCommand.serve(engine, positional, out, err);
                case "bench":
                    return BenchCommand.bench(engine, positional, json, out, err);
                case "explain":
                    if (positional.isEmpty()) {
                        err.println("usage error: explain <file|module>");
                        return 3;
                    }
                    return Commands.explain(engine, positional.get(0), out, json);
                case "cache":
                    return Commands.cache(engine, positional, out, json);
                default:
                    err.println("not yet implemented: command '" + cmd + "'");
                    return 3;
            }
        } catch (IllegalArgumentException e) {
            err.println("usage/config error: " + String.valueOf(e));
            return 3;
        }
    }

    private static void usage(PrintStream out) {
        out.println("usage: nop-ib <command> [options]");
        out.println("commands: " + String.join(" | ", COMMANDS));
        out.println("common options: --json, --allow-partial, --paths <paths>");
        out.println("implemented (wave 1): status plan explain model cache");
        out.println("try 'nop-ib --version'");
    }
}
