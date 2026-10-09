package io.nop.cli.commands;

import picocli.CommandLine;

import java.io.PrintStream;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "lint",
        mixinStandardHelpOptions = true,
        description = "Lint source files with AST-based pattern matching.",
        subcommands = {CommandLine.HelpCommand.class}
)
public class CliLintCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "Lint subcommand: check|match|test")
    String subcommand;

    @CommandLine.Parameters(index = "1..*", description = "Target file or directory paths")
    String[] targets;

    @CommandLine.Option(names = {"-p", "--profile"}, defaultValue = "fast",
            description = "Lint profile: fast|standard")
    private String profile;

    @CommandLine.Option(names = {"-f", "--format"}, defaultValue = "console",
            description = "Output format: console|sarif|checkstyle-xml|json|junit-xml")
    private String format;

    @CommandLine.Option(names = {"--fix"}, description = "Apply safe fixes")
    private boolean fix;

    @CommandLine.Option(names = {"--rules"}, description = "Rule ID filter (repeatable)")
    private java.util.List<String> rules;

    @CommandLine.Option(names = {"--max-warnings"}, description = "Max allowed warnings")
    private Integer maxWarnings;

    @Override
    public Integer call() {
        PrintStream out = System.out;
        PrintStream err = System.err;
        return ToolBridge.runLint(toArgv(), out, err);
    }

    String[] toArgv() {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(subcommand != null ? subcommand : "check");
        if (targets != null) {
            for (String t : targets) {
                args.add(t);
            }
        }
        if (profile != null && !"fast".equals(profile)) {
            args.add("--profile");
            args.add(profile);
        }
        if (format != null && !"console".equals(format)) {
            args.add("--format");
            args.add(format);
        }
        if (fix) args.add("--fix");
        if (rules != null) {
            for (String r : rules) {
                args.add("--rules");
                args.add(r);
            }
        }
        if (maxWarnings != null) {
            args.add("--max-warnings");
            args.add(String.valueOf(maxWarnings));
        }
        return args.toArray(new String[0]);
    }
}
