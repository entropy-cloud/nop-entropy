package io.nop.lint.java;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Module bootstrap proof: through this module's own classpath the
 * nop-lint-core → nop-treesitter dependency chain resolves, and Java syntax
 * kinds (the raw material of the kind-name mapping landing next) are
 * reachable.
 */
class JavaModuleBootstrapTest {

    private static final String SNIPPET = """
            class Demo {
                java.util.List<String> names;
            }
            """;

    @Test
    void javaGrammarResolvesThroughTransitiveCoreDependency() {
        Language java = Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin");
        assertNotNull(java, "java grammar blob must be visible from this module");

        TSTree tree = TSParser.parse(java, SNIPPET);

        List<String> kinds = new ArrayList<>();
        collectKinds(tree.rootNode(), kinds);
        assertTrue(kinds.contains("type_identifier"),
                "generic type reference must surface type_identifier, kinds saw: " + kinds);
        assertFalse(kinds.contains("ERROR"), "snippet must parse without errors");
    }

    private void collectKinds(TSNode node, List<String> kinds) {
        kinds.add(node.type());
        for (int i = 0; i < node.childCount(); i++) {
            TSNode child = node.child(i);
            if (child != null) {
                collectKinds(child, kinds);
            }
        }
    }
}
