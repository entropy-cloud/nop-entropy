package io.nop.lint.core.xml;

import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.pattern.MetaVarSyntax;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compiles an XML pattern snippet (one pattern element, design 01 §3.5)
 * into an {@link XNodePattern}. The snippet parses through the platform's
 * XNode parser — the same parser the source side uses, so pattern and source
 * share one tree model and one coordinate system.
 *
 * <p>Meta-var grammar: attribute values and text are classified by the same
 * {@link MetaVarSyntax} the tree-sitter path uses (§3.5: "meta-var 语义一致"),
 * with the position-specific v1 surface:</p>
 *
 * <ul>
 * <li>attribute value: literal → exact equality; {@code $VAR} → capture;
 * {@code $_VAR}/{@code $_} → drop (absence tolerated); bare {@code $$$} →
 * presence-required wildcard; named {@code $$$VAR}, anonymous {@code $$}/
 * {@code $$VAR}, and the bare {@code $} form are compile-time rejections
 * (fail-closed — the scalar position has no sequence semantics to give
 * them).</li>
 * <li>text: identical classification on the trimmed content; bare
 * {@code $$$} matches any text including empty (§3.5).</li>
 * <li>tag name: literal XML names only — a meta-var tag name fails to parse
 * as an XML name and is rejected.</li>
 * <li>{@code x:}-prefixed pattern attributes are rejected (§3.5 namespace
 * row: XDSL internals do not participate in matching; a rule asking for them
 * targets the wrong layer, e.g. the delta merge input).</li>
 * </ul>
 *
 * <p>Mixed-content pattern elements (text plus element children) are
 * rejected: v1 carries text through the content dimension and children
 * through the element sequence, and a mixed pattern element has no
 * unambiguous reading (explicit adjudication, §3.5 增注).</p>
 */
public final class XNodePatternCompiler {

    private XNodePatternCompiler() {
    }

    /**
     * Compiles one pattern element.
     *
     * @throws NopLintException when the text is blank, does not parse as
     *                          exactly one XML element, or uses a form
     *                          outside the v1 surface above
     */
    public static XNodePattern compile(String patternText) {
        if (patternText == null || patternText.isBlank()) {
            throw new NopLintException("xml pattern must not be blank");
        }
        XNode root;
        try {
            root = XNodeParser.instance().keepComment(true).parseFromText(null, patternText);
        } catch (Exception e) {
            throw new NopLintException("xml pattern failed to parse: " + e.getMessage()
                    + " (pattern: " + describe(patternText) + ")", e);
        }
        if (root == null || root.isTextNode()) {
            throw new NopLintException("xml pattern must contain exactly one element (got: "
                    + describe(patternText) + ")");
        }
        return convert(root, patternText);
    }

    private static XNodePattern convert(XNode element, String patternText) {
        String tagName = element.getTagName();
        if (tagName == null || tagName.isEmpty()) {
            throw new NopLintException("xml pattern element has a blank tag name (pattern: "
                    + describe(patternText) + ")");
        }

        List<XNodePattern.AttrSpec> attrs = new ArrayList<>();
        Map<String, ValueWithLocation> sourceAttrs = element.attrValueLocs();
        if (sourceAttrs != null) {
            for (Map.Entry<String, ValueWithLocation> entry : sourceAttrs.entrySet()) {
                String name = entry.getKey();
                if (name.startsWith("x:")) {
                    throw new NopLintException("xml pattern declares the XDSL-internal attribute '"
                            + name + "', which never participates in matching (design 01 §3.5: "
                            + "matching applies to the merged final model)");
                }
                Classified classified = classify(entry.getValue().asString(),
                        "attribute '" + name + "'", patternText);
                attrs.add(new XNodePattern.AttrSpec(name, classified.spec(), classified.literal(),
                        classified.captureName()));
            }
        }

        boolean hasContent = element.hasContent();
        List<XNode> elementChildren = new ArrayList<>();
        for (XNode child : element.getChildren()) {
            if (child.isTextNode()) {
                String text = child.contentText();
                if (text != null && !text.isBlank()) {
                    throw new NopLintException("xml pattern element '" + tagName
                            + "' mixes text content with element children, which has no "
                            + "unambiguous v1 reading (fail-closed; pattern: "
                            + describe(patternText) + ")");
                }
                continue;
            }
            elementChildren.add(child);
        }

        XNodePattern.ValueSpec textSpec = XNodePattern.ValueSpec.ANY;
        String literalText = null;
        String textCapture = null;
        if (hasContent) {
            ValueWithLocation content = element.content();
            String raw = content.asString("");
            String trimmed = raw == null ? "" : raw.trim();
            // CDATA content participates as its text value (§3.5 增注).
            Classified classified = classify(trimmed, "text", patternText);
            textSpec = classified.spec();
            literalText = classified.literal();
            textCapture = classified.captureName();
        }

        List<XNodePattern> children = new ArrayList<>();
        for (XNode child : elementChildren) {
            children.add(convert(child, patternText));
        }

        return new XNodePattern(patternText, tagName, XmlTagKinds.idFor(tagName), attrs,
                textSpec, literalText, textCapture, children);
    }

    private record Classified(XNodePattern.ValueSpec spec, String literal, String captureName) {
    }

    /**
     * Classifies one attribute-value or trimmed-text token into the pattern's
     * value spec. Both positions share the MetaVarSyntax grammar; the label
     * only feeds the rejection message.
     */
    private static Classified classify(String raw, String positionLabel, String patternText) {
        boolean isText = "text".equals(positionLabel);
        String token = isText ? raw.trim() : raw;
        MetaVarSyntax.rejectReserved(token);
        MetaVarSyntax.Spec spec = MetaVarSyntax.parse(token);
        if (spec == null) {
            return new Classified(XNodePattern.ValueSpec.LITERAL, token, null);
        }
        if (spec.isDrop()) {
            return new Classified(XNodePattern.ValueSpec.DROP, null, null);
        }
        if (spec.isMulti()) {
            if (spec.name() == null) {
                // Bare $$$: text — any content including empty; attribute —
                // presence-required wildcard.
                return new Classified(XNodePattern.ValueSpec.WILDCARD, null, null);
            }
            throw reject(token, positionLabel, patternText);
        }
        if (spec.isAnonymous() || spec.name() == null) {
            // Anonymous ($$/$$VAR) and the bare '$' form have no scalar
            // semantics in v1 (fail-closed).
            throw reject(token, positionLabel, patternText);
        }
        return new Classified(XNodePattern.ValueSpec.CAPTURE, null, spec.name());
    }

    private static NopLintException reject(String form, String where, String patternText) {
        return new NopLintException("xml pattern uses '" + form + "' in " + where
                + " position, which has no v1 semantics (supported: literal, $VAR, $_VAR, bare "
                + "$$$; fail-closed) (pattern: " + describe(patternText) + ")");
    }

    private static String describe(String patternText) {
        String trimmed = patternText.strip();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80) + "...";
    }
}
