package io.nop.lint.core.xml;

import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.SourceRange;

import java.util.List;

/**
 * A captured attribute value or element text: the node-shaped wrapper that
 * lets the shared {@code MetaVarEnv} hold scalar XML values with the same
 * same-name consistency semantics as node captures (leaf comparison = kind
 * plus text, so two captures of one name agree only when the values are
 * equal). Values never enter the tree; they exist only inside capture
 * environments.
 */
public final class XmlValueNode implements LintNode {

    private final String value;
    private final SourceRange range;

    XmlValueNode(String value, SourceRange range) {
        this.value = value;
        this.range = range;
    }

    /**
     * The captured value text.
     */
    public String value() {
        return value;
    }

    @Override
    public String kind() {
        return XNodeLintNode.TEXT_KIND;
    }

    @Override
    public int kindId() {
        return XmlTagKinds.idFor(kind());
    }

    @Override
    public boolean isNamed() {
        return false;
    }

    @Override
    public boolean isExtra() {
        return false;
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
        return value;
    }

    @Override
    public LintNode parent() {
        return null;
    }

    @Override
    public List<LintNode> children() {
        return List.of();
    }

    @Override
    public List<LintNode> namedChildren() {
        return List.of();
    }

    @Override
    public LintNode childByField(String fieldName) {
        return null;
    }

    @Override
    public io.nop.lint.core.node.NodeIterator iterator() {
        return new io.nop.lint.core.node.NodeIterator(this);
    }
}
