package io.nop.treesitter.cursor;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.compat.TreeSitterBash;
import io.nop.treesitter.compat.TreeSitterCSharp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Primary-API {@code hasError()} and field navigation (plan Phase 3), and their
 * agreement with the compat surface.
 *
 * <p>The compat layer exists so that migrating off the JNI bindings is an import
 * change and not a behaviour change, so "the two agree on the same tree" is a
 * contract, not a convenience — it is asserted here rather than assumed.</p>
 */
class TSNodeErrorAndFieldTest {

    private static TSNode firstNodeOfType(TSTree tree, String type) {
        TSTreeCursor cursor = tree.rootNode().cursor();
        if (cursor.gotoFirstChild()) {
            do {
                TSNode node = cursor.currentNode();
                if (node.named() && type.equals(node.type())) {
                    return node;
                }
            } while (cursor.gotoNextSibling());
        }
        return null;
    }

    private static TSNode findByType(TSNode node, String type) {
        if (node.named() && type.equals(node.type())) {
            return node;
        }
        TSTreeCursor cursor = node.cursor();
        if (cursor.gotoFirstChild()) {
            do {
                TSNode found = findByType(cursor.currentNode(), type);
                if (found != null) {
                    return found;
                }
            } while (cursor.gotoNextSibling());
        }
        return null;
    }

    // ---- hasError ----------------------------------------------------------

    @Test
    void errorFreeTreeHasNoError() {
        TSTree tree = TSParser.parse(new TreeSitterBash().language(), "echo hi\n".getBytes());
        assertFalse(tree.rootNode().hasError());
    }

    @Test
    void errorNodeIsReported() {
        TSTree tree = TSParser.parse(new TreeSitterBash().language(),
                "if then rm -rf /tmp/example\n".getBytes());
        TSNode error = findByType(tree.rootNode(), "ERROR");
        assertNotNull(error, "input is expected to produce an ERROR node");
        assertTrue(error.hasError());
        assertTrue(tree.rootNode().hasError(), "an ERROR below the root must surface at the root");
    }

    @Test
    void missingNodeIsReported() {
        TSTree tree = TSParser.parse(new TreeSitterBash().language(), "echo ${x\n".getBytes());
        assertTrue(tree.toSexpString(false).contains("MISSING"), tree.toSexpString(false));
        assertTrue(tree.rootNode().hasError(), "a MISSING node is a syntax error for fail-closed callers");
    }

    @Test
    void errorNestedDeepInsideTheSubtreeIsReported() {
        // The ERROR sits several levels below `program`, behind nodes that render
        // as ordinary well-formed nodes: the walk must not stop at the first
        // visible level.
        TSTree tree = TSParser.parse(new TreeSitterBash().language(),
                "for i in a; do if then echo x; done\n".getBytes());
        TSNode program = tree.rootNode();
        assertTrue(program.hasError());
        assertTrue(program.namedChild(0).hasError(),
                "the error must be visible from the enclosing statement too");
    }

    /**
     * No false positives at any depth of a clean tree. Every node reachable in a
     * composite-but-valid script must report no error, or the fail-closed
     * predicate would be useless (everything would be "not proven").
     */
    @Test
    void noFalsePositiveAtAnyDepthOfACleanTree() {
        TSTree tree = TSParser.parse(new TreeSitterBash().language(),
                ("cat <<EOF\nbody $USER\nEOF\n"
                        + "for i in a b; do echo $i; done\n"
                        + "case $i in a) ls ;; esac\n").getBytes());
        assertFalse(tree.toSexpString(false).contains("ERROR"), tree.toSexpString(false));
        assertFalse(tree.rootNode().hasError());
        assertEveryNodeReportsNoError(tree.rootNode());
    }

    private static void assertEveryNodeReportsNoError(TSNode node) {
        assertFalse(node.hasError(), "unexpected error reported at " + node.type()
                + " [" + node.startByte() + "," + node.endByte() + ")");
        TSTreeCursor cursor = node.cursor();
        if (cursor.gotoFirstChild()) {
            do {
                assertEveryNodeReportsNoError(cursor.currentNode());
            } while (cursor.gotoNextSibling());
        }
    }

    // ---- field navigation ---------------------------------------------------

    @Test
    void fieldNavigationReachesTheHeredocRedirect() {
        TSTree tree = TSParser.parse(new TreeSitterBash().language(),
                "cat <<EOF\nbody\nEOF\n".getBytes());
        TSNode redirected = firstNodeOfType(tree, "redirected_statement");
        assertNotNull(redirected, tree.toSexpString(true));

        TSNode body = redirected.childByFieldName("body");
        assertNotNull(body, "redirected_statement must expose body:");
        assertEquals("command", body.type());

        TSNode redirect = redirected.childByFieldName("redirect");
        assertNotNull(redirect, "redirected_statement must expose redirect:");
        assertEquals("heredoc_redirect", redirect.type());
    }

