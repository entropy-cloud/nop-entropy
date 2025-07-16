/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.script.psi;

import java.util.ArrayList;
import java.util.List;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import io.nop.idea.plugin.lang.script.reference.QualifiedNameReference;
import org.jetbrains.annotations.NotNull;

/**
 * 类名节点
 * <p/>
 * <code>String</code>：
 * <pre>
 * QualifiedNameRootNode(qualifiedName_)
 *   QualifiedNameNode(qualifiedName)
 *     RuleSpecNode(qualifiedName_name_)
 *       IdentifierNode(identifier)
 *         PsiElement(Identifier)('String')
 * </pre>
 *
 * <code>Abc.Def</code>：
 * <pre>
 * QualifiedNameRootNode(qualifiedName_)
 *   QualifiedNameNode(qualifiedName)
 *     RuleSpecNode(qualifiedName_name_)
 *       IdentifierNode(identifier)
 *         PsiElement(Identifier)('Abc')
 *     PsiElement('.')('.')
 *     QualifiedNameNode(qualifiedName)
 *       RuleSpecNode(qualifiedName_name_)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('Def')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class QualifiedNameRootNode extends RuleSpecNode {

    public QualifiedNameRootNode(@NotNull ASTNode node) {
        super(node);
    }

    public QualifiedNameNode getQualifiedName() {
        return (QualifiedNameNode) getFirstChild();
    }

    @Override
    protected PsiReference @NotNull [] doGetReferences() {
        List<QualifiedNameReference> result = createReferences();

        return result.toArray(PsiReference.EMPTY_ARRAY);
    }

    public PsiClass getQualifiedType() {
        List<QualifiedNameReference> result = createReferences();
        if (result.isEmpty()) {
            return null;
        }

        String fqn = getText().replace(" ", "");
        QualifiedNameReference ref = result.get(result.size() - 1);
        PsiElement element = ref.resolve();

        if (!(element instanceof PsiClass clazz)) {
            return null;
        }

        String className = clazz.getQualifiedName();

        return className != null //
               && (fqn.equals(className) //
                   || className.endsWith('.' + fqn)) //
               ? clazz : null;
    }

    protected List<QualifiedNameReference> createReferences() {
        QualifiedNameNode qnn = getQualifiedName();

        List<QualifiedNameReference> result = new ArrayList<>();
        createReferences(null, qnn, 0, result);

        return result;
    }

    protected void createReferences(
            QualifiedNameReference parentReference, QualifiedNameNode qnn, int offset,
            List<QualifiedNameReference> result
    ) {
        IdentifierNode identifier = qnn.getIdentifier();
        // Note: 取相对于 qnn 的 TextRange 并做偏移
        TextRange textRange = identifier.getParent().getTextRangeInParent().shiftRight(offset);

        QualifiedNameReference ref = new QualifiedNameReference(this, textRange, identifier, parentReference);
        result.add(ref);

        PsiElement sub = qnn.getLastChild();
        if (!(sub instanceof QualifiedNameNode subQnn)) {
            return;
        }

        createReferences(ref, subQnn, subQnn.getStartOffsetInParent() + offset, result);
    }
}
