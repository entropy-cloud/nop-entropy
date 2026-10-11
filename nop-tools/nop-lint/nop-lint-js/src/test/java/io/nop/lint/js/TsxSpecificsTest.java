package io.nop.lint.js;

import io.nop.lint.core.node.LintNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TSX specifics (plan item 19 Phase 2): the tsx grammar parses JSX element
 * syntax cleanly (no ERROR nodes), while the plain typescript grammar
 * degrades on the same source with explicit ERROR nodes — pinned here by
 * assertion, not guessed. The two bindings stay distinct parse surfaces
 * behind one contract.
 */
class TsxSpecificsTest {

    private static final String JSX = """
            const el = <div className="x">hello</div>;
            export function App(): JSX.Element {
                return <span onClick={() => console.log("click")}>go</span>;
            }
            """;

    @Test
    void tsxBindingParsesJsxElementsWithoutErrors() {
        LintNode root = TsxLanguage.get().parse(JSX).root();

        assertEquals("program", root.kind());
        LintNode element = findKind(root, "jsx_element");
        assertNotNull(element, "the JSX element must surface on the tsx binding");
        assertNotNull(findKind(root, "jsx_opening_element"));
        assertNotNull(findKind(root, "jsx_closing_element"));
        assertNotNull(findKind(root, "jsx_attribute"));
        assertNotNull(findKind(element, "jsx_text"));
        assertFalse(hasError(root), "the tsx grammar must parse JSX without recovery nodes");
    }

    @Test
    void typescriptBindingDegradesExplicitlyOnTsxSyntax() {
        LintNode root = TypeScriptLanguage.get().parse(JSX).root();

        assertTrue(hasError(root),
                "the plain typescript grammar must surface ERROR nodes on JSX syntax — "
                        + "the documented degrade path, pinned here");
        assertNull(findKind(root, "jsx_element"),
                "the typescript grammar has no JSX element kinds");
        assertEquals(-1, TypeScriptLanguage.get().kindId("jsx_element"),
                "the kinds of one binding are not borrowed by the other");
        assertTrue(TsxLanguage.get().kindId("jsx_element") > 0,
                "only the tsx binding resolves the JSX kinds");
    }

    private boolean hasError(LintNode root) {
        for (LintNode node : root) {
            if (node.kind().equals("ERROR")) {
                return true;
            }
        }
        return false;
    }

    private LintNode findKind(LintNode root, String kind) {
        for (LintNode node : root) {
            if (node.kind().equals(kind)) {
                return node;
            }
        }
        return null;
    }
}
