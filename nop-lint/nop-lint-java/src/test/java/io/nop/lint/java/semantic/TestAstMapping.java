package io.nop.lint.java.semantic;

import io.nop.lint.core.node.LintTree;
import io.nop.lint.java.JavaLanguage;
import io.nop.lint.core.lang.LintLanguage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The conversion and mapping contract tests (roadmap item 26, plan
 * 2026-09-24-0500-1): the UTF-16 col ⇄ UTF-8 byte conversion (the F3
 * end-col-inclusive trap and the multi-byte drift), and the design 06 §6.3
 * ASTMapping four boundary rules with the bidirectional queries.
 */
public class TestAstMapping {

    private static final LintLanguage JAVA = new JavaLanguage();

    // ==================== LineColBytes ====================

    @Test
    public void asciiLineConvertsArithmetically() {
        LineColBytes cols = new LineColBytes(LineColBytes.utf8("abc\ndef\n"));
        assertEquals(0, cols.byteOf(1, 1));
        assertEquals(2, cols.byteOf(1, 3));
        assertEquals(4, cols.byteOf(2, 1));
    }

    @Test
    public void multiByteLineConvertsExactly() {
        // the round-1 spike case: a naive (col-1) arithmetic drifts on
        // multi-byte prefixes — 中🎉 is 3+4=7 bytes but 3 UTF-16 units
        String source = "int x = 1; // 中🎉\nfoo(1);";
        LineColBytes cols = new LineColBytes(LineColBytes.utf8(source));
        int prefixBytes = "int x = 1; // ".getBytes(StandardCharsets.UTF_8).length
                + "中🎉".getBytes(StandardCharsets.UTF_8).length + 1;
        assertEquals(prefixBytes, cols.byteOf(2, 1),
                "line 2 starts after the multi-byte prefix");
    }

    @Test
    public void lineBeyondTheSourceDegradesToSourceLength() {
        LineColBytes cols = new LineColBytes(LineColBytes.utf8("abc\n"));
        assertEquals(4, cols.byteOf(2, 1), "the tail position equals the source length");
        assertEquals(4, cols.byteOf(9, 9));
    }

    // ==================== ASTMapping ====================

    private record Parsed(LintTree tree, com.github.javaparser.ast.CompilationUnit cu,
                          LineColBytes cols, ASTMapping mapping) {
    }

    private Parsed parse(String source) {
        LintTree tree = JAVA.parse(source);
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        LineColBytes cols = new LineColBytes(bytes);
        com.github.javaparser.ast.CompilationUnit cu = JavaTypeResolver.parseUnit(bytes);
        return new Parsed(tree, cu, cols, ASTMapping.build(tree, cu, cols));
    }

    @Test
    public void exactRangeHitResolvesTheMethod() {
        Parsed parsed = parse("class A {\n    void run() {\n    }\n}\n");
        // find the tree-sitter method_declaration node
        io.nop.lint.core.node.LintNode method = findKind(parsed.tree().root(), "method_declaration");

        com.github.javaparser.ast.Node mapped = parsed.mapping()
                .getJavaNode(method, com.github.javaparser.ast.Node.class);

        assertEquals(com.github.javaparser.ast.body.MethodDeclaration.class, mapped.getClass(),
                "rule 1: the exact range hit resolves to the kind-equivalent JavaParser node");
    }

    @Test
    public void containmentResolvesWhenExactMisses() {
        Parsed parsed = parse("class A {\n    void run() {\n        int x = 1;\n    }\n}\n");
        // a tree-sitter expression_statement around `int x = 1;` — the
        // JavaParser side models it as VariableDeclarationExpr inside
        // ExpressionStmt, whose range differs by the semicolon
        io.nop.lint.core.node.LintNode statement = findKind(parsed.tree().root(),
                "local_variable_declaration");
        if (statement == null) {
            statement = findKind(parsed.tree().root(), "expression_statement");
        }

        com.github.javaparser.ast.Node mapped = parsed.mapping()
                .getJavaNode(statement, com.github.javaparser.ast.Node.class);

        assertEquals(true, mapped != null, "a boundary-mismatched node still maps by containment");
        int[] range = parsed.mapping().javaIndex().rangeOf(mapped);
        assertEquals(true, range[0] <= statement.range().endByte()
                        && statement.range().startByte() <= range[1],
                "rule 2 is bidirectional containment (design 06 §6.3: 或反之，容差 = 注释/空白)"
                        + " — the tree-sitter declaration spans its semicolon, the JavaParser"
                        + " expression does not");
    }

    @Test
    public void reverseLookupFindsTheExactLintNode() {
        Parsed parsed = parse("class A {\n    void run() {\n    }\n}\n");
        io.nop.lint.core.node.LintNode method = findKind(parsed.tree().root(), "method_declaration");

        com.github.javaparser.ast.Node mapped = parsed.mapping()
                .getJavaNode(method, com.github.javaparser.ast.Node.class);
        io.nop.lint.core.node.LintNode back = mapped == null ? null
                : parsed.mapping().getLintNode(mapped, parsed.cols());

        assertEquals(method, back, "the round trip lands on the same lint node");
    }

    @Test
    public void positionOutsideTheUnitReturnsNull() {
        Parsed parsed = parse("class A {\n}\n");
        assertNull(parsed.mapping().javaIndex().minimalContaining(9999),
                "rule 4: the fallback is null, never a guess");
    }

    private io.nop.lint.core.node.LintNode findKind(io.nop.lint.core.node.LintNode root,
                                                    String kind) {
        for (io.nop.lint.core.node.LintNode node : root) {
            if (kind.equals(node.kind())) {
                return node;
            }
        }
        return null;
    }
}
