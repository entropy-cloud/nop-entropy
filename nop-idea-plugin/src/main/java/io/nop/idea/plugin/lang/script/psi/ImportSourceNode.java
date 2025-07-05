package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import io.nop.idea.plugin.utils.PsiClassHelper;
import org.jetbrains.annotations.NotNull;

/**
 * <code>import</code> 语句中导入的类名的节点
 * <p/>
 * <code>java.lang.String</code>：
 * <pre>
 * ImportSourceNode(ast_importSource)
 *   QualifiedNameNode(qualifiedName)
 *     RuleSpecNode(qualifiedName_name_)
 *       IdentifierNode(identifier)
 *         PsiElement(Identifier)('java')
 *     PsiElement('.')('.')
 *     QualifiedNameNode(qualifiedName)
 *       RuleSpecNode(qualifiedName_name_)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('lang')
 *       PsiElement('.')('.')
 *       QualifiedNameNode(qualifiedName)
 *         RuleSpecNode(qualifiedName_name_)
 *           IdentifierNode(identifier)
 *             PsiElement(Identifier)('String')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-29
 */
public class ImportSourceNode extends RuleSpecNode {
    private QualifiedNameNode qualifiedName;

    public ImportSourceNode(@NotNull ASTNode node) {
        super(node);
    }

    public QualifiedNameNode getQualifiedName() {
        if (qualifiedName == null || !qualifiedName.isValid()) {
            qualifiedName = (QualifiedNameNode) getFirstChild();
        }
        return qualifiedName;
    }

    /** 构造 Java 相关的引用对象，从而支持自动补全、引用跳转、文档显示等 */
    @Override
    protected PsiReference @NotNull [] doGetReferences() {
        QualifiedNameNode qnn = getQualifiedName();
        String fqn = qnn.getFullyName();

        return PsiClassHelper.createJavaClassReferences(this, fqn, 0);
    }
}
