package io.nop.lint.java.semantic;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hand-computed anchors for the position-keyed L3 queries (roadmap item 34
 * Phase 1, plan Decision 3/R1 B-1): the identity-matched record lookup must
 * keep shadowing re-declarations independent (the by-name table would
 * collapse them), fields and method-outside declarations fail closed, and
 * the constant/use-count/self-assignment answers mirror the item 30 kernel.
 */
public class TestDataflowQueries {

    private final DataflowQueries queries = new DataflowQueries();
    private final JavaParser parser = new JavaParser(new ParserConfiguration());

    private MethodDeclaration method(String source, String name) {
        return parser.parse(source).getResult().orElseThrow()
                .findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getNameAsString().equals(name))
                .findFirst().orElseThrow();
    }

    @Test
    public void constantValueAnswersForACompileTimeConstant() {
        String source = """
                class Demo {
                    void m() {
                        String mode = "fast";
                        int retries = 3;
                        int computed = retries * 2;
                    }
                }
                """;
        MethodDeclaration m = method(source, "m");

        int[] mode = pos(source, "mode = \"fast\"", 0);
        assertEquals("fast", queries.constantValue(m, mode[0], mode[1]));

        int[] computed = pos(source, "computed =", 0);
        assertNull(queries.constantValue(m, computed[0], computed[1]),
                "a non-constant initializer is the legitimate null answer");
    }

    @Test
    public void shadowingRedeclarationsKeepIndependentAnswers() {
        String source = """
                class Demo {
                    void m() {
                        int x = 1;
                        if (x > 0) {
                            int x = "shadow";
                        }
                    }
                }
                """;
        MethodDeclaration m = method(source, "m");

        int[] outer = pos(source, "x = 1", 0);
        int[] inner = pos(source, "x = \"shadow\"", 0);

        assertEquals("1", queries.constantValue(m, outer[0], outer[1]),
                "the outer declaration keeps its own answer (identity-matched, not by name)");
        assertEquals("shadow", queries.constantValue(m, inner[0], inner[1]),
                "the inner re-declaration is not collapsed into the outer one");
    }

    @Test
    public void useCountAndSelfAssignmentMirrorTheKernel() {
        String source = """
                class Demo {
                    void m() {
                        int used = 1;
                        int dead = 2;
                        int self = 0;
                        self = self;
                        int later = used + used;
                    }
                }
                """;
        MethodDeclaration m = method(source, "m");

        int[] used = pos(source, "used = 1", 0);
        int[] dead = pos(source, "dead = 2", 0);
        int[] self = pos(source, "self = 0", 0);

        assertEquals(2, queries.useCount(m, used[0], used[1]));
        assertEquals(0, queries.useCount(m, dead[0], dead[1]));
        assertTrue(queries.isSelfAssigned(m, self[0], self[1]));
        assertFalse(queries.isSelfAssigned(m, used[0], used[1]));
    }

    @Test
    public void fieldAndMethodOutsideDeclarationsFailClosed() {
        String source = """
                class Demo {
                    private int field = 1;

                    void m() {
                        int local = field + 1;
                    }
                }
                """;
        MethodDeclaration m = method(source, "m");

        int[] fieldRef = pos(source, "field + 1", 0);
        int[] localDecl = pos(source, "local = field + 1", 0);

        assertThrows(IllegalArgumentException.class,
                () -> queries.constantValue(m, fieldRef[0], fieldRef[1]),
                "a field reference is not a local declaration (v1 surface)");
        // the local declaration itself answers
        assertNull(queries.constantValue(m, localDecl[0], localDecl[1]),
                "field initializer participation is not constant → null is legitimate");
    }

    static int[] pos(String source, String token, int occurrence) {
        int index = -1;
        for (int i = 0; i <= occurrence; i++) {
            index = source.indexOf(token, index + 1);
            if (index < 0) {
                throw new IllegalStateException("token not found: " + token + "#" + occurrence);
            }
        }
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        int column = index - source.lastIndexOf('\n', index);
        return new int[]{line, column};
    }
}
