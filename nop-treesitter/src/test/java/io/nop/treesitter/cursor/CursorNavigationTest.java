package io.nop.treesitter.cursor;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1: TSNode + TSTreeCursor core navigation over the arena snapshot that
 * {@code TSParser.parse} produces — deep descent, sibling walk, parent ascent,
 * named-vs-all iteration, extras (comments), &gt;{@code MAX_CHILDREN} flattening
 * and no-silent-skip out-of-range motion.
 */
public class CursorNavigationTest {

    private static Language JSON;

    @BeforeAll
    static void loadJson() {
        JSON = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
    }

    /**
     * Node type names appearing in a corpus-format s-expression, in document
     * order (the named visible spine).
     */
    private static List<String> sexpNamedTypes(String sexp) {
        List<String> out = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\(([A-Za-z_][A-Za-z0-9_]*)").matcher(sexp);
        while (matcher.find()) {
            out.add(matcher.group(1));
        }
        return out;
    }

    /**
     * Preorder list of every node's type reachable through named navigation,
     * visited purely through the cursor (no second tree representation).
     */
    private static List<String> namedCursorPreorderTypes(TSTree tree) {
        List<String> out = new ArrayList<>();
        TSTreeCursor cursor = tree.cursor();
        out.add(cursor.currentNode().type());
        descendNamed(cursor, out);
        return out;
    }

    private static void descendNamed(TSTreeCursor cursor, List<String> out) {
        if (!cursor.gotoFirstNamedChild()) {
            return;
        }
        while (true) {
            out.add(cursor.currentNode().type());
            descendNamed(cursor, out);
            if (!cursor.gotoNextNamedChild()) {
                break;
            }
        }
        cursor.gotoParent();
    }

    @Test
    void deepNavigationMatchesSExpressionSpine() {
        TSTree tree = TSParser.parse(JSON, "[1, [2, 3], {\"a\": 1}]");
        assertEquals(sexpNamedTypes(tree.toSExpression()), namedCursorPreorderTypes(tree));

        TSTreeCursor cursor = tree.cursor();
        assertEquals("document", cursor.currentNode().type());
        assertTrue(cursor.gotoFirstNamedChild());
        assertEquals("array", cursor.currentNode().type());
        assertEquals(1, cursor.depth());
        assertTrue(cursor.gotoFirstNamedChild());
        assertEquals("number", cursor.currentNode().type());
        assertEquals(2, cursor.depth());
        assertTrue(cursor.gotoNextNamedChild());
        assertEquals("array", cursor.currentNode().type());
        assertTrue(cursor.gotoFirstNamedChild());
        assertEquals("number", cursor.currentNode().type());
        assertTrue(cursor.gotoNextNamedChild());
        assertEquals("number", cursor.currentNode().type());
        assertTrue(cursor.gotoNextNamedChild());
        assertEquals("object", cursor.currentNode().type());
        assertTrue(cursor.gotoFirstNamedChild());
        assertEquals("pair", cursor.currentNode().type());
        assertTrue(cursor.gotoFirstNamedChild());
        assertEquals("string", cursor.currentNode().type());
        assertTrue(cursor.gotoFirstNamedChild());
        assertEquals("string_content", cursor.currentNode().type());
        assertFalse(cursor.gotoFirstNamedChild(), "a string_content leaf has no named children");
    }

    @Test
    void parentAscentReproducesOriginalPath() {
        TSTree tree = TSParser.parse(JSON, "{\"a\": {\"b\": 1}}");
        TSTreeCursor cursor = tree.cursor();
        List<String> down = new ArrayList<>();
        down.add(cursor.currentNode().type());
        assertTrue(cursor.gotoFirstNamedChild());
        down.add(cursor.currentNode().type());
        assertTrue(cursor.gotoFirstNamedChild());
        down.add(cursor.currentNode().type());
        assertTrue(cursor.gotoFirstNamedChild());
        down.add(cursor.currentNode().type());
        assertTrue(cursor.gotoFirstNamedChild());
        down.add(cursor.currentNode().type());
        assertEquals(List.of("document", "object", "pair", "string", "string_content"), down);

        List<String> up = new ArrayList<>();
        while (cursor.gotoParent()) {
            up.add(cursor.currentNode().type());
        }
        List<String> expectedUp = new ArrayList<>(down.subList(0, down.size() - 1));
        java.util.Collections.reverse(expectedUp);
        assertEquals(expectedUp, up);
        assertEquals("document", cursor.currentNode().type(), "gotoParent at root must be stable");
        assertFalse(cursor.gotoParent(), "gotoParent above the root must return false");
    }

    @Test
    void siblingWalkEqualsSExpressionChildOrder() {
        TSTree tree = TSParser.parse(JSON, "{\"a\": 1, \"b\": [1, 2], \"c\": {\"d\": 3}}");
        TSTreeCursor cursor = tree.cursor();
        assertTrue(cursor.gotoFirstNamedChild(), "document -> object");
        assertTrue(cursor.gotoFirstNamedChild(), "object -> first pair");

        List<String> walked = new ArrayList<>();
        do {
            walked.add(cursor.currentNode().type());
        } while (cursor.gotoNextNamedChild());
        assertEquals(List.of("pair", "pair", "pair"), walked,
                "named sibling walk order equals the s-expression child order");
        assertEquals(directChildTypes(subtreeOf(tree.toSExpression(), "object")), walked,
                "sibling walk order equals the toSExpression child order");
    }

    /**
     * The substring of a multi-line s-expression from {@code "(nodeName"} up to
     * its matching closing paren.
     */
    private static String subtreeOf(String sexp, String nodeName) {
        int start = sexp.indexOf("(" + nodeName + "\n");
        if (start < 0) {
            start = sexp.indexOf("(" + nodeName + "(");
        }
        int depth = 0;
        for (int i = start; i < sexp.length(); i++) {
            if (sexp.charAt(i) == '(') {
                depth++;
            } else if (sexp.charAt(i) == ')') {
                depth--;
                if (depth == 0) {
                    return sexp.substring(start, i + 1);
                }
            }
        }
        throw new IllegalStateException("no matching close paren for " + nodeName);
    }

    /**
     * Direct child type names of a subtree: the lines at the first indent level
     * below the node line of a multi-line s-expression.
     */
    private static List<String> directChildTypes(String subtree) {
        List<String> out = new ArrayList<>();
        String[] lines = subtree.split("\n");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("    ") && !line.startsWith("      ") && line.contains("(")) {
                Matcher matcher = Pattern.compile("\\(([A-Za-z_][A-Za-z0-9_]*)").matcher(line);
                if (matcher.find()) {
                    out.add(matcher.group(1));
                }
            }
        }
        return out;
    }

    @Test
    void namedVsAllChildIteration() {
        TSTree tree = TSParser.parse(JSON, "{\"a\": 1}");
        TSTreeCursor cursor = tree.cursor();
        assertTrue(cursor.gotoFirstChild());
        assertEquals("object", cursor.currentNode().type());
        assertEquals(3, cursor.childCount(), "object visible children: '{', pair, '}'");
        assertEquals(1, cursor.namedChildCount(), "object has exactly one named child (pair)");
        assertTrue(cursor.gotoFirstNamedChild());
        assertEquals("pair", cursor.currentNode().type());
        assertEquals(3, cursor.childCount(), "pair visible children: string, ':', number");
        assertEquals(2, cursor.namedChildCount(), "named pair children: string and number");

        List<String> all = new ArrayList<>();
        assertTrue(cursor.gotoFirstChild());
        do {
            all.add(cursor.currentNode().type());
        } while (cursor.gotoNextSibling());
        assertEquals(List.of("string", ":", "number", "}"), all,
                "unfiltered iteration includes the anonymous ':' token and bubbles past "
                        + "the pair's last child into the object's closing brace");

        List<String> named = new ArrayList<>();
        assertTrue(cursor.gotoParent(), "back to object");
        assertTrue(cursor.gotoFirstNamedChild());
        do {
            named.add(cursor.currentNode().type());
        } while (cursor.gotoNextNamedChild());
        assertEquals(List.of("pair"), named, "named sibling walk at the object level");
        assertFalse(cursor.gotoNextNamedChild(), "no named sibling after the last one");

        assertTrue(cursor.gotoFirstNamedChild());
        assertEquals("string", cursor.currentNode().type());
        assertTrue(cursor.gotoNextNamedChild());
        assertEquals("number", cursor.currentNode().type(),
                "named iteration inside pair skips the anonymous ':' token and bubbles "
                        + "through the hidden value wrapper");
        assertFalse(cursor.gotoNextNamedChild(), "no named sibling after the value");
    }

    @Test
    void commentExtrasReachableInUnfilteredIteration() {
        TSTree tree = TSParser.parse(JSON,
                "{\n  // leading comment\n  \"a\": 1,\n  /* block */\n  \"b\": 2\n}");
        TSTreeCursor cursor = tree.cursor();
        assertTrue(cursor.gotoFirstChild());
        assertEquals("object", cursor.currentNode().type());
        assertEquals(4, cursor.namedChildCount(),
                "comments are named extras: 2 comments + 2 pairs");
        assertEquals(7, cursor.childCount(), "object visible children: '{', 2 comments, 2 pairs, 1 comma, '}'");

        List<String> pairTypes = new ArrayList<>();
        assertTrue(cursor.gotoFirstNamedChild());
        do {
            assertEquals("comment", cursor.currentNode().type());
            assertTrue(cursor.currentNode().isExtra(), "the comment child must be flagged extra");
        } while (cursor.gotoNextNamedChild() && cursor.currentNode().type().equals("comment"));
        do {
            if ("pair".equals(cursor.currentNode().type())) {
                pairTypes.add(cursor.currentNode().type());
            }
        } while (cursor.gotoNextNamedChild());
        assertEquals(List.of("pair", "pair"), pairTypes,
                "both pairs reachable past interleaved comment extras");
    }

    @Test
    void moreThanMaxChildrenNodeFlattensTransparently() {
        TSTree tree = TSParser.parse(JSON, "[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]");
        TSTreeCursor cursor = tree.cursor();
        assertTrue(cursor.gotoFirstChild());
        assertEquals("array", cursor.currentNode().type());
        assertEquals(25, cursor.childCount(),
                "visible child count is not limited by inline slots: '[', 12 numbers, 11 commas, ']'");
        assertEquals(12, cursor.namedChildCount());

        List<String> walked = new ArrayList<>();
        assertTrue(cursor.gotoFirstChild());
        do {
            walked.add(cursor.currentNode().type());
        } while (cursor.gotoNextSibling());
        assertEquals(25, walked.size(), "cursor child sequence equals the flattened child sequence");
        assertEquals(12, walked.stream().filter("number"::equals).count());
        assertEquals("[", walked.get(0));
        assertEquals("]", walked.get(walked.size() - 1));
        assertEquals("array", cursor.currentNode().parent().type(),
                "gotoParent after the walk returns to the array");
    }

    @Test
    void leafNodesHaveNoChildren() {
        TSTree tree = TSParser.parse(JSON, "1");
        TSTreeCursor cursor = tree.cursor();
        assertEquals("document", cursor.currentNode().type());
        assertTrue(cursor.gotoFirstChild());
        assertEquals("number", cursor.currentNode().type());
        assertEquals(0, cursor.childCount());
        assertFalse(cursor.gotoFirstChild(), "gotoFirstChild on a leaf returns false");
        assertEquals("number", cursor.currentNode().type(), "failed motion leaves the cursor unchanged");
    }

    @Test
    void outOfRangeMotionReturnsFalseWithStablePosition() {
        TSTree tree = TSParser.parse(JSON, "[1, 2]");
        TSTreeCursor cursor = tree.cursor();
        assertFalse(cursor.gotoParent(), "gotoParent at the root returns false");
        assertEquals("document", cursor.currentNode().type());

        assertTrue(cursor.gotoFirstChild());
        assertEquals("array", cursor.currentNode().type());
        assertTrue(cursor.gotoFirstChild());
        assertEquals("[", cursor.currentNode().type());
        assertTrue(cursor.gotoNextSibling());
        assertEquals("number", cursor.currentNode().type());
        assertTrue(cursor.gotoNextSibling());
        assertEquals(",", cursor.currentNode().type());
        assertTrue(cursor.gotoNextSibling());
        assertEquals("number", cursor.currentNode().type());
        assertTrue(cursor.gotoNextSibling());
        assertEquals("]", cursor.currentNode().type());
        assertFalse(cursor.gotoNextSibling(), "gotoNextSibling after the last sibling returns false");
        assertEquals("]", cursor.currentNode().type(), "failed motion leaves the cursor at the documented position");
        assertFalse(cursor.gotoChild(5), "gotoChild out of range returns false");
        assertEquals("]", cursor.currentNode().type());
        assertTrue(cursor.gotoParent());
        assertEquals("array", cursor.currentNode().type());
        assertTrue(cursor.gotoChild(0), "gotoChild(0) lands on the first visible child");
        assertEquals("[", cursor.currentNode().type());
        assertTrue(cursor.gotoParent());
        assertTrue(cursor.gotoParent());
        assertEquals("document", cursor.currentNode().type(), "ascent back to the root");
    }

    @Test
    void tsNodeAccessorsDelegateToTheCursor() {
        TSTree tree = TSParser.parse(JSON, "{\"a\": 1}");
        TSNode root = tree.rootNode();
        assertNotNull(root);
        assertEquals("document", root.type());
        assertTrue(root.isVisible());
        assertFalse(root.isExtra());

        TSNode object = root.child(0);
        assertNotNull(object);
        assertEquals("object", object.type());
        assertTrue(object.named());
        assertEquals(3, object.childCount());
        assertEquals(1, object.namedChildCount());

        TSNode pair = object.namedChild(0);
        assertNotNull(pair);
        assertEquals("pair", pair.type());
        assertEquals(3, pair.childCount());
        assertEquals(2, pair.namedChildCount());
        assertEquals("string", pair.namedChild(0).type());
        assertEquals("number", pair.namedChild(1).type());
        assertEquals("string", pair.child(0).type());
        assertEquals(":", pair.child(1).type());
        assertEquals("number", pair.child(2).type());
        assertEquals(pair, pair.child(0).parent());
        assertNull(pair.child(9), "out-of-range child returns null");
        assertEquals(":", pair.namedChild(0).nextSibling().type(),
                "nextSibling includes the anonymous ':' token, matching C ts_node_next_sibling");
        assertEquals("number", pair.child(1).nextSibling().type(),
                "the value follows the anonymous colon");
        assertEquals("}", pair.child(1).nextSibling().nextSibling().type(),
                "sibling motion bubbles past the pair's last child into the object's closing brace");
    }

    @Test
    void cursorWalksTheArenaSnapshotThatTheParserProduced() {
        TSTree tree = TSParser.parse(JSON, "{\"a\": 1}");
        TSNode root = tree.rootNode();
        assertEquals(tree.root(), root.id(), "the cursor root is the parser-produced root id");
        assertEquals(tree.arena(), root.cursor().currentNode().tree().arena(),
                "the cursor navigates the tree's own snapshot arena");
        assertEquals(0, root.startByte());
    }

    @Test
    void preorderNamedSequenceEqualsSExpressionNamedNodes() {
        String source = "[1, {\"a\": [2, 3]}, null, {\"b\": {\"c\": 4}}]";
        TSTree tree = TSParser.parse(JSON, source);
        assertEquals(sexpNamedTypes(tree.toSExpression()),
                namedCursorPreorderTypes(tree),
                "cursor-reachable named nodes equal the named nodes of the s-expression, in order");
    }
}