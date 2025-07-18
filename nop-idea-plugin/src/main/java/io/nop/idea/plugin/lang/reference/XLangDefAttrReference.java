/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.reference;

import java.util.function.Function;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.nop.api.core.util.SourceLocation;
import io.nop.idea.plugin.lang.psi.XLangAttribute;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.vfs.NopVirtualFile;
import io.nop.xlang.xdef.IXDefAttribute;
import org.jetbrains.annotations.Nullable;

/**
 * 对属性定义的引用：指向属性的定义位置
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-10
 */
public class XLangDefAttrReference extends XLangReferenceBase {

    public XLangDefAttrReference(XLangAttribute myElement, TextRange myRangeInElement) {
        super(myElement, myRangeInElement);
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        XLangAttribute attr = (XLangAttribute) myElement;
        IXDefAttribute attrDef = attr.getDefAttr();
        if (attrDef == null) {
            return null;
        }

        String path = XmlPsiHelper.getNopVfsPath(attrDef);
        if (path == null) {
            return null;
        }

        SourceLocation loc = attrDef.getLocation();
        Function<PsiFile, PsiElement> targetResolver = (file) -> {
            PsiElement target = XmlPsiHelper.getPsiElementAt(file, loc, XLangAttribute.class);

            if (target == null) {
                target = XmlPsiHelper.getPsiElementAt(file, loc, XLangTag.class);

                if (target instanceof XLangTag tag) {
                    // Note: 在交叉定义时，属性定义中的属性名字与当前属性名字是不相同的
                    target = tag.getAttribute(attrDef.getName());
                }
            }
            return target;
        };

        return new NopVirtualFile(myElement, path, targetResolver);
    }
}
