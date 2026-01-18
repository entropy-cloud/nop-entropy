/*
 * Copyright (c) 2025, Entropy Cloud
 *
 * Licensed to the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.ai.shell.parser;

import io.nop.ai.shell.executor.CommandData;
import io.nop.ai.shell.executor.PipeType;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Pipeline parser that parses command lines with pipes and redirections.
 * Supports: |, &&, ||, >, >>, 2>, 2>>, 1>&2, 2>&1, &>, &>>
 */
public class PipelineParser {

    /**
     * Pipe command data with pipe type and redirection info.
     */
    public static class PipeCommand {
        private final String command;
        private final List<String> args;
        private final PipeType pipeType;
        private final java.nio.file.Path redirectFile;
        private final boolean append;

        public PipeCommand(String command, List<String> args, PipeType pipeType,
                       java.nio.file.Path redirectFile, boolean append) {
            this.command = command;
            this.args = args;
            this.pipeType = pipeType;
            this.redirectFile = redirectFile;
            this.append = append;
        }

        public String command() { return command; }
        public List<String> args() { return args; }
        public PipeType pipeType() { return pipeType; }
        public java.nio.file.Path redirectFile() { return redirectFile; }
        public boolean append() { return append; }

        @Override
        public String toString() {
            return "PipeCommand{cmd='" + command + "', pipeType=" + pipeType +
                    ", redirectFile=" + redirectFile + ", args=" + args + "}";
        }
    }

    /**
     * Parse a command line and return list of pipe commands.
     *
     * @param line the command line to parse
     * @return list of pipe commands
     * @throws IllegalArgumentException if syntax is invalid
     */
    public List<PipeCommand> parse(String line) {
        List<PipeCommand> commands = new ArrayList<>();
        List<String> words = splitWords(line);

        if (words.isEmpty()) {
            return commands;
        }

        int first = 0;
        while (first < words.size()) {
            // Find pipe, redirect, or semicolon operator
            int pipeIndex = findPipeOrRedirect(words, first);

            if (pipeIndex == -1) {
                // Check for semicolon separator
                int semiIndex = findSemicolon(words, first);
                if (semiIndex != -1) {
                    // Semicolon separator - treat as NONE pipe type between commands
                    List<String> cmd1Words = words.subList(first, semiIndex);
                    if (!cmd1Words.isEmpty()) {
                        commands.add(new PipeCommand(cmd1Words.get(0),
                                cmd1Words.subList(1, cmd1Words.size()),
                                PipeType.NONE, null, false));
                    }
                    first = semiIndex + 1;
                    continue;
                }

                // No pipe, last command
                List<String> args = words.subList(first, words.size());
                if (!args.isEmpty()) {
                    commands.add(new PipeCommand(args.get(0),
                            args.subList(1, args.size()),
                            PipeType.NONE, null, false));
                }
                break;
            }

            // Extract command and arguments
            List<String> cmdWords = words.subList(first, pipeIndex);
            PipeType pipeType = identifyPipeType(words.get(pipeIndex));

            java.nio.file.Path redirectFile = null;
            boolean append = false;
            int last = pipeIndex;

            // Handle redirection
            if (pipeType == PipeType.STDOUT_REDIRECT || pipeType == PipeType.STDOUT_APPEND ||
                    pipeType == PipeType.STDOUT_REDIRECT_FD || pipeType == PipeType.STDOUT_APPEND_FD ||
                    pipeType == PipeType.STDERR_REDIRECT || pipeType == PipeType.STDERR_APPEND ||
                    pipeType == PipeType.MERGE_REDIRECT || pipeType == PipeType.MERGE_APPEND) {
                if (pipeIndex + 1 >= words.size()) {
                    throw new IllegalArgumentException(
                            "Redirect operator requires a filename");
                }
                redirectFile = extractRedirectFile(words, pipeIndex, pipeType);
                append = isAppend(pipeType);
                last = pipeIndex + 1;
            } else if (pipeType == PipeType.STDERR_TO_STDOUT || pipeType == PipeType.STDOUT_TO_STDERR) {
                // Stream merge doesn't need a file
                last = pipeIndex;
            }

            if (!cmdWords.isEmpty()) {
                String cmd = cmdWords.get(0);
                List<String> args = cmdWords.subList(1, cmdWords.size());
                commands.add(new PipeCommand(cmd, args, pipeType, redirectFile, append));
            }

            // Prepare for next command
            first = last + 1;

            // Validate conditional pipes (can't be first)
            if (pipeType == PipeType.AND || pipeType == PipeType.OR) {
                if (commands.isEmpty()) {
                    throw new IllegalArgumentException(
                            pipeType + " cannot be the first operator in a pipeline");
                }
            }
        }

        return commands;
    }

