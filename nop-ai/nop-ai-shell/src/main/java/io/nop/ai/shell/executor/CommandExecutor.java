package io.nop.ai.shell.executor;

import io.nop.ai.shell.parser.ParsedLine;
import io.nop.ai.shell.parser.Parser;
import io.nop.ai.shell.registry.CommandRegistry;
import io.nop.ai.shell.registry.impl.BuiltInCommandRegistry;
import io.nop.ai.shell.script.ScriptEngine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class CommandExecutor {

    private final Parser parser;
    private final CommandRegistry commandRegistry;
    private final ScriptEngine scriptEngine;
    private final PipeExecutor pipeExecutor;

    public CommandExecutor(Parser parser, CommandRegistry commandRegistry, ScriptEngine scriptEngine) {
        this.parser = parser;
        this.commandRegistry = commandRegistry;
        this.scriptEngine = scriptEngine;
        this.pipeExecutor = new PipeExecutor(commandRegistry, scriptEngine);
    }

    public CommandExecutor(Parser parser) {
        this(parser, new BuiltInCommandRegistry(), null);
    }

    /**
     * Execute a command line. This method detects if the command contains
     * pipeline operators (|, &&, ||, >, >>, 2>, 2>>, etc.) and routes
     * to the appropriate executor.
     *
     * @param commandLine the command line to execute
     * @return execution result with combined stdout/stderr and exit code
     */
    public ExecutionResult execute(String commandLine) {
        // Check if command line contains pipeline operators
        if (hasPipes(commandLine)) {
            return pipeExecutor.execute(commandLine);
        }

        // Use original single command execution logic
        try {
            ParsedLine parsed = parser.parse(commandLine, 0, Parser.ParseContext.ACCEPT_LINE);
            List<String> words = parsed.words();

            if (words.isEmpty()) {
                return new ExecutionResult("", "", 0);
            }

            ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

            PrintStream stdout = new PrintStream(stdoutBuffer);
            PrintStream stderr = new PrintStream(stderrBuffer);

            String command = words.get(0);
            List<String> args = words.subList(1, words.size());

            InputStream in = new ByteArrayInputStream(new byte[0]);
            CommandRegistry.CommandSession session =
                    new CommandRegistry.CommandSession(in, stdout, stderr);

            Object result = executeCommand(session, command, args.toArray());
            String output = stdoutBuffer.toString();
            String error = stderrBuffer.toString();

            return new ExecutionResult(output, error,
                    result instanceof Number ? ((Number) result).intValue() : 0);

        } catch (Exception e) {
            return new ExecutionResult("", "", 1, e);
        }
    }

    /**
     * Check if the command line contains pipeline or redirection operators.
     *
     * @param line the command line to check
     * @return true if the line contains pipes or redirects
     */
    private boolean hasPipes(String line) {
        return line.contains("|") || line.contains("&&")
                || line.contains("||") || line.contains(">")
                || line.matches(".*\\b[12]>.*");
    }

    private Object executeCommand(CommandRegistry.CommandSession session, String command, Object[] args) throws Exception {
        if (commandRegistry.hasCommand(command)) {
            return commandRegistry.invoke(session, command, args);
        } else if (scriptEngine != null) {
            String statement = buildStatement(command, args);
            return scriptEngine.execute(statement);
        } else {
            session.err().println("Unknown command: " + command);
            return 1;
        }
    }

    private String buildStatement(String command, Object[] args) {
        StringBuilder sb = new StringBuilder();
        sb.append(command);
        for (Object arg : args) {
            sb.append(" ").append(escapeArg(arg.toString()));
        }
        return sb.toString();
    }

    private String escapeArg(String arg) {
        if (arg.contains(" ") || arg.contains("\t")) {
            return "\"" + arg.replace("\"", "\\\"") + "\"";
        }
        return arg;
    }

    public void shutdown() {
    }
}
