package io.nop.lint.core.xml;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.node.LineIndex;
import io.nop.lint.core.node.SourceRange;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the XML facade's parse contract: 1-based line mapping of node ranges
 * (the coordinates the diagnostics and console output share), UTF-8 byte
 * offsets over multi-byte content, quote-aware start-tag spans, comment and
 * text trivia exposure, attribute decoding, and fail-closed parse errors.
 */
class XmlSourceParserTest {

    @Test
    void nodeRangeMapsToTheVisualLine() {
        String source = "<entity>\n"
                + "  <columns>\n"
                + "    <column name=\"a\"/>\n"
                + "  </columns>\n"
                + "</entity>";
        LintTree tree = XmlSourceParser.parse(source);
        LineIndex lines = new LineIndex(tree.source());
        LintNode column = findFirst(tree.root(), "column");
        assertEquals(3, lines.startLine(column.range()));
        assertEquals(3, lines.endLine(column.range()));
        assertEquals('<', charAt(tree, column.range().startByte()));
    }

    @Test
    void byteOffsetsRespectMultiByteContent() {
        // "注释" is 2 chars but 6 UTF-8 bytes: the column element starts at
        // char offset 12 but byte offset 16.
        String source = "<!-- 注释 -->\n<column name=\"é\"/>";
        LintTree tree = XmlSourceParser.parse(source);
        LintNode column = findFirst(tree.root(), "column");
        byte[] raw = tree.source();
        assertEquals(16, column.range().startByte());
        assertEquals('<', raw[column.range().startByte()]);
        // The end byte is exclusive after the start tag's '>'.
        assertEquals('>', raw[column.range().endByte() - 1]);
        assertEquals(source.length(), new String(raw, StandardCharsets.UTF_8).length());
    }

    @Test
    void startTagSpanIsQuoteAware() {
        String source = "<column name=\"a>b\" notes=\"c'd\"/>";
        LintTree tree = XmlSourceParser.parse(source);
        LintNode column = tree.root();
        String text = column.text();
        assertEquals("<column name=\"a>b\" notes=\"c'd\"/>", text);
        assertEquals(source.length(), column.range().endByte());
    }

    @Test
    void attributeValuesAreDecoded() {
        LintTree tree = XmlSourceParser.parse("<column name=\"a&amp;b\"/>");
        XNodeLintNode element = (XNodeLintNode) tree.root();
        assertEquals("a&b", element.attrValue("name"));
        assertNull(element.attrValue("missing"));
    }

    @Test
    void textContentIsExposedTrimmedReadyForMatching() {
        XNodeLintNode element = (XNodeLintNode) XmlSourceParser.parse("<auth> role:admin </auth>").root();
        assertEquals("role:admin", element.contentText().trim());
    }

    @Test
    void elementTextIsTheStartTagSlice() {
        LintTree tree = XmlSourceParser.parse("<a x=\"1\"><b/></a>");
        String raw = new String(tree.source(), StandardCharsets.UTF_8);
        SourceRange range = tree.root().range();
        assertEquals("<a x=\"1\">", raw.substring(range.startByte(), range.endByte()));
    }

    @Test
    void commentAttachesToTheFollowingElement() {
        String source = "<entity>\n<!-- about b -->  <b/>\n</entity>";
        LintTree tree = XmlSourceParser.parse(source);
        LintNode b = findFirst(tree.root(), "b");
        List<LintNode> children = b.children();
        assertEquals(1, children.size());
        LintNode comment = children.get(0);
        assertEquals(XNodeLintNode.COMMENT_KIND, comment.kind());
        String raw = new String(tree.source(), StandardCharsets.UTF_8);
        assertEquals("<!-- about b -->",
                raw.substring(comment.range().startByte(), comment.range().endByte()));
        assertEquals(comment.text(), raw.substring(comment.range().startByte(), comment.range().endByte()));
    }

    @Test
    void parentLinksAreConsistent() {
        String source = "<a><b><c/></b></a>";
        LintTree tree = XmlSourceParser.parse(source);
        LintNode root = tree.root();
        assertNull(root.parent());
        LintNode b = findFirst(root, "b");
        assertEquals(root, b.parent());
        LintNode c = findFirst(root, "c");
        assertEquals(b, c.parent());
    }

    @Test
    void parseFailureFailsClosed() {
        assertThrows(NopLintException.class, () -> XmlSourceParser.parse("<a><b></a>"));
        assertThrows(NopLintException.class, () -> XmlSourceParser.parse((String) null));
        assertThrows(NopLintException.class, () -> XmlSourceParser.parse((byte[]) null));
    }

    @Test
    void sourceBytesRoundTrip() {
        String source = "<a/>";
        LintTree tree = XmlSourceParser.parse(source);
        assertEquals(source, new String(tree.source(), StandardCharsets.UTF_8));
    }

    @Test
    void mixedContentProducesTextTrivia() {
        LintTree tree = XmlSourceParser.parse("<p>hello<b/></p>");
        List<LintNode> children = tree.root().children();
        assertEquals(2, children.size());
        assertEquals(XNodeLintNode.TEXT_KIND, children.get(0).kind());
        org.junit.jupiter.api.Assertions.assertFalse(children.get(0).isNamed(),
                "text trivia must be unnamed so matchers step over it");
        assertEquals("hello", children.get(0).text());
    }

    private static char charAt(LintTree tree, int byteOffset) {
        return new String(tree.source(), StandardCharsets.UTF_8).charAt(byteOffset);
    }

    private static LintNode findFirst(LintNode root, String tag) {
        for (LintNode node : root) {
            if (tag.equals(node.kind())) {
                return node;
            }
        }
        throw new AssertionError("no element with tag " + tag);
    }
}
