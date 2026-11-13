package io.nop.ib;

import java.io.IOException;
import java.io.PrintStream;

/**
 * CLI entry point for the nop-ib incremental build engine.
 *
 * <p>Wave 0 skeleton: only {@code --version} and usage are wired. The command surface
 * (status/plan/compile/test/check/explain/model/serve/cache/bench, design 05 §九) lands with
 * Wave 1; any known-but-unimplemented command fails loudly instead of silently no-op'ing.
 */
public class NopIbMain {
    public static final String VERSION = "0.1.0";

    /**
     * Fixed command surface per design 05 §九 / IB-D12.
     */
    public static final String[] COMMANDS = {
            "status", "plan", "compile", "test", "check",
            "explain", "model", "serve", "cache", "bench"
    };

    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        if (code != 0)
            System.exit(code);
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0) {
            usage(err);
            return 3; // usage/config error, design 05 §九
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
        // Wave 1 implements the command surface; until then every command is an explicit failure.
        err.println("not yet implemented: command '" + cmd
                + "' lands in Wave 1 (see ai-dev/backlog/nop-ib-roadmap.md)");
        return 3;
    }

    private static void usage(PrintStream out) {
        out.println("usage: nop-ib <command> [options]");
        out.println("commands: " + String.join(" | ", COMMANDS));
        out.println("try 'nop-ib --version'");
    }
}
