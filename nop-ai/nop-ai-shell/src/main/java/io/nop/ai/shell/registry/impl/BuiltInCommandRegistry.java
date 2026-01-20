package io.nop.ai.shell.registry.impl;

import io.nop.ai.shell.commands.Builtins;
import io.nop.ai.shell.commands.Command;
import io.nop.ai.shell.commands.GrepCommand;
import io.nop.ai.shell.commands.HeadCommand;
import io.nop.ai.shell.commands.LsCommand;
import io.nop.ai.shell.commands.SortCommand;
import io.nop.ai.shell.commands.TailCommand;
import io.nop.ai.shell.commands.WcCommand;
import io.nop.ai.shell.registry.CommandRegistry;

import java.util.Set;

/**
 * Default implementation of CommandRegistry with built-in commands.
 * <p>
 * Provides a comprehensive set of built-in shell commands including
 * file operations, text processing, and system utilities.
 * All commands are self-contained and do not depend on terminal features.
 * </p>
 */
public class BuiltInCommandRegistry extends AbstractCommandRegistry {

    public BuiltInCommandRegistry() {
        registerBuiltInCommands();
    }

    private void registerBuiltInCommands() {
        registerCommand("cat");
        registerCommand("echo");
        registerCommand("pwd");
        registerCommand("date");
        registerCommand("sleep");
        registerCommand("wc");
        registerCommand("head");
        registerCommand("tail");
        registerCommand("clear");
        registerCommand("ls");
        registerCommand("sort");
        registerCommand("grep");
        registerCommand("cd");
    }

    @Override
    public int invoke(CommandSession session, String command, Object... args) {
        switch (command) {
            case "cat":
                return Builtins.cat(session, toStringArray(args));
            case "echo":
                return Builtins.echo(session, toStringArray(args));
            case "pwd":
                return Builtins.pwd(session, toStringArray(args));
            case "date":
                return Builtins.date(session, toStringArray(args));
            case "sleep":
                return Builtins.sleep(session, toStringArray(args));
            case "wc":
                return new WcCommand().execute(session, toStringArray(args));
            case "head":
                return new HeadCommand().execute(session, toStringArray(args));
            case "tail":
                return new TailCommand().execute(session, toStringArray(args));
            case "clear":
                return Builtins.clear(session, toStringArray(args));
            case "ls":
                return new LsCommand().execute(session, toStringArray(args));
            case "sort":
                return new SortCommand().execute(session, toStringArray(args));
            case "grep":
                return new GrepCommand().execute(session, toStringArray(args));
            case "cd":
                return Builtins.cd(session, toStringArray(args));
            default:
                session.err().println("nop: unknown command: " + command);
                return 1;
        }
    }

    @Override
    public Set<String> commandNames() {
        return Set.of(
                "cat", "echo", "pwd", "date", "sleep",
                "wc", "head", "tail", "clear",
                "ls", "sort", "grep", "cd"
        );
    }

    private String[] toStringArray(Object... args) {
        if (args == null) {
            return new String[0];
        }
        String[] result = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            result[i] = args[i] != null ? args[i].toString() : "";
        }
        return result;
    }
}
