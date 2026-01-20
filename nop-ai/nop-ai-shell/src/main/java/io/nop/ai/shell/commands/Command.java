package io.nop.ai.shell.commands;

import io.nop.ai.shell.registry.CommandRegistry;

/**
 * Interface for shell commands.
 * <p>
 * All commands follow Unix shell conventions:
 * - Return exit code: 0 for success, non-zero for failure
 * - Output normal results to stdout
 * - Output error messages to stderr in Unix format: "command: message"
 * - Do not throw business exceptions (only system exceptions)
 * </p>
 */
public interface Command {

    /**
     * Execute a shell command.
     *
     * @param session command session with input/output streams
     * @param args command arguments
     * @return exit code: 0 for success, non-zero for failure
     */
    int execute(CommandRegistry.CommandSession session, String[] args);
}
