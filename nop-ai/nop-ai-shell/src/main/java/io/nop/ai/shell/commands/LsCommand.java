package io.nop.ai.shell.commands;

import io.nop.ai.shell.registry.CommandRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ls command implementation.
 * Lists directory contents.
 */
public class LsCommand implements Command {

    @Override
    public Object execute(CommandRegistry.CommandSession session, String[] args) {
        Path dir = Path.of(".");
        if (args.length > 0) {
            dir = Path.of(args[0]);
        }

        if (!Files.isDirectory(dir)) {
            session.err().println("ls: " + dir + ": No such file or directory");
            return 1;
        }

        try {
            Files.list(dir).forEach(path -> session.out().println(path.getFileName().toString()));
        } catch (IOException e) {
            session.err().println("ls: " + e.getMessage());
            return 1;
        }
        return 0;
    }
}
