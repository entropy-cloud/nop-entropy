package io.nop.ai.shell.commands;

import io.nop.ai.shell.registry.CommandRegistry;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Abstract base class for shell commands that read from stdin.
 */
public abstract class AbstractStdinCommand implements Command {

    /**
     * Read all lines from stdin.
     */
    protected List<String> readFromStdin(CommandRegistry.CommandSession session) throws IOException {
        return new BufferedReader(new InputStreamReader(session.in()))
                .lines()
                .collect(java.util.stream.Collectors.toList());
    }
}
