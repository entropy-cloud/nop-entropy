package io.nop.lint.core.xml;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.pattern.Match;
import io.nop.lint.core.pattern.MetaVarEnv;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The XNode pattern matching matrix (plan Phase 1, Minimum Rules #25): tag /
 * attribute / text / children × hit / miss × meta-var capture consistency,
 * plus the missing-attribute, {@code x:}-exclusion, comment-skip, and
 * fail-closed compile-rejection cells — one assertion per cell.
 */
class XNodePatternMatcherTest {

    private static LintTree parse(String source) {
        return XmlSourceParser.parse(source);
    }

    private static List<Match> matches(String pattern, String source) {
        return new XNodePatternMatcher(XNodePatternCompiler.compile(pattern))
                .findMatches(parse(source).root());
    }

    private static boolean matchesOnce(String pattern, String source) {
        return matches(pattern, source).size() == 1;
    }

    // ==================== tag dimension ====================

    @Test
    void tagNameMatchHits() {
        assertTrue(matchesOnce("<entity/>", "<entity name=\"a\"/>"));
    }

    @Test
    void tagNameMismatchFails() {
        assertTrue(matches("<entity/>", "<table name=\"a\"/>").isEmpty());
    }

    @Test
    void tagMatchIsRecursiveOverSubtree() {
        List<Match> found = matches("<column/>", "<entity><columns><column name=\"a\"/>"
                + "<column name=\"b\"/></columns></entity>");
        assertEquals(2, found.size());
    }

    // ==================== attribute dimension ====================

    @Test
    void literalAttrValueHits() {
        assertTrue(matchesOnce("<column mandatory=\"true\"/>",
                "<column name=\"a\" mandatory=\"true\"/>"));
    }

    @Test
    void literalAttrValueMismatchFails() {
        assertTrue(matches("<column mandatory=\"false\"/>",
                "<column name=\"a\" mandatory=\"true\"/>").isEmpty());
    }

    @Test
    void declaredAttrMissingOnSourceFails() {
        assertTrue(matches("<column mandatory=\"true\"/>", "<column name=\"a\"/>").isEmpty());
    }

    @Test
    void undeclaredSourceAttrsAreFree() {
        assertTrue(matchesOnce("<entity name=\"a\"/>",
                "<entity name=\"a\" tableName=\"t\" icon=\"i\" x:extends=\"/base.orm.xml\"/>"));
    }

    @Test
    void singleMetaVarCapturesAttrValue() {
        List<Match> found = matches("<column name=\"$COL\"/>", "<column name=\"dept_id\"/>");
        assertEquals(1, found.size());
        assertEquals("dept_id", capturedValue(found.get(0), "COL"));
    }

    @Test
    void sameNameMetaVarRequiresEqualAttrValues() {
        assertTrue(matchesOnce("<key column=\"$C\" refColumn=\"$C\"/>",
                "<key column=\"dept\" refColumn=\"dept\"/>"));
    }

    @Test
    void sameNameMetaVarWithDifferentAttrValuesFails() {
        assertTrue(matches("<key column=\"$C\" refColumn=\"$C\"/>",
                "<key column=\"dept\" refColumn=\"user\"/>").isEmpty());
    }

    @Test
    void dropVarMatchesAnyAttrValueAndCapturesNothing() {
        List<Match> found = matches("<column name=\"$_\"/>", "<column name=\"secret\"/>");
        assertEquals(1, found.size());
        assertEquals(0, found.get(0).env().singleCaptures().size());
    }

    @Test
    void dropVarToleratesAbsentAttribute() {
        assertTrue(matchesOnce("<column mandatory=\"$_\"/>", "<column name=\"a\"/>"));
    }

    @Test
    void namedDropVarToleratesAbsentAttribute() {
        assertTrue(matchesOnce("<column mandatory=\"$_M\"/>", "<column name=\"a\"/>"));
    }

    @Test
    void bareMultiAttrRequiresPresenceWithAnyValue() {
        assertTrue(matchesOnce("<entity name=\"$$$\"/>", "<entity name=\"x\"/>"));
    }

    @Test
    void bareMultiAttrAbsentFails() {
        assertTrue(matches("<entity name=\"$$$\"/>", "<entity tableName=\"x\"/>").isEmpty());
    }

    @Test
    void xPrefixedPatternAttrIsRejectedAtCompile() {
        NopLintException e = assertThrows(NopLintException.class,
                () -> XNodePatternCompiler.compile("<entity x:extends=\"$$$\"/>"));
        assertTrue(e.getMessage().contains("XDSL-internal"));
    }

    // ==================== text dimension ====================

    @Test
    void textComparesTrimmed() {
        assertTrue(matchesOnce("<auth>role:admin</auth>",
                "<auth>\n      role:admin   \n    </auth>"));
    }

    @Test
    void literalTextMismatchFails() {
        assertTrue(matches("<auth>role:admin</auth>", "<auth>role:user</auth>").isEmpty());
    }

    @Test
    void undeclaredTextLeavesSourceTextUnconstrained() {
        assertTrue(matchesOnce("<auth/>", "<auth>role:admin</auth>"));
    }

    @Test
    void bareMultiTextMatchesAnyTextIncludingEmpty() {
        assertTrue(matchesOnce("<auth>$$$</auth>", "<auth>role:admin</auth>"));
        assertTrue(matchesOnce("<auth>$$$</auth>", "<auth/>"));
    }

    @Test
    void textMetaVarCapturesTrimmedValue() {
        List<Match> found = matches("<auth>$ROLE</auth>", "<auth>\n role:admin \n</auth>");
        assertEquals(1, found.size());
        assertEquals("role:admin", capturedValue(found.get(0), "ROLE"));
    }

    @Test
    void sameNameConsistencyAcrossAttrAndTextPositions() {
        assertTrue(matchesOnce("<grant role=\"$R\"><auth>$R</auth></grant>",
                "<grant role=\"admin\"><auth>admin</auth></grant>"));
        assertTrue(matches("<grant role=\"$R\"><auth>$R</auth></grant>",
                "<grant role=\"admin\"><auth>user</auth></grant>").isEmpty());
    }

    @Test
    void namedMultiInTextPositionIsRejected() {
        assertThrows(NopLintException.class, () -> XNodePatternCompiler.compile("<auth>$$$R</auth>"));
    }

    @Test
    void anonymousMetaVarInTextPositionIsRejected() {
        assertThrows(NopLintException.class, () -> XNodePatternCompiler.compile("<auth>$$R</auth>"));
    }

    @Test
    void cdataTextParticipatesAsText() {
        assertTrue(matchesOnce("<desc>hello</desc>", "<desc><![CDATA[hello]]></desc>"));
    }

    // ==================== children dimension ====================

    @Test
    void undeclaredChildrenAreUnconstrained() {
        assertTrue(matchesOnce("<action name=\"$$$\">$$$</action>",
                "<action name=\"query\"><auth>admin</auth><source>xml</source></action>"));
    }

    @Test
    void declaredChildSequenceHits() {
        assertTrue(matchesOnce("<entity><columns><column/></columns></entity>",
                "<entity name=\"a\"><columns><column name=\"x\"/></columns></entity>"));
    }

    @Test
    void declaredChildSequenceWithExtraSourceChildFails() {
        assertTrue(matches("<entity><columns/></entity>",
                "<entity><columns/><indexes/></entity>").isEmpty());
    }

    @Test
    void declaredChildSequenceWrongOrderFails() {
        assertTrue(matches("<entity><columns/><indexes/></entity>",
                "<entity><indexes/><columns/></entity>").isEmpty());
    }

    @Test
    void declaredChildSequenceWithMissingChildFails() {
        assertTrue(matches("<entity><columns/></entity>", "<entity/>").isEmpty());
    }

    @Test
    void nestedChildPatternMatchesRecursively() {
        assertTrue(matchesOnce("<entity name=\"$$$\"><columns><column mandatory=\"true\"/></columns></entity>",
                "<entity name=\"a\"><columns><column name=\"x\" mandatory=\"true\"/></columns></entity>"));
    }

    @Test
    void childCaptureCommitsToParentMatch() {
        List<Match> found = matches("<entity><columns><column name=\"$C\"/></columns></entity>",
                "<entity><columns><column name=\"dept\"/></columns></entity>");
        assertEquals(1, found.size());
        assertEquals("dept", capturedValue(found.get(0), "C"));
    }

    // ==================== strictness: comments and trivia ====================

    @Test
    void commentBeforeElementDoesNotBlockMatch() {
        String source = "<!-- select all columns -->\n<column name=\"a\" mandatory=\"true\"/>";
        assertTrue(matchesOnce("<column name=\"a\" mandatory=\"true\"/>", source));
    }

    @Test
    void commentBetweenChildrenIsSkipped() {
        String source = "<entity><!-- legacy --><columns><column name=\"x\"/></columns></entity>";
        assertTrue(matchesOnce("<entity><columns><column name=\"x\"/></columns></entity>", source));
    }

    @Test
    void commentIsExposedAsExtraTriviaForSuppression() {
        LintTree tree = parse("<!-- nop-lint-disable-line demo/r -->\n<column name=\"a\"/>");
        List<LintNode> children = tree.root().children();
        assertEquals(1, children.size());
        LintNode trivia = children.get(0);
        assertInstanceOf(XNodeLintNode.class, trivia);
        assertTrue(trivia.isExtra());
        assertEquals(XNodeLintNode.COMMENT_KIND, trivia.kind());
        assertTrue(trivia.text().startsWith("<!--"));
        assertTrue(trivia.text().endsWith("-->"));
        // The trivia range slices exactly its text out of the source.
        String raw = new String(tree.source());
        assertEquals(trivia.text(), raw.substring(trivia.range().startByte(), trivia.range().endByte()));
    }

    @Test
    void mixedContentTextTriviaIsSkippedByMatcher() {
        LintTree tree = parse("<p>text<column name=\"a\"/></p>");
        assertEquals(2, tree.root().children().size());
        assertTrue(matchesOnce("<p><column name=\"a\"/></p>", "<p>text<column name=\"a\"/></p>"));
    }

    // ==================== fail-closed compile surface ====================

    @Test
    void blankPatternIsRejected() {
        assertThrows(NopLintException.class, () -> XNodePatternCompiler.compile("   "));
    }

    @Test
    void twoRootElementPatternIsRejected() {
        assertThrows(NopLintException.class, () -> XNodePatternCompiler.compile("<a/><b/>"));
    }

    @Test
    void malformedXmlPatternIsRejected() {
        assertThrows(NopLintException.class, () -> XNodePatternCompiler.compile("<a attr=\"x\"/>extra"));
    }

    @Test
    void namedMultiInAttrPositionIsRejected() {
        assertThrows(NopLintException.class, () -> XNodePatternCompiler.compile("<a name=\"$$$V\"/>"));
    }

    @Test
    void anonymousMetaVarInAttrPositionIsRejected() {
        assertThrows(NopLintException.class, () -> XNodePatternCompiler.compile("<a name=\"$$V\"/>"));
    }

    @Test
    void bareSingleInAttrPositionIsRejected() {
        assertThrows(NopLintException.class, () -> XNodePatternCompiler.compile("<a name=\"$\"/>"));
    }

    @Test
    void typedMetaVarIsRejectedEverywhere() {
        assertThrows(NopLintException.class, () -> XNodePatternCompiler.compile("<a name=\"$@T\"/>"));
    }

    @Test
    void mixedContentPatternIsRejected() {
        assertThrows(NopLintException.class, () -> XNodePatternCompiler.compile("<a>text<b/></a>"));
    }

    @Test
    void processingInstructionInsideBodyFailsClosedAtParse() {
        // A PI inside the body is not a supported XNode match form: the
        // platform parser rejects it and the XML path propagates the failure
        // (Minimum Rules #24 — no silent skip of unsupported shapes).
        assertThrows(NopLintException.class, () -> XmlSourceParser.parse("<a><?pi x?></a>"));
    }

    @Test
    void kindIdOfUnknownTagFormResolvesToMinusOne() {
        assertEquals(-1, XmlTagKinds.idFor("$$$"));
        assertEquals(-1, XmlTagKinds.idFor(""));
        assertTrue(XmlTagKinds.idFor("entity") >= 0);
        assertEquals("entity", XmlTagKinds.tagNameFor(XmlTagKinds.idFor("entity")));
    }

    // ==================== node-matcher leaf ====================

    @Test
    void nodeMatcherLeafCommitsOnlyOnSuccess() {
        XNodePatternMatcher matcher = new XNodePatternMatcher(
                XNodePatternCompiler.compile("<column name=\"$C\"/>"));
        LintTree tree = parse("<column name=\"a\"/>");
        MetaVarEnv env = new MetaVarEnv();
        assertTrue(matcher.matches(tree.root(), env));
        assertEquals("a", capturedValue(new Match(tree.root(), env), "C"));

        MetaVarEnv failing = new MetaVarEnv();
        assertFalse(matcher.matches(parse("<column/>").root(), failing));
        assertEquals(0, failing.singleCaptures().size());
    }

    private static String capturedValue(Match match, String name) {
        LintNode captured = match.env().getCapture(name);
        assertNotNull(captured, "capture '" + name + "' must be bound");
        return captured.text();
    }
}
