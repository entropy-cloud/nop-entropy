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
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import io.nop.api.core.util.SourceLocation;
import io.nop.idea.plugin.lang.psi.XLangAttribute;
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

        SourceLocation loc = attrDef.getLocation();
        if (loc == null) {
            return null;
        }

        // Note: SourceLocation#getPath() 得到的 jar 中的 vfs 路径会添加 classpath:_vfs 前缀
        String path = loc.getPath().replace("classpath:_vfs", "");

        Function<PsiFile, PsiElement> targetResolver = (file) -> {
            PsiElement target = XmlPsiHelper.getPsiElementAt(file, loc, XmlAttribute.class);

            if (target == null) {
                target = XmlPsiHelper.getPsiElementAt(file, loc, XmlTag.class);

                if (target instanceof XmlTag tag) {
                    // Note: 在交叉定义时，属性定义中的属性名字与当前属性名字是不相同的
                    target = tag.getAttribute(attrDef.getName());
                }
            }
            return target;
        };

        return new NopVirtualFile(myElement, path, targetResolver);
    }
}
