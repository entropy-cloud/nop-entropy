package io.nop.lint.java;

import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.semantic.LocalTypeIndex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Java local type index (the LTYPE substrate, roadmap-free repo
 * convention 2026-10-22): declaration bindings — locals, {@code var}
 * inference, fields, parameters, same-file method returns — answer exactly
 * from the file; everything else is null (never a guess).
 */
public class TestJavaLocalTypeIndex {

    private final LocalTypeIndex index = JavaLanguage.get()
            .localTypeIndex(parse());

    private static LintTree parse() {
        return JavaLanguage.get().parse("""
                import java.util.regex.Pattern;

                public class Demo {
                    private static final Pattern DASH = Pattern.compile("a+");
                    private String label = "x";

                    String m(String s, int n) {
                        var p = Pattern.compile("b+");
                        String local = s + n;
                        boolean hit = DASH.matcher(local).matches();
                        return helper().split(",")[0];
                    }

                    String helper() {
                        return "a,b";
                    }
                }
                """);
    }

    private String typeOfIdentifier(String text, int occurrence) {
        int seen = 0;
        for (LintNode node : parse().root()) {
            if ("identifier".equals(node.kind()) && text.equals(node.text())) {
                seen++;
                if (seen == occurrence) {
                    return index.typeOf(node);
                }
            }
        }
        return null;
    }

    @Test
    public void parameterAndLocalBindingsAnswer() {
        // the parameter's declaration identifier resolves to String
        assertEquals("String", typeOfIdentifier("s", 1));
        // the var-inferred Pattern local resolves to Pattern
        assertEquals("Pattern", typeOfIdentifier("p", 1));
        // the String-typed local resolves to String
        assertEquals("String", typeOfIdentifier("local", 1));
    }

    @Test
    public void fieldAndMethodReturnBindingsAnswer() {
        // the Pattern field resolves to Pattern (never flagged as String)
        assertEquals("Pattern", typeOfIdentifier("DASH", 1));
        // same-file method return type: helper() answers String
        assertTrue(returnsOfStringHelper());
    }

    private boolean returnsOfStringHelper() {
        for (LintNode node : parse().root()) {
            if ("method_invocation".equals(node.kind())
                    && "helper()".equals(node.text())) {
                return "String".equals(index.typeOf(node));
            }
        }
        return false;
    }

    @Test
    public void unknownShapesStayNull() {
        // an unresolvable name is null — never a guess
        assertNull(typeOfIdentifier("hit", 1));
        assertFalse("String".equals(typeOfIdentifier("hit", 1)));
    }
}
