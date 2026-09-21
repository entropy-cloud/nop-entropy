package io.nop.lint.java;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.node.LineIndex;
import io.nop.lint.core.node.SourceRange;
import io.nop.lint.core.suppress.SuppressionProvider;
import io.nop.lint.core.suppress.SuppressionSpan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Extracts {@code @SuppressWarnings} suppression spans from a parsed Java
 * tree (design 09 §3) by CST navigation: annotation nodes named
 * {@code SuppressWarnings} (simple or qualified) hang off their declaration's
 * {@code modifiers}, so the span range is the enclosing declaration node —
 * class, method, constructor, field, parameter, or local declaration.
 *
 * <p><b>Value forms (design 09 §3)</b>: {@code "nop-lint:<rule-id>"} (the
 * recommended form), a bare rule id (compatibility), and {@code "all"}. The
 * three forms are judged per string value, so array arguments contribute one
 * span per value. A {@code nop-lint:} value with a blank or malformed rule id
 * fails closed — an explicit suppression claim that cannot be parsed is a
 * contract violation, never a fabricated suppression (Minimum Rules #24).
 * Values outside the three forms (javac built-ins, PMD/ErrorProne aliases)
 * are recognized as bare rule ids by the compatibility rule; they match no
 * nop-lint rule and surface through the unused-directive check (the mute
 * knob is roadmap item 27). PMD/ErrorProne <em>alias semantics</em> (map the
 * alias to a migrated rule) belong to the migration manifest, item 29.</p>
 *
 * <p><b>Extraction vehicle (adjudicated, design 09 §3 增注)</b>: CST node
 * navigation via the {@link LintNode} facade behind the
 * {@link LintLanguage#suppressionProvider()} hook — the core engine consumes
 * spans without any Java grammar knowledge, and the provider shares the
 * binding's lifecycle (a provider is meaningless without its grammar).</p>
 */
public final class JavaSuppressWarningsProvider implements SuppressionProvider {

    /**
     * The annotation simple name this provider recognizes; qualified names
     * (e.g. {@code java.lang.SuppressWarnings}) match by their suffix.
     */
    private static final String ANNOTATION_NAME = "SuppressWarnings";
    private static final String NOP_LINT_PREFIX = "nop-lint:";
    private static final Pattern RULE_TOKEN = Pattern.compile("[A-Za-z0-9_.$/-]+");
    private static final String ARGUMENT_LIST_KIND = "annotation_argument_list";
    private static final String STRING_LITERAL_KIND = "string_literal";
    private static final String STRING_FRAGMENT_KIND = "string_fragment";
    private static final String MODIFIERS_KIND = "modifiers";
    private static final String ALL_VALUE = "all";

    private static final JavaSuppressWarningsProvider INSTANCE = new JavaSuppressWarningsProvider();

    private JavaSuppressWarningsProvider() {
    }

    /**
     * The canonical shared instance (stateless; shared for the same reason
     * {@link JavaLanguage#get()} is).
     */
    public static JavaSuppressWarningsProvider get() {
        return INSTANCE;
    }

    @Override
    public List<SuppressionSpan> extractSpans(LintTree tree) {
        Objects.requireNonNull(tree, "tree must not be null");
        LineIndex lines = new LineIndex(tree.source());
        List<SuppressionSpan> spans = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (LintNode node : tree.root()) {
            if (!isSuppressWarningsAnnotation(node)) {
                continue;
            }
            SourceRange declarationRange = declarationRange(node, lines);
            for (String value : stringValues(node)) {
                SuppressionSpan span = spanFor(node, value, declarationRange, lines);
                if (seen.add(span.range() + "|" + span.ruleIds())) {
                    spans.add(span);
                }
            }
        }
        return spans;
    }

    private boolean isSuppressWarningsAnnotation(LintNode node) {
        String kind = node.kind();
        if (!kind.equals("annotation") && !kind.equals("marker_annotation")) {
            return false;
        }
        LintNode name = node.childByField("name");
        return name != null && name.text().endsWith(ANNOTATION_NAME);
    }

    /**
     * The declaration the annotation scopes: through the {@code modifiers}
     * wrapper when one carries it, otherwise the annotation's direct parent.
     */
    private SourceRange declarationRange(LintNode annotation, LineIndex lines) {
        LintNode parent = annotation.parent();
        if (parent == null) {
            throw new NopLintException("@SuppressWarnings at line " + lineOf(annotation, lines)
                    + " has no enclosing declaration; annotation scope extraction requires one");
        }
        LintNode declaration = MODIFIERS_KIND.equals(parent.kind()) ? parent.parent() : parent;
        if (declaration == null) {
            throw new NopLintException("@SuppressWarnings at line " + lineOf(annotation, lines)
                    + " has no enclosing declaration; annotation scope extraction requires one");
        }
        return declaration.range();
    }

    /**
     * The annotation's string values in declaration order: every
     * {@code string_literal} in the argument list (directly, inside an
     * {@code element_value_array}, or as an {@code element_value_pair}
     * value). A marker annotation has none. Java escapes are not processed —
     * nop-lint rule ids carry none.
     */
    private List<String> stringValues(LintNode annotation) {
        List<String> values = new ArrayList<>();
        LintNode argumentList = null;
        for (LintNode child : annotation.children()) {
            if (ARGUMENT_LIST_KIND.equals(child.kind())) {
                argumentList = child;
                break;
            }
        }
        if (argumentList == null) {
            return values;
        }
        for (LintNode node : argumentList) {
            if (STRING_LITERAL_KIND.equals(node.kind())) {
                values.add(concatFragments(node));
            }
        }
        return values;
    }

    private String concatFragments(LintNode stringLiteral) {
        StringBuilder sb = new StringBuilder();
        for (LintNode node : stringLiteral) {
            if (STRING_FRAGMENT_KIND.equals(node.kind())) {
                sb.append(node.text());
            }
        }
        return sb.toString();
    }

    private SuppressionSpan spanFor(LintNode annotation, String value, SourceRange declarationRange,
                                    LineIndex lines) {
        SourceRange directiveRange = annotation.range();
        if (ALL_VALUE.equals(value)) {
            return SuppressionSpan.allRules(declarationRange, directiveRange);
        }
        if (value.startsWith(NOP_LINT_PREFIX)) {
            String ruleId = value.substring(NOP_LINT_PREFIX.length()).trim();
            if (ruleId.isEmpty() || !RULE_TOKEN.matcher(ruleId).matches()) {
                throw new NopLintException("@SuppressWarnings value '" + value + "' at line "
                        + lineOf(annotation, lines) + " carries the nop-lint prefix but not a "
                        + "parseable rule id (expected [A-Za-z0-9_.$/-]); fail-closed instead of "
                        + "fabricating a suppression");
            }
            return new SuppressionSpan(declarationRange, Set.of(ruleId), directiveRange);
        }
        return new SuppressionSpan(declarationRange, Set.of(value), directiveRange);
    }

    private static int lineOf(LintNode annotation, LineIndex lines) {
        return lines.lineOfByte(annotation.range().startByte());
    }
}
