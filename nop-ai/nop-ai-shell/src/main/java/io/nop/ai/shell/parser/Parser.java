package io.nop.ai.shell.parser;

public interface Parser {
    String REGEX_VARIABLE = "[a-zA-Z_]+[a-zA-Z0-9_-]*";
    String REGEX_COMMAND = "[:]?[a-zA-Z]+[a-zA-Z0-9_-]*";

    ParsedLine parse(String line, int cursor, ParseContext context);

    default ParsedLine parse(String line, int cursor) {
        return parse(line, cursor, ParseContext.UNSPECIFIED);
    }

    default boolean isEscapeChar(char ch) {
        return ch == '\\';
    }

    default boolean validCommandName(String name) {
        return name != null && name.matches(REGEX_COMMAND);
    }

    default boolean validVariableName(String name) {
        return name != null && name.matches(REGEX_VARIABLE);
    }

    default String getCommand(String line) {
        String out;
        java.util.regex.Pattern patternCommand = java.util.regex.Pattern.compile("^\\s*" + REGEX_VARIABLE + "=(" + REGEX_COMMAND + ")(\\s+|$)");
        java.util.regex.Matcher matcher = patternCommand.matcher(line);
        if (matcher.find()) {
            out = matcher.group(1);
        } else {
            out = line.trim().split("\\s+")[0];
            if (!out.matches(REGEX_COMMAND)) {
                out = "";
            }
        }
        return out;
    }

    default String getVariable(String line) {
        String out = null;
        java.util.regex.Pattern patternCommand = java.util.regex.Pattern.compile("^\\s*(" + REGEX_VARIABLE + ")\\s*=[^=~].*");
        java.util.regex.Matcher matcher = patternCommand.matcher(line);
        if (matcher.find()) {
            out = matcher.group(1);
        }
        return out;
    }

    enum ParseContext {
        UNSPECIFIED,
        ACCEPT_LINE,
        SPLIT_LINE,
        COMPLETE,
        SECONDARY_PROMPT
    }
}
