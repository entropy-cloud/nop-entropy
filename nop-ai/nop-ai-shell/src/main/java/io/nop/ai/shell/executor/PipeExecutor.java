/*
 * Copyright (c) 2025, Entropy Cloud
 *
 * Licensed to the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.ai.shell.executor;

import io.nop.ai.shell.parser.PipelineParser;
import io.nop.ai.shell.registry.CommandRegistry;
import io.nop.ai.shell.script.ScriptEngine;

import java.io.PrintStream;
import java.util.List;

/**
 * Pipeline executor that handles pipe chains and stream redirections.
 * Supports: |, &&, ||, >, >>, 2>, 2>>, 1>&2, 2>&1, &>, &>>
 */
public class PipeExecutor {

    private final CommandRegistry commandRegistry;
    private final ScriptEngine scriptEngine;
    private final PipelineParser pipelineParser;

    public PipeExecutor(CommandRegistry commandRegistry, ScriptEngine scriptEngine) {
        this.commandRegistry = commandRegistry;
        this.scriptEngine = scriptEngine;
        this.pipelineParser = new PipelineParser();
    }

    /**
     * Execute a command line that may contain pipes.
     *
     * @param commandLine the command line to execute
     * @return execution result with combined stdout/stderr and exit code
     */
    public ExecutionResult execute(String commandLine) {
        try {
            List<PipelineParser.PipeCommand> commands = pipelineParser.parse(commandLine);

            if (commands.isEmpty()) {
                return new ExecutionResult("", "", 0);
            }

            PipeOutputContext context = new PipeOutputContext();
            String finalStdout = "";
            String finalStderr = "";
            int finalExitCode = 0;

            for (PipelineParser.PipeCommand cmd : commands) {
                // Setup stream redirection based on pipe type
                setupRedirections(context, cmd);

                // Execute command
                int exitCode = executeCommand(context, cmd);

                // Get output
                String stdout = context.getStdout();
                String stderr = context.getStderr();

                // Accumulate error output
                if (!stderr.isEmpty()) {
                    finalStderr += stderr;
                }

                // Check pipe type
                PipeType pipeType = cmd.pipeType();

                if (pipeType == PipeType.NONE) {
                    // Last command
                    finalStdout = stdout;
                    finalExitCode = exitCode;
                    break;
                }

                // Check if should continue execution
                if (!context.shouldContinue(pipeType, exitCode)) {
                    // Conditional pipe, stop execution and capture current output
                    finalStdout = stdout;
                    finalExitCode = exitCode;
                    break;
                }

                // Prepare next command's input
                if (pipeType == PipeType.PIPE) {
                    context.prepareNextCommandInput();
                }
            }

            context.close();
            return new ExecutionResult(finalStdout, finalStderr, finalExitCode);

        } catch (Exception e) {
            return new ExecutionResult("", "", 1, e);
        }
    }

    /**
     * Setup stream redirections based on pipe type.
     */
    private void setupRedirections(PipeOutputContext context, PipelineParser.PipeCommand cmd) 
            throws Exception {
        java.nio.file.Path redirectFile = cmd.redirectFile();
        
        switch (cmd.pipeType()) {
            case STDERR_TO_STDOUT:
                context.redirectStderrToStdout();
                break;
            case STDOUT_TO_STDERR:
                context.redirectStdoutToStderr();
                break;
            case STDOUT_REDIRECT:
                if (redirectFile != null) {
                    context.redirectStdout(redirectFile, false);
                }
                break;
            case STDOUT_APPEND:
                if (redirectFile != null) {
                    context.redirectStdout(redirectFile, true);
                }
                break;
            case STDOUT_REDIRECT_FD:
                if (redirectFile != null) {
                    context.redirectStdout(redirectFile, false);
                }
                break;
            case STDOUT_APPEND_FD:
                if (redirectFile != null) {
                    context.redirectStdout(redirectFile, true);
                }
                break;
            case STDERR_REDIRECT:
                if (redirectFile != null) {
                    context.redirectStderr(redirectFile, false);
                }
                break;
            case STDERR_APPEND:
                if (redirectFile != null) {
                    context.redirectStderr(redirectFile, true);
                }
                break;
            case MERGE_REDIRECT:
                if (redirectFile != null) {
                    context.redirectAllToFile(redirectFile, false);
                }
                break;
            case MERGE_APPEND:
                if (redirectFile != null) {
                    context.redirectAllToFile(redirectFile, true);
                }
                break;
            default:
                // Use default in-memory buffers
                break;
        }
    }

    /**
     * Execute a single command with given context.
     */
    private int executeCommand(PipeOutputContext context, PipelineParser.PipeCommand cmd)
            throws Exception {

        // Create session
        CommandRegistry.CommandSession session = new CommandRegistry.CommandSession(
            context.getPipeInput(),
            new PrintStream(context.getPipeOutput()),
            new PrintStream(context.getPipeError())
        );

        // Execute command
        Object result;
        if (commandRegistry.hasCommand(cmd.command())) {
            result = commandRegistry.invoke(session, cmd.command(),
                                        cmd.args().toArray());
        } else if (scriptEngine != null) {
            String statement = buildStatement(cmd);
            result = scriptEngine.execute(statement);
        } else {
            session.err().println("Unknown command: " + cmd.command());
            return 1;
        }

        // Convert result to exit code
        if (result instanceof Number) {
            return ((Number) result).intValue();
        }
        return 0;
    }

    /**
     * Build a script statement from command and args.
     */
    private String buildStatement(PipelineParser.PipeCommand cmd) {
        StringBuilder sb = new StringBuilder();
        sb.append(cmd.command());
        for (String arg : cmd.args()) {
            sb.append(" ").append(escapeArg(arg));
        }
        return sb.toString();
    }

    /**
     * Escape an argument for shell processing.
     */
    private String escapeArg(String arg) {
        if (arg.contains(" ") || arg.contains("\t")) {
            return "\"" + arg.replace("\"", "\\\"") + "\"";
        }
        return arg;
    }
}
