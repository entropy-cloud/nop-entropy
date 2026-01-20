package io.nop.ai.shell.commands;

import io.nop.ai.shell.registry.CommandRegistry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Tail command implementation.
 * Outputs last N lines of files or stdin.
 */
public class TailCommand implements Command {

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
                session.err().println("tail: invalid line count: " + args[0]);
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
        List<String> allLines = new ArrayList<>();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(session.in()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                allLines.add(line);
            }
        } catch (java.io.IOException e) {
            session.err().println("tail: read error: " + e.getMessage());
            return 1;
        }
        int start = Math.max(0, allLines.size() - lines);
        for (int i = start; i < allLines.size(); i++) {
            session.out().println(allLines.get(i));
        }
        return 0;
    }

    private int readFromFiles(CommandRegistry.CommandSession session, String[] args, int argIndex, int lines) {
        for (int i = argIndex; i < args.length; i++) {
            Path path = Path.of(args[i]);
            try {
                List<String> fileLines = new ArrayList<>();
                java.nio.file.Files.lines(path).forEach(fileLines::add);
                int start = Math.max(0, fileLines.size() - lines);
                for (int j = start; j < fileLines.size(); j++) {
                    session.out().println(fileLines.get(j));
                }
            } catch (java.io.IOException e) {
                session.err().println("tail: cannot open '" + args[i] + "': " + e.getMessage());
                return 1;
            }
        }
        return 0;
    }
}
