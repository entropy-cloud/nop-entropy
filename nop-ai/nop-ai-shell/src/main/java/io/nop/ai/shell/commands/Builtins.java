package io.nop.ai.shell.commands;

import io.nop.ai.shell.registry.CommandRegistry.CommandSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Builtins {

    private Builtins() {}

    public static Object cat(CommandSession session, String[] args) {
        if (args.length == 0 || "-".equals(args[0])) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(session.in()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    session.out().println(line);
                }
            } catch (IOException e) {
                session.err().println("cat: read error: " + e.getMessage());
                return 1;
            }
        } else {
            for (String arg : args) {
                if ("-".equals(arg)) {
                    session.err().println("cat: " + arg + ": invalid argument");
                    return 1;
                }
                Path path = Path.of(arg);
                try {
                    Files.lines(path).forEach(session.out()::println);
                } catch (IOException e) {
                    session.err().println("cat: " + arg + ": " + e.getMessage());
                    return 1;
                }
            }
        }
        return 0;
    }

    public static Object echo(CommandSession session, String[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(args[i]);
        }
        session.out().println(sb);
        return 0;
    }

    public static Object pwd(CommandSession session, String[] args) {
        session.out().println(".");
        return 0;
    }

    public static Object date(CommandSession session, String[] args) {
        SimpleDateFormat formatter;
        if (args.length > 0 && args[0].startsWith("+")) {
            String format = args[0].substring(1);
            formatter = new SimpleDateFormat(format);
        } else {
            formatter = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy");
        }
        session.out().println(formatter.format(new Date()));
        return 0;
    }

    public static Object sleep(CommandSession session, String[] args) {
        if (args.length != 1) {
            session.err().println("usage: sleep seconds");
            return 1;
        }
        try {
            int seconds = Integer.parseInt(args[0]);
            Thread.sleep(seconds * 1000L);
            return 0;
        } catch (NumberFormatException e) {
            session.err().println("sleep: invalid number: " + args[0]);
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 1;
        }
    }

    public static Object wc(CommandSession session, String[] args) {
        if (args.length == 0) {
            session.err().println("wc: missing file operand");
            return 1;
        }

        int totalLines = 0;
        int totalWords = 0;
        int totalChars = 0;

        for (String arg : args) {
            if ("-".equals(arg)) {
                try {
                    List<String> lines = new BufferedReader(new InputStreamReader(session.in()))
                            .lines()
                            .collect(java.util.stream.Collectors.toList());
                    for (String line : lines) {
                        totalLines++;
                        totalWords += line.split("\\s+").length;
                        totalChars += line.length() + 1;
                    }
                } catch (Exception e) {
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

    public static Object head(CommandSession session, String[] args) {
        int lines = 10;
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
            List<String> allLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(session.in()))) {
                String line;
                for (int i = 0; i < lines && (reader.readLine() != null); i++) {
                    session.out().println(reader.readLine());
                }
            } catch (IOException e) {
                session.err().println("head: read error: " + e.getMessage());
                return 1;
            }
        } else {
            for (int i = argIndex; i < args.length; i++) {
                Path path = Path.of(args[i]);
                try {
                    List<String> fileLines = new ArrayList<>();
                    Files.lines(path).forEach(fileLines::add);
                    int count = Math.min(lines, fileLines.size());
                    for (int j = 0; j < count; j++) {
                        session.out().println(fileLines.get(j));
                    }
                } catch (IOException e) {
                    session.err().println("head: " + args[i] + ": " + e.getMessage());
                    return 1;
                }
            }
        }
        return 0;
    }

    public static Object tail(CommandSession session, String[] args) {
        int lines = 10;
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
            List<String> allLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(session.in()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    allLines.add(line);
                }
                int start = Math.max(0, allLines.size() - lines);
                for (int i = start; i < allLines.size(); i++) {
                    session.out().println(allLines.get(i));
                }
            } catch (IOException e) {
                session.err().println("tail: read error: " + e.getMessage());
                return 1;
            }
        } else {
            for (int i = argIndex; i < args.length; i++) {
                Path path = Path.of(args[i]);
                try {
                    List<String> fileLines = new ArrayList<>();
                    Files.lines(path).forEach(fileLines::add);
                    int start = Math.max(0, fileLines.size() - lines);
                    for (int j = start; j < fileLines.size(); j++) {
                        session.out().println(fileLines.get(j));
                    }
                } catch (IOException e) {
                    session.err().println("tail: " + args[i] + ": " + e.getMessage());
                    return 1;
                }
            }
        }
        return 0;
    }

    public static Object clear(CommandSession session, String[] args) {
        session.out().print("\u001B[2J");
        session.out().flush();
        return 0;
    }

    public static Object ls(CommandSession session, String[] args) {
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

    public static Object sort(CommandSession session, String[] args) {
        List<String> lines = new ArrayList<>();

        if (args.length == 0 || "-".equals(args[0])) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(session.in()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            } catch (IOException e) {
                session.err().println("sort: read error: " + e.getMessage());
                return 1;
            }
        } else {
            for (String arg : args) {
                if ("-".equals(arg)) {
                    session.err().println("sort: " + arg + ": invalid argument");
                    return 1;
                }
                Path path = Path.of(arg);
                try {
                    Files.lines(path).forEach(lines::add);
                } catch (IOException e) {
                    session.err().println("sort: " + arg + ": " + e.getMessage());
                    return 1;
                }
            }
        }

        lines.sort(String::compareTo);
        lines.forEach(session.out()::println);
        return 0;
    }

    public static Object grep(CommandSession session, String[] args) {
        if (args.length < 2) {
            session.err().println("usage: grep PATTERN [FILE...]");
            return 1;
        }

        String pattern = args[0];
        boolean caseInsensitive = false;

        int fileIndex = 1;
        if (pattern.startsWith("-i")) {
            caseInsensitive = true;
            pattern = args[1];
            fileIndex = 2;
        } else if (pattern.startsWith("-v")) {
            session.err().println("grep: -v not implemented");
            return 1;
        }

        if (pattern.startsWith("-") && !pattern.startsWith("-i")) {
            session.err().println("grep: " + pattern + ": invalid option");
            return 1;
        }

        try {
            java.util.regex.Pattern regex;
            if (caseInsensitive) {
                regex = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
            } else {
                regex = java.util.regex.Pattern.compile(pattern);
            }

            if (args.length <= fileIndex) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(session.in()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (regex.matcher(line).find()) {
                        session.out().println(line);
                    }
                }
            } else {
                for (int i = fileIndex; i < args.length; i++) {
                    if ("-".equals(args[i])) {
                        continue;
                    }
                    String filePath = args[i];
                    try {
                        java.util.List<String> lines = Files.readAllLines(Path.of(filePath));
                        for (String line : lines) {
                            if (regex.matcher(line).find()) {
                                session.out().println(line);
                            }
                        }
                    } catch (IOException e) {
                        session.err().println("grep: " + filePath + ": " + e.getMessage());
                        return 1;
                    }
                }
            }
        } catch (Exception e) {
            session.err().println("grep: " + e.getMessage());
            return 1;
        }
        return 0;
    }

    public static Object cd(CommandSession session, String[] args) {
        if (args.length == 0) {
            String home = System.getProperty("user.home");
            if (home == null) {
                session.err().println("cd: HOME not set");
                return 1;
            }
            session.out().println(Path.of(home));
            return 0;
        } else if (args.length == 1) {
            String target = args[0];
            Path newDir = Path.of(target).normalize();
            session.out().println(newDir);
            return 0;
        } else {
            session.err().println("cd: too many arguments");
            return 1;
        }
    }
}
