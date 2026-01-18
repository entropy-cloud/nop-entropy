/*
 * Copyright (c) 2025, Entropy Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.nop.ai.shell.registry.impl;

import io.nop.ai.shell.commands.Builtins;
import io.nop.ai.shell.registry.CommandRegistry;

import java.util.HashMap;
import java.util.Map;
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
    public Object invoke(CommandSession session, String command, Object... args) throws Exception {
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
                return Builtins.wc(session, toStringArray(args));
            case "head":
                return Builtins.head(session, toStringArray(args));
            case "tail":
                return Builtins.tail(session, toStringArray(args));
            case "clear":
                return Builtins.clear(session, toStringArray(args));
            case "ls":
                return Builtins.ls(session, toStringArray(args));
            case "sort":
                return Builtins.sort(session, toStringArray(args));
            case "grep":
                return Builtins.grep(session, toStringArray(args));
            case "cd":
                return Builtins.cd(session, toStringArray(args));
            default:
                session.err().println("Unknown command: " + command);
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
