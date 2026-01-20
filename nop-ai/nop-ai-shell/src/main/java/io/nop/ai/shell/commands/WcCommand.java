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
 * Counts lines, words and characters in files or stdin.
 */
public class WcCommand implements Command {

    @Override
    public int execute(CommandRegistry.CommandSession session, String[] args) {
        if (args.length == 0) {
            session.err().println("wc: missing file operand");
            return 1;
        }

        int totalLines = 0;
        int totalWords = 0;
        int totalChars = 0;

        for (String arg : args) {
            if ("-".equals(arg)) {
                WcResult result = countFromStdin(session);
                totalLines += result.lines;
                totalWords += result.words;
                totalChars += result.chars;
            } else {
                WcResult result = countFromFile(session, arg);
                if (result == null) {
                    return 1;
                }
                totalLines += result.lines;
                totalWords += result.words;
                totalChars += result.chars;
            }
        }

        session.out().printf("  %d  %d  %d%n", totalLines, totalWords, totalChars);
        return 0;
    }

    private WcResult countFromStdin(CommandRegistry.CommandSession session) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(session.in()))) {
            List<String> lines = reader.lines().collect(Collectors.toList());
            int linesCount = lines.size();
            int wordsCount = 0;
            int charsCount = 0;
            for (String line : lines) {
                wordsCount += line.split("\\s+").length;
                charsCount += line.length() + 1;
            }
            return new WcResult(linesCount, wordsCount, charsCount);
        } catch (IOException e) {
            session.err().println("wc: read error: " + e.getMessage());
            return new WcResult(0, 0, 0);
        }
    }

    private WcResult countFromFile(CommandRegistry.CommandSession session, String filename) {
        try {
            Path path = Path.of(filename);
            String content = Files.readString(path);
            int linesCount = content.split("\n").length;
            int wordsCount = content.split("\\s+").length;
            int charsCount = content.length();
            return new WcResult(linesCount, wordsCount, charsCount);
        } catch (IOException e) {
            session.err().println("wc: cannot open '" + filename + "': " + e.getMessage());
            return null;
        }
    }

    private static class WcResult {
        final int lines;
        final int words;
        final int chars;

        WcResult(int lines, int words, int chars) {
            this.lines = lines;
            this.words = words;
            this.chars = chars;
        }
    }
}
