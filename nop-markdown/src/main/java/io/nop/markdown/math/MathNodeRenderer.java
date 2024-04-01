package io.nop.markdown.math;

import org.commonmark.node.Node;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.markdown.MarkdownNodeRendererContext;
import org.commonmark.renderer.markdown.MarkdownWriter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MathNodeRenderer implements NodeRenderer {
    private final MarkdownWriter writer;

    public MathNodeRenderer(MarkdownNodeRendererContext context) {
        this.writer = context.getWriter();
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return new HashSet<>(Arrays.asList(MathBlock.class, MathNode.class));
    }

    @Override
    public void render(Node node) {
        if (node instanceof MathBlock) {
            MathBlock block = (MathBlock) node;
            writer.raw("$$");
            writer.line();
            writer.raw(block.getLiteral());
            writer.raw("$$");
            writer.block();
        } else if (node instanceof MathNode) {
            renderMathNode((MathNode) node);
        }
    }

    private void renderMathNode(MathNode node) {
        String literal = node.getLiteral();
        writer.raw("$");
        writer.raw(literal);
        writer.raw("$");
    }
}
