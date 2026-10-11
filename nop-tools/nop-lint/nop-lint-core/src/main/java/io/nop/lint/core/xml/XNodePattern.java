package io.nop.lint.core.xml;

import java.util.List;

/**
 * A compiled XNode structural pattern (design 01 §3.5): one pattern element
 * with its tag (the kind equivalent), its attribute constraints, its text
 * constraint, and its literal element-children sequence. Matching semantics
 * live in {@link XNodePatternMatcher}; this type is the immutable compiled
 * data.
 *
 * <p>Dimension contract (§3.5 table + 增注): a dimension the pattern does not
 * declare is unconstrained — attributes are an open-world constraint set
 * (every declared attribute must exist on the candidate with a matching
 * value; undeclared candidate attributes are free), a pattern without text
 * constrains nothing about the candidate's text, and a pattern without
 * element children constrains nothing about the candidate's children. A
 * declared children sequence matches closed (lockstep, exact element count)
 * because that is the sequence semantics the {@code $$$} ellipsis contract is
 * shared with; the open-world need is served by leaving the sequence
 * undeclared or by the {@code has} relational operator.</p>
 */
public final class XNodePattern {

    /**
     * One declared attribute: the name plus the value constraint.
     *
     * @param name        the attribute name (never {@code x:}-prefixed — the
     *                    compiler rejects those)
     * @param spec        the value constraint
     * @param literal     the literal value for {@link ValueSpec#LITERAL};
     *                    null otherwise
     * @param captureName the capture name for {@link ValueSpec#CAPTURE};
     *                    null otherwise
     */
    public record AttrSpec(String name, ValueSpec spec, String literal, String captureName) {
    }

    /**
     * The value constraint of one declared attribute or text position.
     */
    public enum ValueSpec {
        /**
         * No constraint declared: the dimension is unconstrained (text only;
         * an attribute constraint always carries a spec).
         */
        ANY,
        /**
         * A literal value: exact (trimmed, for text) string equality.
         */
        LITERAL,
        /**
         * {@code $VAR}: captures the value; same-name consistency enforced by
         * the capture environment.
         */
        CAPTURE,
        /**
         * {@code $_}/{@code $_VAR}: matches any value, captures nothing, and
         * tolerates an absent attribute (§3.5: "缺省属性不匹配，除非用
         * {@code $_}"). In text position it matches any text.
         */
        DROP,
        /**
         * Bare {@code $$$}: matches any value (text: including empty) and
         * captures nothing. The attribute must still exist.
         */
        WILDCARD
    }

    private final String patternText;
    private final String tagName;
    private final int tagKindId;
    private final List<AttrSpec> attrs;
    private final ValueSpec textSpec;
    private final String literalText;
    private final String textCaptureName;
    private final List<XNodePattern> children;

    XNodePattern(String patternText, String tagName, int tagKindId, List<AttrSpec> attrs,
                 ValueSpec textSpec, String literalText, String textCaptureName,
                 List<XNodePattern> children) {
        this.patternText = patternText;
        this.tagName = tagName;
        this.tagKindId = tagKindId;
        this.attrs = List.copyOf(attrs);
        this.textSpec = textSpec;
        this.literalText = literalText;
        this.textCaptureName = textCaptureName;
        this.children = List.copyOf(children);
    }

    /**
     * The pattern source this was compiled from (diagnostics).
     */
    public String patternText() {
        return patternText;
    }

    /**
     * The pattern element's tag name (the kind equivalent).
     */
    public String tagName() {
        return tagName;
    }

    /**
     * The interned kind id of {@link #tagName()} — the O(1) prefilter input.
     */
    public int tagKindId() {
        return tagKindId;
    }

    /**
     * The declared attribute constraints, in declaration order.
     */
    public List<AttrSpec> attrs() {
        return attrs;
    }

    /**
     * The text constraint of the pattern element.
     */
    public ValueSpec textSpec() {
        return textSpec;
    }

    /**
     * The literal text for {@link ValueSpec#LITERAL} (already trimmed); null
     * otherwise.
     */
    public String literalText() {
        return literalText;
    }

    /**
     * The capture name for {@link ValueSpec#CAPTURE}; null otherwise.
     */
    public String textCaptureName() {
        return textCaptureName;
    }

    /**
     * The literal element-children patterns, in order; empty means the
     * candidate's children are unconstrained (open world).
     */
    public List<XNodePattern> children() {
        return children;
    }

    /**
     * The root kind ids this pattern can possibly match — always the single
     * tag id (v1 tag names are literal; a meta-var tag name is a compile-time
     * rejection).
     */
    public int[] possibleKindIds() {
        return new int[]{tagKindId};
    }
}
