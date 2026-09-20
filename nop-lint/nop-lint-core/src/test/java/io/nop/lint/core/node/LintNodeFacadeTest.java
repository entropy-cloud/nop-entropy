package io.nop.lint.core.node;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Facade focus tests: kind/kindId/text slicing/range, field access, parent
 * chain, children vs named children, bounded pre-order traversal, equality
 * semantics — verified against an independent raw-backend count.
 */
class LintNodeFacadeTest {

    private static final String SNIPPET = """
            class Demo {
                java.util.List<String> names;
                void run(int x) {
                    if (x > 0) {
                        throw new RuntimeException("boom");
                    }
                }
            }
            """;

    private static Language java;

    @BeforeAll
    static void loadLanguage() {
        java = Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin");
    }

    private static LintTree parseSnippet() {
        return LintTree.of(TSParser.parse(java, SNIPPET));
    }

    @Test
    void rootIsProgramAndHasNoParent() {
        LintNode root = parseSnippet().root();
        assertEquals("program", root.kind());
        assertTrue(root.kindId() >= 0);
        assertNull(root.parent());
    }

    @Test
    void textSliceMatchesRange() {
        LintNode root = parseSnippet().root();
        for (LintNode node : root) {
            SourceRange range = node.range();
            assertTrue(range.startByte() >= 0 && range.endByte() <= SNIPPET.length(),
                    "range must stay inside the source: " + range);
            if (node.isNamed() && node.children().isEmpty()) {
                String text = node.text();
                assertEquals(range.length(), text.getBytes(StandardCharsets.UTF_8).length,
                        "leaf text byte length must equal range length for " + node.kind());
            }
        }
        LintNode method = findKind(root, "method_declaration");
        assertEquals("void run(int x) {\n"
                + "        if (x > 0) {\n"
                + "            throw new RuntimeException(\"boom\");\n"
                + "        }\n"
                + "    }", method.text());
    }

    @Test
    void fieldAccessFindsMethodName() {
        LintNode method = findKind(parseSnippet().root(), "method_declaration");
        LintNode name = method.childByField("name");
        assertNotNull(name, "method_declaration must occupy a name field");
        assertEquals("identifier", name.kind());
        assertEquals("run", name.text());

        assertNull(method.childByField("no_such_field"),
                "unknown field must resolve to null, not throw");
    }

    @Test
    void parentChainClimbsToRoot() {
        LintNode root = parseSnippet().root();
        LintNode method = findKind(root, "method_declaration");
        LintNode current = method;
        while (current.parent() != null) {
            current = current.parent();
        }
        assertEquals(root, current, "parent chain must reach the same root handle");
    }

    @Test
    void childrenAreSupersetOfNamedChildren() {
        LintNode root = parseSnippet().root();
        for (LintNode node : root) {
            assertTrue(node.children().size() >= node.namedChildren().size(),
                    "visible children must be a superset of named children at " + node.kind());
        }
        LintNode block = findKind(root, "block");
        assertTrue(block.children().size() > block.namedChildren().size(),
                "block owns anonymous braces, so visible children must exceed named children");
    }

    @Test
    void traversalCountsMatchIndependentBackendWalk() {
        LintTree tree = parseSnippet();
        int viaFacade = 0;
        for (LintNode ignored : tree.root()) {
            viaFacade++;
        }

        int viaBackend = countVisible(tree.tree().rootNode());
        assertEquals(viaBackend, viaFacade,
                "facade pre-order must visit exactly the visible subtree once each");
    }

    private int countVisible(TSNode node) {
        int count = 1;
        for (int i = 0; i < node.childCount(); i++) {
            TSNode child = node.child(i);
            if (child != null) {
                count += countVisible(child);
            }
        }
        return count;
    }

    @Test
    void traversalVisitsEachNodeOnce() {
        LintNode root = parseSnippet().root();
        Set<LintNode> seen = new HashSet<>();
        int count = 0;
        for (LintNode node : root) {
            assertTrue(seen.add(node), "node visited twice: " + node);
            count++;
        }
        assertTrue(count > 10, "snippet tree must be non-trivial, got " + count);
    }

    @Test
    void equalityIsByTreePosition() {
        LintTree tree = parseSnippet();
        LintNode root = tree.root();
        LintNode method = findKind(root, "method_declaration");

        assertEquals(root, tree.root(), "two handles on the same position are equal");
        assertEquals(root.hashCode(), tree.root().hashCode());
        assertNotEquals(root, method);
        assertNotEquals(method, findKind(root, "class_declaration"));
    }

    @Test
    void leafChildrenAreEmptyNotNull() {
        LintNode root = parseSnippet().root();
        LintNode method = findKind(root, "method_declaration");
        LintNode name = method.childByField("name");
        assertNotNull(name);
        assertTrue(name.children().isEmpty(), "identifier leaf must have empty children");
        assertTrue(name.isNamed(), "identifier is a named node");
    }

    @Test
    void iteratorContract() {
        LintNode root = parseSnippet().root();
        Iterator<LintNode> it = root.iterator();
        assertTrue(it.hasNext());
        assertEquals(root, it.next(), "self comes first");
    }

    private LintNode findKind(LintNode root, String kind) {
        for (LintNode node : root) {
            if (node.kind().equals(kind)) {
                return node;
            }
        }
        throw new AssertionError("no " + kind + " in snippet tree");
    }
}
