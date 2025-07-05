package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * <code>java.lang.String</code>：
 * <pre>
 * QualifiedNameNode(qualifiedName)
 *   RuleSpecNode(qualifiedName_name_)
 *     IdentifierNode(identifier)
 *       PsiElement(Identifier)('java')
 *   PsiElement('.')('.')
 *   QualifiedNameNode(qualifiedName)
 *     RuleSpecNode(qualifiedName_name_)
 *       IdentifierNode(identifier)
 *         PsiElement(Identifier)('lang')
 *     PsiElement('.')('.')
 *     QualifiedNameNode(qualifiedName)
 *       RuleSpecNode(qualifiedName_name_)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('String')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-04
 */
public class QualifiedNameNode extends RuleSpecNode {
    private IdentifierNode identifier;

    public QualifiedNameNode(@NotNull ASTNode node) {
        super(node);
    }

    public IdentifierNode getIdentifier() {
        if (identifier == null || !identifier.isValid()) {
            identifier = (IdentifierNode) getFirstChild().getFirstChild();
        }
        return identifier;
    }

    public String getLastName() {
        PsiElement last = PsiTreeUtil.getDeepestLast(this);

        return last.getText();
    }

    public String getFullyName() {
        return getText();
    }
}
