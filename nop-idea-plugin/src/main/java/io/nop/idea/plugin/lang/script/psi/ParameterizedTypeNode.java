package io.nop.idea.plugin.lang.script.psi;

import java.util.ArrayList;
import java.util.List;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import io.nop.idea.plugin.utils.PsiClassHelper;
import org.jetbrains.annotations.NotNull;

/**
 * <code>new</code> 语句中的类名节点
 * <p/>
 * <code>String</code>：
 * <pre>
 * ParameterizedTypeNode(parameterizedTypeNode)
 *   RuleSpecNode(qualifiedName_)
 *     QualifiedNameNode(qualifiedName)
 *       RuleSpecNode(qualifiedName_name_)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('String')
 * </pre>
 *
 * <code>Abc.Def</code>：
 * <pre>
 * ParameterizedTypeNode(parameterizedTypeNode)
 *   RuleSpecNode(qualifiedName_)
 *     QualifiedNameNode(qualifiedName)
 *       RuleSpecNode(qualifiedName_name_)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('Abc')
 *       PsiElement('.')('.')
 *       QualifiedNameNode(qualifiedName)
 *         RuleSpecNode(qualifiedName_name_)
 *           IdentifierNode(identifier)
 *             PsiElement(Identifier)('Def')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class ParameterizedTypeNode extends RuleSpecNode {
    private QualifiedNameNode qualifiedName;

    public ParameterizedTypeNode(@NotNull ASTNode node) {
        super(node);
    }

    public QualifiedNameNode getQualifiedName() {
        if (qualifiedName == null || !qualifiedName.isValid()) {
            qualifiedName = (QualifiedNameNode) getFirstChild().getFirstChild();
        }
        return qualifiedName;
    }

    @Override
    protected PsiReference @NotNull [] doGetReferences() {
        List<PsiClassAndTextRange> result = getClassAndTextRanges();

        // 若未找到已导入的类，则尝试按包查找
        if (result.isEmpty()) {
            String fqn = getText();

            return PsiClassHelper.createJavaClassReferences(this, fqn, 0);
        }

        return PsiClassAndTextRange.createReferences(this, result);
    }

    public PsiClass getParameterizedType() {
        List<PsiClassAndTextRange> result = getClassAndTextRanges();

        String fqn = getText().replace(" ", "");
        if (result.isEmpty()) {
            // 按全类名处理
            return PsiClassHelper.findClass(getProject(), fqn);
        }

        PsiClass clazz = result.get(result.size() - 1).clazz();
        String clazzName = clazz.getQualifiedName();

        return clazzName != null //
               && (fqn.equals(clazzName) //
                   || clazzName.endsWith('.' + fqn)) //
               ? clazz : null;
    }

    protected List<PsiClassAndTextRange> getClassAndTextRanges() {
        QualifiedNameNode qnn = getQualifiedName();
        IdentifierNode identifier = qnn.getIdentifier();

        PsiClass clazz = identifier.getDataType();

        List<PsiClassAndTextRange> result = new ArrayList<>();
        findInnerClass(clazz, qnn, 0, result);

        return result;
    }

    protected void findInnerClass(
            PsiClass clazz, QualifiedNameNode qnn, int offset, List<PsiClassAndTextRange> result
    ) {
        if (clazz == null) {
            return;
        }

        result.add(new PsiClassAndTextRange(clazz, qnn.getTextRangeInParent().shiftRight(offset)));

        PsiElement sub = qnn.getLastChild();
        if (!(sub instanceof QualifiedNameNode subQnn)) {
            return;
        }

        String subName = subQnn.getIdentifier().getText();
        PsiClass subClazz = clazz.findInnerClassByName(subName, true);

        findInnerClass(subClazz, subQnn, qnn.getStartOffsetInParent(), result);
    }
}
