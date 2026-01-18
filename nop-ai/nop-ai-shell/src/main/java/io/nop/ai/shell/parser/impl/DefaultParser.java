package io.nop.ai.shell.parser.impl;

import io.nop.ai.shell.parser.ParsedLine;
import io.nop.ai.shell.parser.Parser;

import java.util.ArrayList;
import java.util.List;

public class DefaultParser implements Parser {

    private char[] quoteChars = {'\'', '"'};
    private char[] escapeChars = {'\\'};
    private String regexVariable = REGEX_VARIABLE;
    private String regexCommand = REGEX_COMMAND;

    public DefaultParser() {
    }

    @Override
    public ParsedLine parse(String line, int cursor, ParseContext context) {
        List<String> words = new ArrayList<>();
        StringBuilder currentWord = new StringBuilder();
        boolean inQuote = false;
        char quoteChar = 0;
        boolean escape = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (escape) {
                currentWord.append(c);
                escape = false;
                continue;
            }

            if (isEscapeChar(c) && !inQuote) {
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
                    currentWord.append(c);
                }
                continue;
            }

            if (Character.isWhitespace(c) && !inQuote) {
                if (currentWord.length() > 0) {
                    words.add(currentWord.toString());
                    currentWord.setLength(0);
                }
                continue;
            }

            currentWord.append(c);
        }

        if (currentWord.length() > 0) {
            words.add(currentWord.toString());
        }

        String word = words.isEmpty() ? "" : words.get(0);

        return new ParsedLineImpl(line, cursor, words, word);
    }

    private boolean isQuoteChar(char c) {
        for (char qc : quoteChars) {
            if (c == qc) {
                return true;
            }
        }
        return false;
    }

    private static class ParsedLineImpl implements ParsedLine {
        private final String line;
        private final int cursor;
        private final List<String> words;
        private final String word;

        ParsedLineImpl(String line, int cursor, List<String> words, String word) {
            this.line = line;
            this.cursor = cursor;
            this.words = words;
            this.word = word;
        }

        @Override
        public List<String> words() {
            return words;
        }

        @Override
        public String line() {
            return line;
        }

        @Override
        public int cursor() {
            return cursor;
        }

        @Override
        public String word() {
            return word;
        }
    }
}
