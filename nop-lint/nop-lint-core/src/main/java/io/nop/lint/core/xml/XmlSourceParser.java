package io.nop.lint.core.xml;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.node.SourceRange;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses XML source into a {@link LintTree} over the {@link XNodeLintNode}
 * facade — the single parse entry of the XML path (design 01 §1: the
 * platform's own XNode parser, consumed read-only; no tree-sitter
 * involvement).
 *
 * <p>Byte-range contract: the XNode parser reports 1-based line/column
 * positions; this class builds a line table over the source string and maps
 * every node to UTF-8 byte offsets (the {@code SourceRange} contract that
 * diagnostics, console output, and suppression spans share). An element's
 * range is its start-tag span, computed by a quote-aware scan for the closing
 * {@code >} (attribute values may contain {@code >}); the attached preceding
 * comment's range is located by a backward scan from the owner node's start,
 * which the parser's comment attachment rule makes exact.</p>
 *
 * <p>Parse failures propagate as the parser's own errors wrapped in
 * {@link NopLintException} — an unparseable source is a hard error, never a
 * silent skip (Minimum Rules #24).</p>
 */
public final class XmlSourceParser {

    private XmlSourceParser() {
    }

    /**
     * Parses XML source text into a facade tree.
     */
    public static LintTree parse(String source) {
        if (source == null) {
            throw new NopLintException("xml source must not be null");
        }
        return parse(source, source.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Parses XML source bytes into a facade tree; {@link LintTree#source()}
     * reports the same bytes.
     */
    public static LintTree parse(byte[] source) {
        if (source == null) {
            throw new NopLintException("xml source must not be null");
        }
        return parse(new String(source, StandardCharsets.UTF_8), source);
    }

    private static LintTree parse(String source, byte[] sourceBytes) {
        XNode root;
        try {
            root = XNodeParser.instance().keepComment(true).parseFromText(null, source);
        } catch (Exception e) {
            throw new NopLintException("xml source failed to parse: " + e.getMessage(), e);
        }
        if (root == null) {
            throw new NopLintException("xml source is empty");
        }
        ByteMap bytes = new ByteMap(source);
        XNodeLintNode facade = build(root, null, bytes, source);
        return LintTree.ofFacade(facade, sourceBytes);
    }

    private static XNodeLintNode build(XNode element, XNodeLintNode parent, ByteMap bytes, String source) {
        SourceLocation loc = element.getLocation();
        if (loc == null) {
            throw new NopLintException("xml element '" + element.getTagName()
                    + "' has no source location; the lint XML path requires parser-built trees");
        }
        // XNode locations point at the first tag-name character (one past
        // the opening '<'), col 1-based — verified against the parser.
        int start = bytes.charOffset(loc.getLine(), loc.getCol()) - 1;
        int tagEnd = startTagEnd(source, start, element.getTagName());
        SourceRange range = new SourceRange(bytes.byteOffset(start), bytes.byteOffset(tagEnd + 1));

        XNodeLintNode node = new XNodeLintNode(XNodeLintNode.Flavor.ELEMENT, element,
                source.substring(start, tagEnd + 1), range, parent, attrView(element));

        List<LintNode> children = new ArrayList<>();
        String comment = element.getComment();
        if (comment != null && !comment.isEmpty()) {
            children.add(commentTrivia(comment, start, bytes, source, node));
        }
        for (XNode child : element.getChildren()) {
            if (child.isTextNode()) {
                children.add(textTrivia(child, bytes, node));
            } else {
                children.add(build(child, node, bytes, source));
            }
        }
        node.attach(children);
        return node;
    }

    private static Map<String, String> attrView(XNode element) {
        Map<String, ValueWithLocation> attrs = element.attrValueLocs();
        if (attrs == null || attrs.isEmpty()) {
            return Map.of();
        }
        Map<String, String> view = new LinkedHashMap<>();
        for (Map.Entry<String, ValueWithLocation> entry : attrs.entrySet()) {
            view.put(entry.getKey(), entry.getValue().asString());
        }
        return view;
    }

    private static XNodeLintNode commentTrivia(String comment, int ownerStartChar, ByteMap bytes,
                                               String source, XNodeLintNode parent) {
        // ownerStartChar is the owner element's '<' offset; the comment ends
        // strictly before it.
        int begin = source.lastIndexOf("<!--", ownerStartChar);
        if (begin < 0) {
            throw new NopLintException("xml comment attached before char " + ownerStartChar
                    + " has no '<!--' opener in the source; source and parse tree are inconsistent");
        }
        int terminator = source.indexOf("-->", begin);
        if (terminator < 0 || terminator + 3 > ownerStartChar) {
            throw new NopLintException("xml comment at char " + begin
                    + " has no '-->' terminator before its owner node; source and parse tree are "
                    + "inconsistent");
        }
        int endExclusive = terminator + 3;
        SourceRange range = new SourceRange(bytes.byteOffset(begin), bytes.byteOffset(endExclusive));
        return new XNodeLintNode(XNodeLintNode.Flavor.COMMENT, null,
                source.substring(begin, endExclusive), range, parent, null);
    }

    private static XNodeLintNode textTrivia(XNode textNode, ByteMap bytes, XNodeLintNode parent) {
        SourceLocation loc = textNode.getLocation();
        ValueWithLocation content = textNode.content();
        String text = content == null || content.isNull() ? "" : content.asString("");
        int start = loc == null ? 0 : bytes.charOffset(loc.getLine(), loc.getCol());
        int startByte = bytes.byteOffset(start);
        // Text trivia never carries diagnostics and never matches; its end is
        // the decoded text length (entity decoding can shift it against the
        // raw slice) — an explicitly documented approximation for trivia.
        int endByte = startByte + text.getBytes(StandardCharsets.UTF_8).length;
        SourceRange range = new SourceRange(startByte, endByte);
        return new XNodeLintNode(XNodeLintNode.Flavor.TEXT, null, text, range, parent, null);
    }

    /**
     * The char offset of the {@code >} closing the start tag that opens at
     * {@code start} (which points at {@code '<'}). Quote-aware: attribute
     * values may contain {@code >}. Fails closed when the tag never closes —
     * unreachable for parser-produced trees, loud if the invariant breaks.
     */
    private static int startTagEnd(String source, int start, String tagName) {
        if (start >= source.length() || source.charAt(start) != '<') {
            throw new NopLintException("xml element '" + tagName + "' start offset does not point "
                    + "at '<'; source and parse tree are inconsistent");
        }
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = start + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == '>' && !inSingle && !inDouble) {
                return i;
            }
        }
        throw new NopLintException("xml element '" + tagName + "' start tag has no closing '>'");
    }

    /**
     * Line table over the source string: 1-based line/col (the XNode parser's
     * coordinates) to char offsets, and char offsets to UTF-8 byte offsets.
     */
    private static final class ByteMap {
        private final String source;
        private final int[] lineStartChars;
        private final int[] lineStartBytes;

        ByteMap(String source) {
            this.source = source;
            int lineCount = 1;
            for (int i = 0; i < source.length(); i++) {
                if (source.charAt(i) == '\n') {
                    lineCount++;
                }
            }
            lineStartChars = new int[lineCount + 1];
            int index = 1;
            for (int i = 0; i < source.length(); i++) {
                if (source.charAt(i) == '\n') {
                    lineStartChars[index++] = i + 1;
                }
            }
            lineStartChars[lineCount] = source.length();
            lineStartBytes = new int[lineCount + 1];
            for (int line = 1; line <= lineCount; line++) {
                lineStartBytes[line] = lineStartBytes[line - 1]
                        + source.substring(lineStartChars[line - 1], lineStartChars[line])
                                .getBytes(StandardCharsets.UTF_8).length;
            }
        }

        int charOffset(int line, int col) {
            if (line < 1 || line >= lineStartChars.length) {
                throw new NopLintException("xml source location line " + line
                        + " is outside the source; source and parse tree are inconsistent");
            }
            int offset = lineStartChars[line - 1] + Math.max(0, col - 1);
            if (offset > source.length()) {
                throw new NopLintException("xml source location line " + line + " col " + col
                        + " is outside the source; source and parse tree are inconsistent");
            }
            return offset;
        }

        int byteOffset(int charOffset) {
            int line = lineOfChar(charOffset);
            return lineStartBytes[line - 1]
                    + source.substring(lineStartChars[line - 1], charOffset)
                            .getBytes(StandardCharsets.UTF_8).length;
        }

        private int lineOfChar(int charOffset) {
            int low = 1;
            int high = lineStartChars.length - 1;
            int line = 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                if (lineStartChars[mid] <= charOffset) {
                    line = mid + 1;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            return line;
        }
    }
}
