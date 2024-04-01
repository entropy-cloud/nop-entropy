package io.nop.markdown.math;

import org.commonmark.node.CustomBlock;

public class MathBlock extends CustomBlock {
    private String literal;

    public String getLiteral() {
        return literal;
    }

    public void setLiteral(String literal) {
        this.literal = literal;
    }
}
