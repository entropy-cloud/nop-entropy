package io.nop.lint.core.pattern;

import io.nop.lint.core.NopLintException;

import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles pattern source text into {@link SourcePattern} values: expando
 * preprocessing, backend parse, CST-to-PatternNode conversion, effective-node
 * extraction, kind precomputation (design 01 §4).
 *
 * <p>Conversion rules (measured against the java grammar, see plan 03
 * baseline): missing <em>unnamed</em> nodes (zero-width trailing {@code ;})
 * are dropped; missing <em>named</em> nodes mean incomplete pattern code and
 * are rejected; ERROR recovery nodes are stripped when they wrap exactly one
 * child over the same text span, otherwise the pattern is rejected (design 04
 * §8: a pattern must be valid code). Extraction descends from the root along
 * single-child chains until a multi-child internal, a terminal, or a
 * meta-var.</p>
 */
public final class SourcePatternCompiler {

    private SourcePatternCompiler() {
    }

    /**
     * Compiles {@code patternText} for {@code language}.
     *
     * @throws NopLintException when the pattern is empty, does not parse into
     *                          valid code (unrecoverable ERROR, missing named
     *                          node), has a bare multi meta-var as its root,
     *                          or uses reserved meta-var forms
     */
    public static SourcePattern compile(String patternText, LintLanguage language) {
        if (patternText == null || patternText.isBlank()) {
            throw new NopLintException("pattern must not be blank");
        }
        LintTree tree = language.parse(language.preprocessPattern(patternText));
        PatternNode root = convertRoot(tree.root());
        PatternNode effective = extractEffective(root);
        if (effective instanceof MetaVarNode metaVar && metaVar.shape() == MetaVarNode.Shape.MULTI) {
            throw new NopLintException(
                    "pattern root cannot be a multi meta-var: " + patternText);
        }
        return new SourcePattern(language, patternText, effective, possibleKindIds(effective));
    }

    /**
     * Compiles a contextual pattern: the context source is parsed and the
     * first (pre-order) node of kind {@code selector} — the context root
     * excluded, a deliberate tightening against degenerate selectors — is
     * compiled as the pattern root without further extraction (the selector
     * pins the root).
     */
    public static SourcePattern contextual(String selector, String context, LintLanguage language) {
        if (selector == null || selector.isBlank()) {
            throw new NopLintException("selector must not be blank");
        }
        if (context == null || context.isBlank()) {
            throw new NopLintException("context must not be blank");
        }
        LintTree tree = language.parse(language.preprocessPattern(context));
        LintNode selected = findSelector(tree.root(), selector);
        PatternNode root = convertRoot(selected);
        return new SourcePattern(language, context, root, possibleKindIds(root));
    }

    private static LintNode findSelector(LintNode root, String selector) {
        for (LintNode node : root) {
            if (node != root && node.kind().equals(selector)) {
                return node;
            }
        }
        throw new NopLintException(
                "selector kind not found in context: " + selector);
    }

    /**
     * Converts the whole tree rooted at {@code rootNode}, rejecting
     * unrecoverable parse artifacts. The root itself must convert to a node
     * (a dropped root would mean a blank match target).
     */
    private static PatternNode convertRoot(LintNode rootNode) {
        PatternNode root = convert(rootNode);
        if (root == null) {
            throw new NopLintException("pattern did not parse into a node");
        }
        return root;
    }

    /**
     * Converts one pattern CST node; null means "drop this node" (missing
     * unnamed tokens). ERROR stripping happens here so wrappers anywhere in
     * the tree (bare {@code $$$} in a class body, bare expressions) vanish
     * before extraction.
     */
    private static PatternNode convert(LintNode node) {
        if (node.isMissing()) {
            if (node.isNamed()) {
                throw new NopLintException(
                        "pattern is incomplete code (missing " + node.kind() + ")");
            }
            return null;
        }
        if (isErrorKind(node)) {
            return stripError(node);
        }
        if (node.isNamed()) {
            return convertNamed(node);
        }
        return new TerminalNode(node.kindId(), node.text(), false);
    }

    private static boolean isErrorKind(LintNode node) {
        return "ERROR".equals(node.kind()) || "_ERROR".equals(node.kind());
    }

    /**
     * An ERROR node is transparent when it wraps exactly one child over the
     * same text span (the recovery wrapper the backend inserts for bare
     * expressions and multi-var member positions). Anything else is a real
     * syntax error in the pattern.
     */
    private static PatternNode stripError(LintNode node) {
        List<LintNode> children = new ArrayList<>(node.children().size());
        for (LintNode child : node.children()) {
            if (!child.isMissing()) {
                children.add(child);
            }
        }
        if (children.size() == 1 && children.get(0).text().equals(node.text())) {
            PatternNode inner = convert(children.get(0));
            if (inner == null) {
                throw new NopLintException(
                        "pattern is incomplete code (missing node inside ERROR at " + node.text() + ")");
            }
            return inner;
        }
        throw new NopLintException(
                "pattern is not valid code: \"" + node.text() + "\"");
    }

    private static PatternNode convertNamed(LintNode node) {
        List<PatternNode> children = new ArrayList<>();
        for (LintNode child : node.children()) {
            PatternNode converted = convert(child);
            if (converted != null) {
                children.add(converted);
            }
        }
        // Meta-var classification is leaf-only: the wrapper chain above the
        // token (program → ERROR → identifier) also carries the token text,
        // and only the leaf occurrence itself is a capture.
        if (children.isEmpty()) {
            MetaVarSyntax.rejectReserved(node.text());
            MetaVarSyntax.Spec spec = MetaVarSyntax.parse(node.text());
            if (spec != null) {
                return new MetaVarNode(spec.shape(), spec.name(), node.text());
            }
            // Named leaves compile to text-carrying terminals (upstream
            // semantics): kind alone would let `dao` match `get`.
            return new TerminalNode(node.kindId(), node.text(), true);
        }
        return new InternalNode(node.kindId(), children);
    }

    /**
     * Descends along single-child chains (program wrappers, stripped ERROR
     * nodes, statement wrappers) to the first multi-child internal, terminal,
     * or meta-var.
     */
    private static PatternNode extractEffective(PatternNode node) {
        while (node instanceof InternalNode internal && internal.children().size() == 1) {
            node = internal.children().get(0);
        }
        return node;
    }

    private static int[] possibleKindIds(PatternNode root) {
        if (root instanceof InternalNode internal) {
            return new int[]{internal.kindId()};
        }
        if (root instanceof TerminalNode terminal) {
            return new int[]{terminal.kindId()};
        }
        // Meta-var roots match any kind.
        return new int[0];
    }
}
