/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import io.nop.core.type.IArrayType;
import io.nop.core.type.IGenericType;
import io.nop.core.type.impl.PredefinedPrimitiveType;
import io.nop.idea.plugin.utils.PsiClassHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link io.nop.xlang.xdef.XDefConstants#STD_DOMAIN_GENERIC_TYPE generic-type} 类型的值引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-15
 */
public class XLangStdDomainGenericTypeReference extends XLangReferenceBase {
    private final IGenericType type;

    public XLangStdDomainGenericTypeReference(PsiElement myElement, TextRange myRangeInElement, IGenericType type) {
        super(myElement, myRangeInElement);
        this.type = type;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        String className = resolveClassName(type);

        return PsiClassHelper.findClass(myElement, className);
    }

    @Override
    public Object @NotNull [] getVariants() {
        // TODO generic-type 类型的属性值补全较复杂，暂不处理
        return new Object[0];
    }

    private String resolveClassName(IGenericType type) {
        if (type instanceof PredefinedPrimitiveType p) {
            return p.getStdDataType().getJavaClass().getName();
        } else if (type instanceof IArrayType pa) {
            return resolveClassName(pa.getComponentType());
        }
        return type.getClassName();
    }
}
