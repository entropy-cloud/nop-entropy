/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import io.nop.idea.plugin.lang.script.reference.PredefinedTypeReference;
import org.jetbrains.annotations.NotNull;

/**
 * 预定义类型节点
 * <p/>
 * <pre>
 * RuleSpecNode(typeNameNode_predefined)
 *   PsiElement('string')('string')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-05
 */
public class TypeNameNodePredefinedNode extends RuleSpecNode {

    public TypeNameNodePredefinedNode(@NotNull ASTNode node) {
        super(node);
    }

    public PsiElement getTypeName() {
        return getLastChild();
    }

    public PsiClass getPredefinedType() {
        String typeName = getTypeName().getText();

        return PredefinedTypeReference.getPredefinedType(this, typeName);
    }

    @Override
    protected PsiReference @NotNull [] doGetReferences() {
        PsiElement typeName = getTypeName();
        TextRange textRange = typeName.getTextRangeInParent();

        PredefinedTypeReference ref = new PredefinedTypeReference(this, textRange, typeName);

        return new PsiReference[] { ref };
    }
}
