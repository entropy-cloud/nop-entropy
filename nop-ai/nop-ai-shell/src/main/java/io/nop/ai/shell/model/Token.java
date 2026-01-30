package io.nop.ai.shell.model;

/**
 * 词法单元 - 表示bash命令行的一个token
 */
public final class Token {

    private final TokenType type;
    private final String value;
    private final int position;

    public Token(TokenType type, String value, int position) {
        this.type = type;
        this.value = value;
        this.position = position;
    }

    public TokenType type() {
        return type;
    }

    public String value() {
        return value;
    }

    public int position() {
        return position;
    }

    public boolean isPipe() {
        return type == TokenType.PIPE;
    }

    public boolean isOperator() {
        return type == TokenType.AND || type == TokenType.OR || type == TokenType.SEMICOLON;
    }

    public boolean isParen() {
        return type == TokenType.LEFT_PAREN || type == TokenType.RIGHT_PAREN;
    }

    public boolean isBrace() {
        return type == TokenType.LEFT_BRACE || type == TokenType.RIGHT_BRACE;
    }

    public boolean isBackground() {
        return type == TokenType.BACKGROUND;
    }

    @Override
    public String toString() {
        return value;
    }
}
