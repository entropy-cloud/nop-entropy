/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.script.reference;

import com.intellij.psi.PsiElement;
import io.nop.idea.plugin.lang.script.psi.ExpressionNode;
import org.jetbrains.annotations.Nullable;

/**
 * 对象方法引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-06
 */
public class ObjectMethodReference extends ObjectMemberReference {

    public ObjectMethodReference(ExpressionNode myElement) {
        super(myElement);
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        return ((ExpressionNode) myElement).getObjectMethod();
    }
}