    @Test
    void fieldNavigationReachesTheCommandName() {
        TSTree tree = TSParser.parse(new TreeSitterBash().language(), "echo hi\n".getBytes());
        TSNode command = firstNodeOfType(tree, "command");
        assertNotNull(command);
        TSNode name = command.childByFieldName("name");
        assertNotNull(name);
        assertEquals("command_name", name.type());
    }

    @Test
    void unknownAndAbsentFieldNamesYieldNoChild() {
        TSTree tree = TSParser.parse(new TreeSitterBash().language(), "echo hi\n".getBytes());
        TSNode command = firstNodeOfType(tree, "command");
        assertNotNull(command);

        assertNull(command.childByFieldName("no_such_field"),
                "a field name the grammar does not define is 'no such child', not a crash");
        assertNull(command.childByFieldName(null), "a null field name is 'no such child'");
        assertNull(command.childByFieldName("redirect"),
                "a real field name that this node does not use is 'no such child'");
    }

    @Test
    void fieldNavigationOnTheRootNeverReturnsTheRootItself() {
        TSTree tree = TSParser.parse(new TreeSitterBash().language(), "echo hi\n".getBytes());
        TSNode root = tree.rootNode();
        assertNull(root.childByFieldName("body"),
                "an absent field must return null rather than standing in for the root");
    }

    // ---- primary / compat agreement ----------------------------------------

    private static io.nop.treesitter.compat.TSNode compatRoot(
            String source, io.nop.treesitter.compat.TreeSitterLanguage language) {
        io.nop.treesitter.compat.TSParser parser = new io.nop.treesitter.compat.TSParser();
        parser.setLanguage(language);
        return parser.parseString(null, source).getRootNode();
    }

    @Test
    void primaryAndCompatHasErrorAgree() {
        TreeSitterBash language = new TreeSitterBash();
        for (String source : new String[]{
                "echo hi\n",
                "if then rm -rf /tmp/example\n",
                "echo ${x\n",
                "cat <<EOF\nbody\nEOF\n",
                "for i in a; do if then echo x; done\n"}) {
            TSTree tree = TSParser.parse(language.language(), source.getBytes());
            io.nop.treesitter.compat.TSNode compat = compatRoot(source, language);
            assertEquals(tree.rootNode().hasError(), compat.hasError(),
                    "hasError disagreement for <<" + source + ">>");
        }
    }

    @Test
    void primaryAndCompatFieldLookupAgree() {
        String heredoc = "cat <<EOF\nbody\nEOF\n";
        TreeSitterBash bashLanguage = new TreeSitterBash();
        TSTree bash = TSParser.parse(bashLanguage.language(), heredoc.getBytes());
        io.nop.treesitter.compat.TSNode compatRedirected =
                compatDescendant(compatRoot(heredoc, bashLanguage), "redirected_statement");
        assertNotNull(compatRedirected);
        assertEquals("heredoc_redirect",
                compatRedirected.getChildByFieldName("redirect").getType());
        assertEquals("command",
                compatRedirected.getChildByFieldName("body").getType());

        String csharpSource = "class C { void M() {} }";
        TreeSitterCSharp csharpLanguage = new TreeSitterCSharp();
        TSTree csharp = TSParser.parse(csharpLanguage.language(), csharpSource.getBytes());
        io.nop.treesitter.compat.TSNode compatClass =
                compatDescendant(compatRoot(csharpSource, csharpLanguage), "class_declaration");
        assertNotNull(compatClass);
        TSNode primaryClass = findByType(csharp.rootNode(), "class_declaration");
        assertNotNull(primaryClass);
        assertEquals(primaryClass.childByFieldName("name").type(),
                compatClass.getChildByFieldName("name").getType());
    }

    /** Depth-first search for a named node of the given type on the compat surface. */
    private static io.nop.treesitter.compat.TSNode compatDescendant(
            io.nop.treesitter.compat.TSNode node, String type) {
        for (int i = 0, n = node.getNamedChildCount(); i < n; i++) {
            io.nop.treesitter.compat.TSNode child = node.getNamedChild(i);
            if (child != null && !child.isNull() && type.equals(child.getType())) {
                return child;
            }
            io.nop.treesitter.compat.TSNode found = compatDescendant(child, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

}
