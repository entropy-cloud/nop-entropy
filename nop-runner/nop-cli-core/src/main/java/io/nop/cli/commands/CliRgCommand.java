package io.nop.cli.commands;

import picocli.CommandLine;

import java.io.PrintStream;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "rg",
        mixinStandardHelpOptions = true,
        description = "Pure-JVM file search tool (ripgrep-compatible subset)."
)
public class CliRgCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "Search pattern (literal or regex with --regex)")
    private String pattern;

    @CommandLine.Parameters(index = "1", defaultValue = ".", description = "Root directory to search")
    private String path;

    @CommandLine.Option(names = {"-g", "--glob"}, description = "Include/exclude glob (repeatable, '!' prefix excludes)")
    private java.util.List<String> globs;

    @CommandLine.Option(names = {"-i", "--ignore-case"}, description = "Case-insensitive (ASCII folding)")
    private boolean ignoreCase;

    @CommandLine.Option(names = {"-c", "--count"}, description = "Print matching line count per file")
    private boolean count;

    @CommandLine.Option(names = {"-l", "--files-with-matches"}, description = "Print matching file paths only")
    private boolean filesWithMatches;

    @CommandLine.Option(names = "--json", description = "Output rg --json compatible messages")
    private boolean json;

    @CommandLine.Option(names = "--no-ignore", description = "Disable .gitignore filtering")
    private boolean noIgnore;

    @CommandLine.Option(names = "--threads", defaultValue = "0", description = "Worker thread count (0 = CPU cores)")
    private int threads;

    @CommandLine.Option(names = {"-r", "--regex"}, description = "Treat pattern as regular expression")
    private boolean regex;

    @CommandLine.Option(names = "--vector", description = "Use Vector API (SIMD) search when available")
    private boolean vector;

    @CommandLine.Option(names = "--delegate-rg", arity = "0..1", fallbackValue = "rg",
            description = "Delegate execution to system rg")
    private String delegateRg;

    @CommandLine.Option(names = "--jfr", arity = "1",
            description = "Record JFR events to the given .jfr file")
    private String jfrOutput;

    @Override
    public Integer call() {
        PrintStream out = System.out;
        PrintStream err = System.err;
        return ToolBridge.runRg(toArgv(), out, err);
    }

    private String[] toArgv() {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(pattern != null ? pattern : "");
        args.add(path != null ? path : ".");
        if (globs != null) {
            for (String g : globs) {
                args.add("-g");
                args.add(g);
            }
        }
        if (ignoreCase) args.add("-i");
        if (count) args.add("-c");
        if (filesWithMatches) args.add("-l");
        if (json) args.add("--json");
        if (noIgnore) args.add("--no-ignore");
        if (threads > 0) {
            args.add("--threads");
            args.add(String.valueOf(threads));
        }
        if (regex) args.add("-r");
        if (vector) args.add("--vector");
        if (jfrOutput != null) {
            args.add("--jfr");
            args.add(jfrOutput);
        }
        if (delegateRg != null) {
            args.add("--delegate-rg");
        }
        return args.toArray(new String[0]);
    }
}
