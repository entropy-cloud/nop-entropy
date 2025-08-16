package io.nop.markdown;

import io.nop.commons.collections.MutableIntArray;
import io.nop.commons.text.MutableString;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.markdown.math.MathExtension;
import io.nop.markdown.math.MathNode;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.Renderer;
import org.commonmark.renderer.markdown.MarkdownRenderer;

import java.io.File;
import java.util.Arrays;

public class MarkdownNormalizer {

    protected Parser buildParser() {
        Parser parser = Parser.builder()
                .extensions(Arrays.asList(TablesExtension.create(), MathExtension.create()))
                .build();
        return parser;
    }

    protected Renderer buildRenderer() {
        MarkdownRenderer.Builder builder = MarkdownRenderer.builder()
                .extensions(Arrays.asList(TablesExtension.create(), MathExtension.create()));
        return builder.build();
    }

    public String normalizeText(String text) {
        Parser parser = buildParser();
        Node document = parser.parse(text);
        document.accept(newNormalizeVisitor());
        String normalized = buildRenderer().render(document);
        return normalized;
    }

    public void normalizeResource(IResource resource) {
        String text = ResourceHelper.readText(resource);
        text = normalizeText(text);
        ResourceHelper.writeText(resource, text);
    }

    public void normalizeDir(File dir) {
        File[] subFiles = dir.listFiles();
        if (subFiles != null) {
            for (File subFile : subFiles) {
                if (subFile.isDirectory()) {
                    normalizeDir(subFile);
                } else if (subFile.getName().endsWith(".md")) {
                    normalizeResource(new FileResource(subFile));
                }
            }
        }
    }

    protected NormalizeVisitor newNormalizeVisitor() {
        return new NormalizeVisitor();
    }

    protected static class NormalizeVisitor extends AbstractVisitor {
        MutableIntArray levels = new MutableIntArray();
        boolean first = true;

        @Override
        public void visit(Heading heading) {
            if (first) {
                levels.add(heading.getLevel());
                heading.setLevel(1);
                first = false;
            } else {
                int level = heading.getLevel();
                if (level == 1) {
                    level = 2;
                }

                while (levels.size() > 1) {
                    if (level <= levels.size()) {
                        levels.pop();
                    } else {
                        break;
                    }
                }

                levels.push(level);
                heading.setLevel(levels.size());
            }
            super.visit(heading);
        }

        @Override
        public void visit(FencedCodeBlock fencedCodeBlock) {
            fencedCodeBlock.setOpeningFenceLength(3);
            fencedCodeBlock.setClosingFenceLength(3);
            super.visit(fencedCodeBlock);
        }

        @Override
        public void visit(Text text) {
            String literal = text.getLiteral();
            int pos = literal.indexOf('$');
            if (pos >= 0 && literal.length() > pos + 2 && literal.indexOf('$', pos + 1) > 0) {
                normalizeMathNode(text);
                text.unlink();
            } else {
                super.visit(text);
            }
        }

        protected void normalizeMathNode(Text text) {
            String literal = text.getLiteral();
            TextScanner sc = TextScanner.fromString(null, literal);
            Node prev = text;
            do {
                MutableString buf = sc.getReusableBuffer();
                if (sc.nextUntilUnescaped('$', sc::appendToBuf)) {
                    sc.next();
                    if (!buf.isEmpty()) {
                        Text newNode = new Text(buf.toString());
                        prev.insertAfter(newNode);
                        prev = newNode;
                    }

                    buf = sc.getReusableBuffer();
                    if (sc.nextUntilUnescaped('$', sc::appendToBuf)) {
                        MathNode newNode = new MathNode(buf.toString());
                        prev.insertAfter(newNode);
                        prev = newNode;
                        buf.clear();
                        sc.next();
                    }
                }
                if (!buf.isEmpty()) {
                    Text newNode = new Text(buf.toString());
                    prev.insertAfter(newNode);
                    prev = newNode;
                }
            } while (!sc.isEnd());
        }
    }
}
