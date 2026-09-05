package io.nop.markdown.ext;

import io.nop.commons.collections.MutableIntArray;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.markdown.ext.math.MathExtension;
import io.nop.markdown.ext.math.MathNode;
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

    // Parser/Renderer 线程安全且构建开销可观（正则/表初始化），批量归一化时复用
    private volatile Parser parser;
    private volatile Renderer renderer;

    protected Parser buildParser() {
        Parser parser = this.parser;
        if (parser == null) {
            synchronized (this) {
                if (this.parser == null) {
                    this.parser = Parser.builder()
                            .extensions(Arrays.asList(TablesExtension.create(), MathExtension.create()))
                            .build();
                }
                parser = this.parser;
            }
        }
        return parser;
    }

    protected Renderer buildRenderer() {
        Renderer renderer = this.renderer;
        if (renderer == null) {
            synchronized (this) {
                if (this.renderer == null) {
                    this.renderer = MarkdownRenderer.builder()
                            .extensions(Arrays.asList(TablesExtension.create(), MathExtension.create()))
                            .build();
                }
                renderer = this.renderer;
            }
        }
        return renderer;
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
            // CommonMark 要求围栏长度大于内容中任何反引号行。强制压到3会导致
            // 内容含```的代码块在重解析时提前闭合（不可逆破坏），保留足够的围栏长度
            int fenceLength = Math.max(3, maxBacktickRunLength(fencedCodeBlock.getLiteral()) + 1);
            fencedCodeBlock.setOpeningFenceLength(fenceLength);
            fencedCodeBlock.setClosingFenceLength(fenceLength);
            super.visit(fencedCodeBlock);
        }

        static int maxBacktickRunLength(String content) {
            int max = 0;
            int cur = 0;
            for (int i = 0; i < content.length(); i++) {
                if (content.charAt(i) == '`') {
                    cur++;
                    if (cur > max)
                        max = cur;
                } else {
                    cur = 0;
                }
            }
            return max;
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
            Node prev = text;
            int n = literal.length();
            int i = 0;
            StringBuilder buf = new StringBuilder();
            while (i < n) {
                // 收集起始$之前的文本（反斜杠转义序列原样保留，与nextUntilUnescaped语义一致）
                i = collectUntilUnescapedDollar(literal, i, buf);
                if (i >= n) {
                    prev = appendText(prev, buf.toString());
                    break;
                }

                // literal.charAt(i)是不带转义的起始$；探测是否存在配对的结束$
                StringBuilder math = new StringBuilder();
                int close = collectUntilUnescapedDollar(literal, i + 1, math);
                if (close >= n) {
                    // 没有配对的结束$：$必须原样保留，不能吞掉（修复前起始$被消费后丢弃）
                    buf.append('$').append(math);
                    appendText(prev, buf.toString());
                    break;
                }

                prev = appendText(prev, buf.toString());
                MathNode newNode = new MathNode(math.toString());
                prev.insertAfter(newNode);
                prev = newNode;
                buf = new StringBuilder();
                i = close + 1;
            }
        }

        private static Node appendText(Node prev, String content) {
            if (content.isEmpty())
                return prev;
            Text newNode = new Text(content);
            prev.insertAfter(newNode);
            return newNode;
        }

        /**
         * 收集位置from开始直到不带转义的'$'或字符串末尾之间的文本到buf，
         * 返回停止位置（'$'的下标或n）
         */
        private static int collectUntilUnescapedDollar(String s, int from, StringBuilder buf) {
            int i = from;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '\\') {
                    buf.append(c);
                    if (i + 1 < s.length()) {
                        buf.append(s.charAt(i + 1));
                        i += 2;
                    } else {
                        i++;
                    }
                    continue;
                }
                if (c == '$')
                    return i;
                buf.append(c);
                i++;
            }
            return s.length();
        }
    }
}
