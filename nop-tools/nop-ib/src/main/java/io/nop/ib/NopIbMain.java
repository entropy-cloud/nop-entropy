package io.nop.ib;

import io.nop.ib.cli.Commands;
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
            List.of("status", "plan", "explain", "model", "cache");

    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        if (code != 0)
            System.exit(code);
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
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
        List<String> positional = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("--json".equals(arg)) {
                json = true;
            } else if ("--allow-partial".equals(arg)) {
                allowPartial = true;
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
            IbEngine engine = new IbEngine(Paths.get("").toAbsolutePath());
            switch (cmd) {
                case "model":
                    return Commands.model(engine, positional, out, json);
                case "status":
                    return Commands.status(engine, out, json);
                case "plan":
                    return Commands.plan(engine, positional, allowPartial, out, json);
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
