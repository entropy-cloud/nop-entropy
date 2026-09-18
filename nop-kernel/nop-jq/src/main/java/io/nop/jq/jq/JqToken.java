package io.nop.jq.jq;

/**
 * A token produced by the jq lexer.
 */
public class JqToken {
    private final JqTokenType type;
    private final String text;
    private final int position;

    public JqToken(JqTokenType type, String text, int position) {
        this.type = type;
        this.text = text;
        this.position = position;
    }

    public JqTokenType getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public int getPosition() {
        return position;
    }

    public int intValue() {
        return Integer.parseInt(text);
    }

    public long longValue() {
        return Long.parseLong(text);
    }

    public double doubleValue() {
        return Double.parseDouble(text);
    }

    @Override
    public String toString() {
        return type + "('" + text + "')";
    }
}
