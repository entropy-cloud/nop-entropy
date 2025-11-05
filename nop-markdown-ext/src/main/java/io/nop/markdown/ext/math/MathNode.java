package io.nop.markdown.ext.math;

import org.commonmark.node.CustomNode;

public class MathNode extends CustomNode {
    private String literal;

    public MathNode() {
    }

    public MathNode(String literal) {
        this.literal = literal;
    }

    public String getLiteral() {
        return literal;
    }

    public void setLiteral(String literal) {
        this.literal = literal;
    }
}
