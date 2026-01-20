package io.nop.ai.shell.commands;

import io.nop.ai.shell.registry.CommandRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Sort command implementation.
 * Sorts lines from files or stdin.
 */
public class SortCommand implements Command {

    @Override
    public int execute(CommandRegistry.CommandSession session, String[] args) {
        List<String> lines = new ArrayList<>();

        if (args.length == 0 || "-".equals(args[0])) {
            int result = readFromStdin(session, lines);
            if (result != 0) {
                return result;
            }
        } else {
            int result = readFromFiles(session, args, lines);
            if (result != 0) {
                return result;
            }
        }

        lines.sort(String::compareTo);
        lines.forEach(session.out()::println);
        return 0;
    }

    private int readFromStdin(CommandRegistry.CommandSession session, List<String> lines) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(session.in()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return 0;
        } catch (IOException e) {
            session.err().println("sort: read error: " + e.getMessage());
            return 1;
        }
    }

    private int readFromFiles(CommandRegistry.CommandSession session, String[] args, List<String> lines) {
        for (String arg : args) {
            if ("-".equals(arg)) {
                session.err().println("sort: invalid argument: '-'");
                return 1;
            }
            Path path = Path.of(arg);
            try {
                Files.lines(path).forEach(lines::add);
            } catch (IOException e) {
                session.err().println("sort: cannot open '" + arg + "': " + e.getMessage());
                return 1;
            }
        }
        return 0;
    }
}
