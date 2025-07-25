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
import io.nop.xlang.xdef.XDefTypeDecl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 对数据域的通用引用
 * <p/>
 * 主要实现代码补全
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-21
 */
public class XLangStdDomainGeneralReference extends XLangReferenceBase {
    private final XDefTypeDecl defType;

    public XLangStdDomainGeneralReference(PsiElement myElement, TextRange myRangeInElement, XDefTypeDecl defType) {
        super(myElement, myRangeInElement);
        this.defType = defType;
    }

    @Override
    public boolean isSoft() {
        // 标记为"软引用"，不触发缺省的错误检查：不影响自定义的 Annotator 检查
        return true;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        return null;
    }

    @Override
    public Object @NotNull [] getVariants() {
        return switch (defType.getStdDomain()) {
            case "boolean" -> new Object[] { Boolean.TRUE.toString(), Boolean.FALSE.toString() };
            default -> new Object[0];
        };
    }
}
