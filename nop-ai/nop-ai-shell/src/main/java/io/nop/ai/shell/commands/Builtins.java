package io.nop.ai.shell.commands;

import io.nop.ai.shell.registry.CommandRegistry;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Built-in shell commands.
 * Simple commands are kept in this class.
 * Complex commands are extracted to separate Command classes.
 */
public class Builtins {

    private Builtins() {}

    public static Object cat(CommandRegistry.CommandSession session, String[] args) {
        if (args.length == 0 || "-".equals(args[0])) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(session.in()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    session.out().println(line);
                }
            } catch (java.io.IOException e) {
                session.err().println("cat: read error: " + e.getMessage());
                return 1;
            }
        } else {
            for (String arg : args) {
                if ("-".equals(arg)) {
                    session.err().println("cat: " + arg + ": invalid argument");
                    return 1;
                }
                java.nio.file.Path path = java.nio.file.Path.of(arg);
                try {
                    java.nio.file.Files.lines(path).forEach(session.out()::println);
                } catch (java.io.IOException e) {
                    session.err().println("cat: " + arg + ": " + e.getMessage());
                    return 1;
                }
            }
        }
        return 0;
    }

    public static Object echo(CommandRegistry.CommandSession session, String[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(args[i]);
        }
        session.out().println(sb);
        return 0;
    }

    public static Object pwd(CommandRegistry.CommandSession session, String[] args) {
        session.out().println(".");
        return 0;
    }

    public static Object date(CommandRegistry.CommandSession session, String[] args) {
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

    public static Object sleep(CommandRegistry.CommandSession session, String[] args) {
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

    public static Object clear(CommandRegistry.CommandSession session, String[] args) {
        session.out().print("\u001B[2J");
        session.out().flush();
        return 0;
    }

    public static Object cd(CommandRegistry.CommandSession session, String[] args) {
        if (args.length == 0) {
            String home = System.getProperty("user.home");
            if (home == null) {
                session.err().println("cd: HOME not set");
                return 1;
            }
            session.out().println(java.nio.file.Path.of(home));
            return 0;
        } else if (args.length == 1) {
            String target = args[0];
            java.nio.file.Path newDir = java.nio.file.Path.of(target).normalize();
            session.out().println(newDir);
            return 0;
        } else {
            session.err().println("cd: too many arguments");
            return 1;
        }
    }
}
