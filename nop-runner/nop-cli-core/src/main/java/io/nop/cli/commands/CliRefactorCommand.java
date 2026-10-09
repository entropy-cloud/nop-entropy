package io.nop.cli.commands;

import picocli.CommandLine;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Bridges {@code nop-refactor}'s batch form (preview|apply) into nop-cli.
 *
 * <p>{@code NopRefactorCli} owns its own fail-closed argument parsing
 * ({@code RefactorOptions.parse}), so this command mirrors that grammar exactly —
 * {@code preview|apply --rules <vfs-prefix> [--json] <file|dir>...} — and
 * re-emits the arguments in the order the engine expects. Options precede
 * positionals in the engine contract, so they are rebuilt ahead of the targets.
 */
@CommandLine.Command(
        name = "refactor",
        mixinStandardHelpOptions = true,
        description = "Batch refactoring with AST-based pattern matching."
)
public class CliRefactorCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "Refactor mode: preview|apply")
    String mode;

    @CommandLine.Option(names = {"--rules"}, required = true,
            description = "Ruleset VFS prefix (the verified safe surface)")
    String rules;

    @CommandLine.Option(names = {"--json"}, description = "Output JSON format")
    boolean json;

    @CommandLine.Parameters(index = "1..*", description = "Target file or directory paths")
    String[] targets;

    @Override
    public Integer call() {
        return ToolBridge.runRefactor(toArgv(), System.out, System.err);
    }

    String[] toArgv() {
        List<String> args = new ArrayList<>();
        args.add(mode);
        args.add("--rules");
        args.add(rules);
        if (json) {
            args.add("--json");
        }
        if (targets != null) {
            for (String target : targets) {
                args.add(target);
            }
        }
        return args.toArray(new String[0]);
    }
}