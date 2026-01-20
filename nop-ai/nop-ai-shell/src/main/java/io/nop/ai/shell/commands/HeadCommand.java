package io.nop.ai.shell.commands;

import io.nop.ai.shell.registry.CommandRegistry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Head command implementation.
 * Outputs first N lines of files or stdin.
 */
public class HeadCommand implements Command {

    private static final int DEFAULT_LINES = 10;

    @Override
    public int execute(CommandRegistry.CommandSession session, String[] args) {
        int lines = DEFAULT_LINES;
        int argIndex = 0;

        if (args.length > 0 && args[0].startsWith("-n") && args.length > 1) {
            try {
                lines = Integer.parseInt(args[0].substring(2));
                argIndex = 1;
            } catch (NumberFormatException e) {
                session.err().println("head: invalid line count: " + args[0]);
                return 1;
            }
        }

        if (args.length <= argIndex) {
            return readFromStdin(session, lines);
        } else {
            return readFromFiles(session, args, argIndex, lines);
        }
    }

    private int readFromStdin(CommandRegistry.CommandSession session, int lines) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(session.in()))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < lines) {
                session.out().println(line);
                count++;
            }
            return 0;
        } catch (java.io.IOException e) {
            session.err().println("head: read error: " + e.getMessage());
            return 1;
        }
    }

    private int readFromFiles(CommandRegistry.CommandSession session, String[] args, int argIndex, int lines) {
        for (int i = argIndex; i < args.length; i++) {
            Path path = Path.of(args[i]);
            try {
                List<String> fileLines = new ArrayList<>();
                java.nio.file.Files.lines(path).forEach(fileLines::add);
                int count = Math.min(lines, fileLines.size());
                for (int j = 0; j < count; j++) {
                    session.out().println(fileLines.get(j));
                }
            } catch (java.io.IOException e) {
                session.err().println("head: cannot open '" + args[i] + "': " + e.getMessage());
                return 1;
            }
        }
        return 0;
    }
}
