package io.nop.ai.shell.executor;

import io.nop.ai.shell.parser.Parser;
import io.nop.ai.shell.registry.CommandRegistry;
import io.nop.ai.shell.registry.impl.BuiltInCommandRegistry;
import io.nop.ai.shell.script.ScriptEngine;

public class CommandExecutor {

    private final CommandRegistry commandRegistry;
    private final ScriptEngine scriptEngine;
    private final ShellCommandExecutor shellCommandExecutor;

    public CommandExecutor(Parser parser, CommandRegistry commandRegistry, ScriptEngine scriptEngine) {
        this.commandRegistry = commandRegistry;
        this.scriptEngine = scriptEngine;
        this.shellCommandExecutor = new ShellCommandExecutor(commandRegistry, scriptEngine);
    }

    public CommandExecutor(Parser parser) {
        this.commandRegistry = new BuiltInCommandRegistry();
        this.scriptEngine = null;
        this.shellCommandExecutor = new ShellCommandExecutor(commandRegistry, scriptEngine);
    }

    /**
     * Execute a command line.
     *
     * @param commandLine command line to execute
     * @return execution result with combined stdout/stderr and exit code
     */
    public ExecutionResult execute(String commandLine) {
        return shellCommandExecutor.execute(commandLine);
    }

    public void shutdown() {
    }
}
