/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

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

    public QualifiedNameNode(@NotNull ASTNode node) {
        super(node);
    }

    public IdentifierNode getIdentifier() {
        return (IdentifierNode) getFirstChild().getFirstChild();
    }

    public String getLastName() {
        PsiElement last = PsiTreeUtil.getDeepestLast(this);

        return last.getText();
    }

    public String getFullyName() {
        return getText();
    }
}
