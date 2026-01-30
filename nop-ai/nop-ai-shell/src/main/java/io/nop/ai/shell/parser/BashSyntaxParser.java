package io.nop.ai.shell.parser;

import io.nop.ai.shell.model.*;

import java.util.ArrayList;
import java.util.List;

public class BashSyntaxParser {

    private final List<Token> tokens;
    private int current;

    public BashSyntaxParser(String input) {
        BashLexer lexer = new BashLexer(input);
        this.tokens = lexer.tokenize();
        this.current = 0;
    }

    public CommandExpression parse() {
        CommandExpression result = parseSequence();
        return result;
    }

    private CommandExpression parseSequence() {
        return parseExpression(0);
    }

    private CommandExpression parseExpression(int minPrecedence) {
        CommandExpression left = parsePrimary();

        while (true) {
            Token token = peek();

            if (token == null || token.type() == TokenType.EOF) {
                break;
            }

            if (token.type() == TokenType.PIPE) {
                if (1 < minPrecedence) {
                    break;
                }
                consume();
                CommandExpression right = parsePrimaryForPipeline();
                if (right == null) {
                    throw new ParseException("Expected command after pipe", getCurrentPosition());
                }
                List<CommandExpression> commands = new ArrayList<>();
                if (left instanceof PipelineExpr) {
                    commands.addAll(((PipelineExpr) left).commands());
                } else {
                    commands.add(left);
                }
                commands.add(right);
                PipelineExpr.Builder builder = PipelineExpr.builder();
                for (CommandExpression cmd : commands) {
                    builder.command(cmd);
                }
                left = builder.build();
                continue;
            }

            LogicalExpr.Operator op = tryMatchLogicalOperator(token);
            if (op == null) {
                break;
            }

            if (op.precedence() < minPrecedence) {
                break;
            }

            consume();
            CommandExpression right = parseExpression(op.precedence() + 1);
            left = new LogicalExpr(left, op, right);
        }

        return left;
    }

    private CommandExpression parsePrimary() {
        Token token = peek();

        if (token == null || token.type() == TokenType.EOF) {
            throw new ParseException("Unexpected end of input", getCurrentPosition());
        }

        if (token.type() == TokenType.LEFT_PAREN) {
            CommandExpression subshell = parseSubshell();
            List<Redirect> redirects = parseTrailingRedirects();
            if (peek() != null && peek().type() == TokenType.BACKGROUND) {
                consume();
                if (!redirects.isEmpty()) {
                    return new BackgroundExpr(new SubshellExpr(((SubshellExpr) subshell).inner(), redirects));
                }
                return new BackgroundExpr(subshell);
            }
            if (!redirects.isEmpty()) {
                return new SubshellExpr(((SubshellExpr) subshell).inner(), redirects);
            }
            return subshell;
        }

        if (token.type() == TokenType.LEFT_BRACE) {
            CommandExpression group = parseGroup();
            List<Redirect> redirects = parseTrailingRedirects();
            if (peek() != null && peek().type() == TokenType.BACKGROUND) {
                consume();
                if (!redirects.isEmpty()) {
                    GroupExpr g = (GroupExpr) group;
                    GroupExpr.Builder builder = GroupExpr.builder();
                    for (CommandExpression cmd : g.commands()) {
                        builder.command(cmd);
                    }
                    for (Redirect redirect : redirects) {
                        builder.redirect(redirect);
                    }
                    return new BackgroundExpr(builder.build());
                }
                return new BackgroundExpr(group);
            }
            if (!redirects.isEmpty()) {
                GroupExpr g = (GroupExpr) group;
                GroupExpr.Builder builder = GroupExpr.builder();
                for (CommandExpression cmd : g.commands()) {
                    builder.command(cmd);
                }
                for (Redirect redirect : redirects) {
                    builder.redirect(redirect);
                }
                return builder.build();
            }
            return group;
        }

        CommandExpression cmd = parseSimpleCommand();

        if (peek() != null && peek().type() == TokenType.PIPE) {
            return parsePipeline(cmd);
        }

        if (peek() != null && peek().type() == TokenType.BACKGROUND) {
            consume();
            return new BackgroundExpr(cmd);
        }

        return cmd;
    }

    private List<Redirect> parseTrailingRedirects() {
        List<Redirect> redirects = new ArrayList<>();
        while (peek() != null && isRedirectToken(peek())) {
            redirects.add(parseRedirect());
        }
        return redirects;
    }

    private CommandExpression parsePrimaryForPipeline() {
        Token token = peek();

        if (token == null || token.type() == TokenType.EOF) {
            throw new ParseException("Unexpected end of input", getCurrentPosition());
        }

        if (token.type() == TokenType.LEFT_PAREN) {
            return parseSubshell();
        }

        if (token.type() == TokenType.LEFT_BRACE) {
            return parseGroup();
        }

        return parseSimpleCommand();
    }

    private CommandExpression parseSubshell() {
        consume();
        CommandExpression inner = parseSequence();

        Token rightParen = consume();
        if (rightParen == null || rightParen.type() != TokenType.RIGHT_PAREN) {
            throw new ParseException("Expected ')'", getCurrentPosition());
        }

        return new SubshellExpr(inner, List.of());
    }

