package io.nop.lint.core.xscript;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintTree;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NodeWrapper} proofs over a real parsed tree (Minimum Rules #23
 * wiring: the wrapper drives the actual tree-sitter Java grammar through the
 * {@code LintNode} facade, not a hand-made stand-in). Navigation semantics,
 * null/empty contracts, and the 1-based line/column range form are each
 * asserted against a known source.
 */
public class TestNodeWrapper {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);

    private static final String SRC = """
            class Demo {
                private List<String> names;

                public String getName() {
                    return names.get(0);
                }
            }
            """;

    private static LintTree tree;
    private static NodeWrapper root;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        tree = JAVA.parse(SRC);
        root = new NodeWrapper(tree.root(), new SourceMap(tree.source()));
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ==================== wiring: real tree navigation ====================

    @Test
    public void descendantFindsFirstPreOrderMatchOnRealTree() {
        NodeWrapper method = root.descendant("method_declaration");
        assertEquals("method_declaration", method.kind());
        assertEquals("public String getName() {\n        return names.get(0);\n    }",
                method.text());
    }

    @Test
    public void ancestorAndChildTraverseTheRealTree() {
        NodeWrapper method = root.descendant("method_declaration");
        NodeWrapper cls = method.ancestor("method_declaration");
        assertNull(cls, "ancestor excludes the node itself");

        cls = method.ancestor("class_declaration");
        assertEquals("Demo", cls.child("name").text(), "the class's name field child");
        assertNull(method.ancestor("interface_declaration"), "missing ancestors are null");
    }

    @Test
    public void childrenSupportKindFiltering() {
        NodeWrapper body = root.descendant("method_declaration").child("body");
        assertEquals("block", body.kind());
        assertEquals(1, body.children().size(), "all visible children");
        assertEquals(1, body.children("return_statement").size(), "filtered children");
        assertEquals(0, body.children("if_statement").size(), "kind misses are empty, never null");
    }

    @Test
    public void siblingsExcludeSelfAndSupportKindFiltering() {
        NodeWrapper method = root.descendant("method_declaration");
        NodeWrapper field = root.descendant("field_declaration");

        List<NodeWrapper> siblings = method.siblings();
        assertEquals(1, siblings.size(), "field_declaration is the method's only sibling");
        assertEquals("field_declaration", siblings.get(0).kind());

        assertEquals(1, method.siblings("field_declaration").size());
        assertEquals(0, method.siblings("method_declaration").size(), "self must be excluded");

        List<NodeWrapper> fieldSiblings = field.siblings("method_declaration");
        assertEquals(1, fieldSiblings.size());
        assertTrue(fieldSiblings.get(0).text().startsWith("public String getName"));
    }

    @Test
    public void rootHasNoSiblings() {
        assertTrue(root.siblings().isEmpty(), "the root node has no parent and no siblings");
    }

    @Test
    public void descendantExcludesSelfAndReturnsNullOnMiss() {
        assertNull(root.descendant("program"), "descendant excludes the node itself");
        NodeWrapper method = root.descendant("method_declaration");
        assertNull(method.descendant("field_declaration"), "kind misses outside the subtree are null");
    }

    // ==================== range: 1-based line/column form ====================

    @Test
    public void rangeReportsOneBasedLinesAndByteColumns() {
        NodeWrapper field = root.descendant("field_declaration");
        var fieldRange = field.range();
        assertEquals(2, fieldRange.get("startLine"), "'private' sits on source line 2");
        assertEquals(5, fieldRange.get("startCol"), "after four leading spaces");
        assertEquals(31, fieldRange.get("endCol"), "the ';' of 'names;' on line 2");
        assertEquals(2, fieldRange.get("endLine"));

        NodeWrapper method = root.descendant("method_declaration");
        var methodRange = method.range();
        assertEquals(4, methodRange.get("startLine"));
        assertEquals(5, methodRange.get("startCol"));
        assertEquals(6, methodRange.get("endLine"), "the method's closing brace line");
        assertEquals(5, methodRange.get("endCol"));
    }

    @Test
    public void rangeOfMultiLineNodeSpansItsLines() {
        NodeWrapper cls = root.descendant("class_declaration");
        var range = cls.range();
        assertEquals(1, range.get("startLine"));
        assertEquals(7, range.get("endLine"), "the class spans to the last source line");
    }

    // ==================== source map ====================

    @Test
    public void sourceMapResolvesLineAndColumn() {
        SourceMap map = new SourceMap(tree.source());
        int bodyStart = SRC.indexOf("return");
        assertEquals(5, map.lineOf(bodyStart));
        assertEquals(SRC.indexOf("return") - SRC.indexOf("        return") + 1, map.columnOf(bodyStart));
    }
}
