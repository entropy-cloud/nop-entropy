package io.nop.ai.shell.commands;

import io.nop.ai.shell.registry.CommandRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wc command implementation.
 * Counts lines, words, and characters in files or stdin.
 */
public class WcCommand implements Command {

    @Override
    public Object execute(CommandRegistry.CommandSession session, String[] args) {
        if (args.length == 0) {
            session.err().println("wc: missing file operand");
            return 1;
        }

        int totalLines = 0;
        int totalWords = 0;
        int totalChars = 0;

        for (String arg : args) {
            if ("-".equals(arg)) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(session.in()))) {
                    List<String> lines = reader.lines().collect(Collectors.toList());
                    for (String line : lines) {
                        totalLines++;
                        totalWords += line.split("\\s+").length;
                        totalChars += line.length() + 1;
                    }
                } catch (IOException e) {
                    session.err().println("wc: read error: " + e.getMessage());
                    return 1;
                }
            } else {
                Path path = Path.of(arg);
                try {
                    String content = Files.readString(path);
                    totalLines += content.split("\n").length;
                    totalWords += content.split("\\s+").length;
                    totalChars += content.length();
                } catch (IOException e) {
                    session.err().println("wc: " + arg + ": " + e.getMessage());
                    return 1;
                }
            }
        }

        session.out().printf("  %d  %d  %d%n", totalLines, totalWords, totalChars);
        return 0;
    }
}
