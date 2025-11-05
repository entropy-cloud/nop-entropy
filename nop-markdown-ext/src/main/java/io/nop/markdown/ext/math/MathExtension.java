package io.nop.markdown.ext.math;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.markdown.MarkdownNodeRendererContext;
import org.commonmark.renderer.markdown.MarkdownNodeRendererFactory;
import org.commonmark.renderer.markdown.MarkdownRenderer;

import java.util.Collections;
import java.util.Set;

public class MathExtension implements Parser.ParserExtension, MarkdownRenderer.MarkdownRendererExtension {

    public static MathExtension create() {
        return new MathExtension();
    }

    @Override
    public void extend(Parser.Builder parserBuilder) {
        parserBuilder.customBlockParserFactory(new MathBlockParser.Factory());
        // parserBuilder.inlineParserFactory(new MathNodeParser.Factory());
    }

    @Override
    public void extend(MarkdownRenderer.Builder rendererBuilder) {
        rendererBuilder.nodeRendererFactory(new MarkdownNodeRendererFactory() {
            @Override
            public NodeRenderer create(MarkdownNodeRendererContext context) {
                return new MathNodeRenderer(context);
            }

            @Override
            public Set<Character> getSpecialCharacters() {
                return Collections.emptySet();
            }
        });
    }
}
