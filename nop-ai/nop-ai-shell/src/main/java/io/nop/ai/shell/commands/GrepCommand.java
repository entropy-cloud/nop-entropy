package io.nop.ai.shell.commands;

import io.nop.ai.shell.registry.CommandRegistry;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Grep command implementation.
 * Searches for patterns in files or stdin using regular expressions.
 */
public class GrepCommand implements Command {

    private Pattern regex;

    @Override
    public int execute(CommandRegistry.CommandSession session, String[] args) {
        if (args.length < 2) {
            session.err().println("usage: grep PATTERN [FILE...]");
            return 1;
        }

        String pattern = args[0];
        int fileIndex = 1;
        boolean caseInsensitive = false;

        if (pattern.startsWith("-i")) {
            caseInsensitive = true;
            pattern = args[1];
            fileIndex = 2;
        } else if (pattern.startsWith("-v")) {
            session.err().println("grep: option '-v' not implemented");
            return 1;
        }

        if (pattern.startsWith("-") && !pattern.startsWith("-i")) {
            session.err().println("grep: invalid option: " + pattern);
            return 1;
        }

        try {
            if (caseInsensitive) {
                regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            } else {
                regex = Pattern.compile(pattern);
            }

            if (args.length <= fileIndex) {
                return searchStdin(session);
            } else {
                return searchFiles(session, args, fileIndex);
            }
        } catch (Exception e) {
            session.err().println("grep: " + e.getMessage());
            return 1;
        }
    }

    private int searchStdin(CommandRegistry.CommandSession session) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(session.in()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (regex.matcher(line).find()) {
                    session.out().println(line);
                }
            }
            return 0;
        } catch (java.io.IOException e) {
            session.err().println("grep: read error: " + e.getMessage());
            return 1;
        }
    }

    private int searchFiles(CommandRegistry.CommandSession session, String[] args, int fileIndex) {
        for (int i = fileIndex; i < args.length; i++) {
            if ("-".equals(args[i])) {
                continue;
            }
            String filePath = args[i];
            try {
                List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Path.of(filePath));
                for (String line : lines) {
                    if (regex.matcher(line).find()) {
                        session.out().println(line);
                    }
                }
            } catch (java.io.IOException e) {
                session.err().println("grep: cannot open '" + filePath + "': " + e.getMessage());
                return 1;
            }
        }
        return 0;
    }
}
