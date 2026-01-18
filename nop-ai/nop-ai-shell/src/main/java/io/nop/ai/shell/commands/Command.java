package io.nop.ai.shell.commands;

import io.nop.ai.shell.registry.CommandRegistry;

/**
 * Interface for shell commands.
 */
public interface Command {
    Object execute(CommandRegistry.CommandSession session, String[] args);
}