    /**
     * Split command line into words, respecting quotes and escapes.
     */
    private List<String> splitWords(String line) {
        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        char quoteChar = 0;
        boolean escape = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (escape) {
                current.append(c);
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                continue;
            }

            if (isQuoteChar(c)) {
                if (inQuote && c == quoteChar) {
                    inQuote = false;
                } else if (!inQuote) {
                    inQuote = true;
                    quoteChar = c;
                } else {
                    current.append(c);
                }
                continue;
            }

            if (Character.isWhitespace(c) && !inQuote) {
                if (current.length() > 0) {
                    words.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            current.append(c);
        }

        if (current.length() > 0) {
            words.add(current.toString());
        }

        return words;
    }

    /**
     * Find index of pipe or redirect operator.
     */
    private int findPipeOrRedirect(List<String> words, int start) {
        for (int i = start; i < words.size(); i++) {
            String word = words.get(i);
            if (isPipeOrRedirect(word)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Find index of semicolon command separator.
     */
    private int findSemicolon(List<String> words, int start) {
        for (int i = start; i < words.size(); i++) {
            String word = words.get(i);
            if (";".equals(word)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Check if word is a pipe or redirect operator.
     */
    private boolean isPipeOrRedirect(String word) {
        return word.equals("|")
                || word.equals("&&")
                || word.equals("||")
                || word.equals(">")
                || word.equals(">>")
                || word.matches("[12]>>?")
                || word.equals("&>")
                || word.equals("&>>")
                || word.matches("[12]>&[12]");
    }

    /**
     * Identify pipe type from operator string.
     */
    private PipeType identifyPipeType(String word) {
        switch (word) {
            case "|":
                return PipeType.PIPE;
            case "&&":
                return PipeType.AND;
            case "||":
                return PipeType.OR;
            case ">":
                return PipeType.STDOUT_REDIRECT;
            case ">>":
                return PipeType.STDOUT_APPEND;
            case "1>":
                return PipeType.STDOUT_REDIRECT_FD;
            case "1>>":
                return PipeType.STDOUT_APPEND_FD;
            case "2>":
                return PipeType.STDERR_REDIRECT;
            case "2>>":
                return PipeType.STDERR_APPEND;
            case "1>&2":
                return PipeType.STDOUT_TO_STDERR;
            case "2>&1":
                return PipeType.STDERR_TO_STDOUT;
            case "&>":
                return PipeType.MERGE_REDIRECT;
            case "&>>":
                return PipeType.MERGE_APPEND;
            default:
                return PipeType.NONE;
        }
    }

    /**
     * Extract redirect file path from words.
     */
    private java.nio.file.Path extractRedirectFile(List<String> words, int redirectIndex, PipeType type) {
        if (type == PipeType.STDERR_TO_STDOUT || type == PipeType.STDOUT_TO_STDERR) {
            return null;
        }

        if (redirectIndex + 1 >= words.size()) {
            throw new IllegalArgumentException(
                    "Redirect operator requires a filename");
        }

        return Paths.get(words.get(redirectIndex + 1));
    }

    /**
     * Check if pipe type is append mode.
     */
    private boolean isAppend(PipeType type) {
        switch (type) {
            case STDOUT_APPEND:
            case STDOUT_APPEND_FD:
            case STDERR_APPEND:
            case MERGE_APPEND:
                return true;
            default:
                return false;
        }
    }

    /**
     * Check if character is a quote character.
     */
    private boolean isQuoteChar(char c) {
        return c == '\'' || c == '"';
    }
}
