package io.nop.lint.java.semantic;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import io.nop.lint.core.node.LintNode;

import java.util.HashMap;
import java.util.Map;

/**
 * The byte-range bridge between a tree-sitter {@link io.nop.lint.core.node.LintTree}
 * and a JavaParser {@link CompilationUnit} (design 06 §6.3, roadmap item 26).
 *
 * <p>Construction is named-only on the lint side (both sides hold ≈1:1
 * named node counts; anonymous tokens inflate the multi-candidate set) and
 * delegates the Java-side position surface to {@link JavaNodeIndex} (the
 * resolver's primary entry). The four boundary-resolution rules of
 * design 06 §6.3 map onto:</p>
 * <ol>
 * <li>exact hit — the range HashMap</li>
 * <li>minimal containment — {@link JavaNodeIndex#minimalContaining}</li>
 * <li>kind-equivalence tie-break — the explicit seed table
 * ({@code MethodDeclaration ↔ method_declaration}, …) when several nodes
 * share a range</li>
 * <li>best-effort fallback — every lookup returns null when no node
 * qualifies; the mapping is an accelerator, never a correctness premise</li>
 * </ol>
 *
 * <p>Range semantics: tree-sitter ranges are UTF-8 byte spans with an
 * exclusive end; JavaParser ranges are 1-based line/UTF-16-col with an
 * INCLUSIVE end column — conversion uses
 * {@code byteOf(endLine, endCol + 1)} (the F3 trap).</p>
 *
 * <p>The bidirectional LintNode queries are the forward-looking TypedLintNode
 * surface (plan F2 adjudication): the engine feeds the resolver positions,
 * so the production consumer in this item is {@link JavaNodeIndex}; the
 * LintNode queries deliver the §6.3 contract and are pinned by tests.</p>
 */
public final class ASTMapping {

    /**
     * The kind-equivalence seed (design 06 §6.3 rule 3): JavaParser node
     * class simple name ⇄ tree-sitter kind name. Entries only disambiguate
     * same-range multi-candidates; unmatched pairs yield no mapping (null).
     */
    private static final Map<String, String> KIND_EQUIVALENCE = Map.ofEntries(
            Map.entry("MethodDeclaration", "method_declaration"),
            Map.entry("ClassOrInterfaceDeclaration", "class_declaration"),
            Map.entry("FieldDeclaration", "field_declaration"),
            Map.entry("ConstructorDeclaration", "constructor_declaration"),
            Map.entry("BlockStmt", "block"),
            Map.entry("ReturnStmt", "return_statement"),
            Map.entry("IfStmt", "if_statement"),
            Map.entry("ExpressionStmt", "expression_statement"),
            Map.entry("VariableDeclarationExpr", "local_variable_declaration"),
            Map.entry("ImportDeclaration", "import_declaration"));

    private final JavaNodeIndex javaIndex;
    private final Map<Long, LintNode> exactLintByRange;

    private ASTMapping(JavaNodeIndex javaIndex, Map<Long, LintNode> exactLintByRange) {
        this.javaIndex = javaIndex;
        this.exactLintByRange = exactLintByRange;
    }

    /**
     * Builds the mapping over the two parsed trees; both were parsed from
     * the same {@code source} bytes, and {@code lineCols} converts
     * JavaParser positions onto those bytes.
     */
    public static ASTMapping build(io.nop.lint.core.node.LintTree lintTree, CompilationUnit cu,
                                   LineColBytes lineCols) {
        JavaNodeIndex javaIndex = JavaNodeIndex.build(cu, lineCols);
        Map<Long, LintNode> exactLint = new HashMap<>();
        for (LintNode node : lintTree.root()) {
            if (!node.isNamed()) {
                continue;
            }
            exactLint.putIfAbsent(key(node.range().startByte(), node.range().endByte()), node);
        }
        return new ASTMapping(javaIndex, exactLint);
    }

    private static long key(int startByte, int endByte) {
        return ((long) startByte << 32) | (endByte & 0xFFFFFFFFL);
    }

    /**
     * Design 06 §6.3 {@code getJavaNode}: the JavaParser node for the lint
     * node — exact range hit first (rule 1, kind-equivalence tie-break
     * rule 3), else the minimal containing JavaParser node (rule 2), else
     * null (rule 4 fallback). The {@code expectedType} filter rejects a
     * hit of an unexpected node class (null), so callers can pin the
     * contract type.
     */
    public Node getJavaNode(LintNode lintNode, Class<? extends Node> expectedType) {
        int start = lintNode.range().startByte();
        int end = lintNode.range().endByte();
        Node exact = javaIndex.exactAt(start, end);
        if (exact != null && kindMatches(exact, lintNode.kind())) {
            return typed(exact, expectedType);
        }
        // an exact-range alias whose kind does not match (the two grammars
        // name the same span differently) falls through to the positional
        // containment rule — the containment hit carries no kind gate
        Node containing = javaIndex.minimalContaining(start);
        return containing == null ? null : typed(containing, expectedType);
    }

    /**
     * Design 06 §6.3 {@code getLintNode}: the lint node whose range exactly
     * equals the JavaParser node's converted range (kind-equivalence
     * tie-break applies); null when no exact counterpart exists.
     */
    public LintNode getLintNode(Node javaNode, LineColBytes lineCols) {
        int[] range = javaIndex.rangeOf(javaNode);
        if (range == null) {
            return null;
        }
        LintNode exact = exactLintByRange.get(key(range[0], range[1]));
        if (exact == null) {
            return null;
        }
        String equivalent = KIND_EQUIVALENCE.get(javaNode.getClass().getSimpleName());
        return equivalent == null || equivalent.equals(exact.kind()) ? exact : null;
    }

    /**
     * The position-key surface the resolver consumes.
     */
    public JavaNodeIndex javaIndex() {
        return javaIndex;
    }

    private static boolean kindMatches(Node javaNode, String lintKind) {
        String equivalent = KIND_EQUIVALENCE.get(javaNode.getClass().getSimpleName());
        return equivalent == null || equivalent.equals(lintKind);
    }

    private static Node typed(Node node, Class<? extends Node> expectedType) {
        return expectedType.isInstance(node) ? node : null;
    }
}