    private CommandExpression parseGroup() {
        consume();

        List<CommandExpression> commands = new ArrayList<>();
        List<Redirect> redirects = new ArrayList<>();

        while (peek() != null && peek().type() != TokenType.RIGHT_BRACE) {
            if (isRedirectToken(peek())) {
                redirects.add(parseRedirect());
            } else {
                CommandExpression cmd = parseExpression(3);
                commands.add(cmd);
                Token token = peek();
                if (token != null && token.type() == TokenType.SEMICOLON) {
                    consume();
                }
            }
        }

        consume();

        GroupExpr.Builder builder = GroupExpr.builder();
        for (CommandExpression cmd : commands) {
            builder.command(cmd);
        }
        for (Redirect redirect : redirects) {
            builder.redirect(redirect);
        }
        return builder.build();
    }

    private CommandExpression parseSimpleCommand() {
        CommandExpression base = parseBaseCommand();

        if (base == null) {
            return null;
        }

        List<Redirect> redirects = new ArrayList<>();

        while (peek() != null) {
            if (isRedirectToken(peek())) {
                redirects.add(parseRedirect());
            } else if (peek().type() == TokenType.ARGUMENT && peek().value().matches("\\d+")) {
                Token fdToken = consume();
                Token nextToken = peek();
                if (nextToken != null && nextToken.type().name().startsWith("REDIRECT_")) {
                    redirects.add(parseRedirectWithFd(fdToken));
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        if (redirects.isEmpty()) {
            return base;
        }

        if (base instanceof SimpleCommand) {
            SimpleCommand sc = (SimpleCommand) base;
            SimpleCommand.Builder builder = SimpleCommand.builder(sc.getCommand());
            for (String arg : sc.getArgs()) {
                builder.arg(arg);
            }
            for (EnvVar envVar : sc.getEnvVars()) {
                builder.envVar(envVar);
            }
            for (Redirect redirect : redirects) {
                builder.redirect(redirect);
            }
            return builder.build();
        }

        return base;
    }

    private Redirect parseRedirectWithFd(Token fdToken) {
        int fd = Integer.parseInt(fdToken.value());

        Token redirectToken = consume();

        if (redirectToken == null) {
            throw new ParseException("Expected redirect operator after file descriptor", getCurrentPosition());
        }

        String value = redirectToken.value();
        Redirect.Type type;

        switch (value) {
            case ">":
                type = Redirect.Type.OUTPUT;
                break;
            case ">>":
                type = Redirect.Type.APPEND;
                break;
            case "<":
                type = Redirect.Type.INPUT;
                break;
            case ">&":
                type = Redirect.Type.FD_OUTPUT;
                break;
            case "<&":
                type = Redirect.Type.FD_INPUT;
                break;
            case "&>":
                type = Redirect.Type.MERGE;
                break;
            case "&>>":
                type = Redirect.Type.MERGE_APPEND;
                break;
            case "<<":
                type = Redirect.Type.HERE_DOC;
                break;
            case "<<<":
                type = Redirect.Type.HERE_STRING;
                break;
            default:
                throw new ParseException("Unknown redirect operator: " + value, getCurrentPosition());
        }

        Token targetToken = peek();
        if (targetToken == null || !isCommandOrArgument(targetToken)) {
            throw new ParseException("Expected redirect target", getCurrentPosition());
        }

        String target = consume().value();
        return new Redirect(fd, type, target);
    }

    private CommandExpression parseBaseCommand() {
        List<EnvVar> envVars = new ArrayList<>();
        List<String> args = new ArrayList<>();

        while (peek() != null && isEnvAssignment()) {
            envVars.add(parseEnvAssignment());
        }

        Token commandToken = peek();
        if (commandToken == null || !isCommandOrArgument(commandToken)) {
            throw new ParseException("Expected command", getCurrentPosition());
        }

        String command = consume().value();

        while (peek() != null && isCommandOrArgument(peek())) {
            Token argToken = consume();

            if (argToken.type() == TokenType.QUOTED_SINGLE) {
                args.add(unquoteSingle(argToken.value()));
            } else if (argToken.type() == TokenType.QUOTED_DOUBLE) {
                args.add(unquoteDouble(argToken.value()));
            } else {
                args.add(argToken.value());
            }
        }

        SimpleCommand.Builder builder = SimpleCommand.builder(command);
        for (EnvVar envVar : envVars) {
            builder.envVar(envVar);
        }
        for (String arg : args) {
            builder.arg(arg);
        }
        return builder.build();
    }

    private String unquoteSingle(String value) {
        if (value.length() >= 2 && value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String unquoteDouble(String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private CommandExpression parsePipeline(CommandExpression first) {
        List<CommandExpression> commands = new ArrayList<>();
        commands.add(first);

        while (peek() != null && peek().type() == TokenType.PIPE) {
            consume();
            CommandExpression next = parsePrimaryForPipeline();
            if (next != null) {
                commands.add(next);
            }
        }

        if (commands.size() < 2) {
            return first;
        }

        PipelineExpr.Builder builder = PipelineExpr.builder();
        for (CommandExpression cmd : commands) {
            builder.command(cmd);
        }
        return builder.build();
    }

    private LogicalExpr.Operator tryMatchLogicalOperator(Token token) {
        if (token == null) return null;

        switch (token.type()) {
            case AND:
                return LogicalExpr.Operator.AND;
            case OR:
                return LogicalExpr.Operator.OR;
            case SEMICOLON:
                return LogicalExpr.Operator.SEMICOLON;
            default:
                return null;
        }
    }

    private boolean isRedirectToken(Token token) {
        if (token == null) return false;

        switch (token.type()) {
            case REDIRECT_OUTPUT:
            case REDIRECT_APPEND:
            case REDIRECT_INPUT:
            case REDIRECT_FD_OUTPUT:
            case REDIRECT_FD_INPUT:
            case REDIRECT_MERGE:
            case REDIRECT_MERGE_APPEND:
            case REDIRECT_HERE_DOC:
            case REDIRECT_HERE_STRING:
                return true;
            default:
                return false;
        }
    }

    private boolean isCommandOrArgument(Token token) {
        if (token == null) return false;
        return token.type() == TokenType.COMMAND ||
               token.type() == TokenType.ARGUMENT ||
               token.type() == TokenType.QUOTED_SINGLE ||
               token.type() == TokenType.QUOTED_DOUBLE;
    }

    private boolean isEnvAssignment() {
        if (peek() == null) return false;

        Token token = peek();
        String value = token.value();

        if (value.equals("export")) {
            if (current + 1 < tokens.size()) {
                Token nextToken = tokens.get(current + 1);
                return nextToken.value().contains("=");
            }
            return false;
        }

        return value.contains("=");
    }

    private Token peek() {
        if (current >= tokens.size()) {
            return null;
        }
        return tokens.get(current);
    }

    private Token consume() {
        if (current >= tokens.size()) {
            return null;
        }
        return tokens.get(current++);
    }

    private int getCurrentPosition() {
        if (current >= tokens.size()) {
            return tokens.size() - 1;
        }
        if (current == 0) {
            return 0;
        }
        return tokens.get(current - 1).position();
    }

    private EnvVar parseEnvAssignment() {
        boolean isExport = false;

        if (peek() != null && peek().value().equals("export")) {
            isExport = true;
            consume();
        }

        Token token = consume();
        if (token == null) {
            throw new ParseException("Expected environment variable assignment", getCurrentPosition());
        }

        String value = token.value();
        int eqPos = value.indexOf('=');

        if (eqPos == -1) {
            throw new ParseException("Invalid environment variable assignment", getCurrentPosition());
        }

        String name = value.substring(0, eqPos);
        String val = value.substring(eqPos + 1);

        if (isExport) {
            return EnvVar.export(name, val);
        }

        return EnvVar.local(name, val);
    }

    private Redirect parseRedirect() {
        Token redirectToken = consume();

        if (redirectToken == null) {
            throw new ParseException("Expected redirect operator", getCurrentPosition());
        }

        Integer fd = null;
        String value = redirectToken.value();

        if (redirectToken.type() == TokenType.REDIRECT_FD_OUTPUT ||
            redirectToken.type() == TokenType.REDIRECT_FD_INPUT ||
            redirectToken.type() == TokenType.REDIRECT_MERGE ||
            redirectToken.type() == TokenType.REDIRECT_MERGE_APPEND) {
            if (value.matches("\\d+[>&<]")) {
                fd = Integer.parseInt(value.substring(0, value.length() - 2));
                value = value.substring(value.length() - 2);
            }
        }

        Redirect.Type type;

        switch (value) {
            case ">":
                type = Redirect.Type.OUTPUT;
                break;
            case ">>":
                type = Redirect.Type.APPEND;
                break;
            case "<":
                type = Redirect.Type.INPUT;
                break;
            case ">&":
                type = Redirect.Type.FD_OUTPUT;
                break;
            case "<&":
                type = Redirect.Type.FD_INPUT;
                break;
            case "&>":
                type = Redirect.Type.MERGE;
                break;
            case "&>>":
                type = Redirect.Type.MERGE_APPEND;
                break;
            case "<<":
                type = Redirect.Type.HERE_DOC;
                break;
            case "<<<":
                type = Redirect.Type.HERE_STRING;
                break;
            default:
                throw new ParseException("Unknown redirect operator: " + value, getCurrentPosition());
        }

        Token targetToken = peek();
        if (targetToken == null || !isCommandOrArgument(targetToken)) {
            throw new ParseException("Expected redirect target", getCurrentPosition());
        }

        String target = consume().value();
        return new Redirect(fd, type, target);
    }

    public static class ParseException extends RuntimeException {
        private final int position;

        public ParseException(String message, int position) {
            super(message + " at position " + position);
            this.position = position;
        }

        public int getPosition() {
            return position;
        }
    }
}
