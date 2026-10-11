package io.nop.lint.core.xml;

import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.NodeIterator;
import io.nop.lint.core.node.SourceRange;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@link LintNode} facade over the platform's read-only XNode model (the
 * XML path of design 01 §1/§3.5: no tree-sitter grammar, matching runs on the
 * platform's own parser). Three node flavors:
 *
 * <ul>
 * <li><strong>element</strong> — one XNode element; kind = tag name (the
 * §3.5 "tag name matching is kind matching" contract), range = the start-tag
 * span, text = the start-tag source slice. Attributes are a separate matching
 * dimension exposed via {@link #attrValue(String)}, never child nodes.</li>
 * <li><strong>text trivia</strong> — an inter-element non-blank text node
 * (the parser's {@code #} text children in mixed content). Unnamed: matchers
 * step over it, because element text is matched through the trimmed
 * content dimension, not the child sequence (§3.5 增注: explicit v1
 * adjudication).</li>
 * <li><strong>comment trivia</strong> — the XNode-attached preceding comment
 * of a node, surfaced as an extra child so the always-on suppression scanner
 * (design 09 §2) and the matcher's comment-skip policy see it without any
 * XML-specific branch. Its text is the raw {@code <!--...-->} source slice,
 * its range the exact span, so directive byte math is consistent.</li>
 * </ul>
 *
 * <p>Instances are value-stable for one parse: the same tree position always
 * yields the same facade object, so facade identity is node identity (the
 * meta-var consistency checks rely on it). Facades are built only by
 * {@link XmlSourceParser}.</p>
 */
public final class XNodeLintNode implements LintNode {

    /**
     * The kind name of text trivia nodes (the parser's {@code #} text tag).
     */
    public static final String TEXT_KIND = "#text";

    /**
     * The kind name of the synthetic comment trivia nodes.
     */
    public static final String COMMENT_KIND = "#comment";

    enum Flavor { ELEMENT, TEXT, COMMENT }

    private final Flavor flavor;
    private final XNode element;
    private final String rawText;
    private final SourceRange range;
    private final XNodeLintNode parent;
    private List<LintNode> children;
    private List<LintNode> namedChildren;
    private final Map<String, String> attrValues;

    XNodeLintNode(Flavor flavor, XNode element, String rawText, SourceRange range,
                  XNodeLintNode parent, Map<String, String> attrValues) {
        this.flavor = flavor;
        this.element = element;
        this.rawText = rawText;
        this.range = range;
        this.parent = parent;
        this.attrValues = attrValues == null ? Map.of() : attrValues;
    }

    /**
     * Attaches the child facades after recursive construction (the children
     * reference this node as their parent, so they can only be built after
     * this node exists). Called exactly once by the builder.
     */
    void attach(List<LintNode> childList) {
        if (children != null) {
            throw new IllegalStateException("facade node already attached: " + this);
        }
        this.children = childList.isEmpty() ? List.of() : List.copyOf(childList);
        List<LintNode> named = new ArrayList<>(childList.size());
        for (LintNode child : childList) {
            if (child.isNamed()) {
                named.add(child);
            }
        }
        this.namedChildren = named.isEmpty() ? List.of() : List.copyOf(named);
    }

    /**
     * The underlying XNode element (element flavor only); null on trivia.
     */
    public XNode xnode() {
        return element;
    }

    /**
     * The decoded value of one attribute, or null when absent. {@code x:}
     * -prefixed XDSL internals are ordinary entries here — the matching
     * layer never consults attributes the pattern does not declare, and
     * pattern-declared {@code x:} attributes are rejected at compile time
     * (design 01 §3.5 namespace row).
     */
    @Override
    public String attrValue(String name) {
        return attrValues.get(name);
    }

    /**
     * The element's text content, decoded; empty when the element has none.
     * CDATA content participates as its text value (§3.5 增注: explicit v1
     * adjudication).
     */
    public String contentText() {
        if (element == null) {
            return rawText == null ? "" : rawText;
        }
        ValueWithLocation content = element.content();
        if (content == null || content.isNull()) {
            return "";
        }
        String text = content.asString("");
        return text == null ? "" : text;
    }

    Flavor flavor() {
        return flavor;
    }

    @Override
    public String kind() {
        return switch (flavor) {
            case ELEMENT -> element.getTagName();
            case TEXT -> TEXT_KIND;
            case COMMENT -> COMMENT_KIND;
        };
    }

    @Override
    public int kindId() {
        return XmlTagKinds.idFor(kind());
    }

    @Override
    public boolean isNamed() {
        return flavor == Flavor.ELEMENT;
    }

    @Override
    public boolean isExtra() {
        return flavor == Flavor.COMMENT;
    }

    @Override
    public boolean isMissing() {
        return false;
    }

    @Override
    public SourceRange range() {
        return range;
    }

    @Override
    public String text() {
        return rawText == null ? "" : rawText;
    }

    @Override
    public LintNode parent() {
        return parent;
    }

    @Override
    public List<LintNode> children() {
        return children == null ? List.of() : children;
    }

    @Override
    public List<LintNode> namedChildren() {
        return namedChildren == null ? List.of() : namedChildren;
    }

    @Override
    public LintNode childByField(String fieldName) {
        // XNode has no grammar field slots (design 01 §3.5: no CST layer);
        // the relational field constraint is rejected at XML rule compile
        // time, so this can only be reached from non-XML callers.
        return null;
    }

    @Override
    public NodeIterator iterator() {
        return new NodeIterator(this);
    }

    @Override
    public String toString() {
        return "XNodeLintNode[" + kind() + "@" + range().startByte() + "]";
    }
}
