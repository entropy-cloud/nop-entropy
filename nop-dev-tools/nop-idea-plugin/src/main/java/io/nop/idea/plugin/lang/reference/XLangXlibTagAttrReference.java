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
import io.nop.idea.plugin.lang.xlib.XlibXDefAttribute;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.vfs.NopVirtualFile;
import io.nop.xlang.xpl.xlib.XlibConstants;
import org.jetbrains.annotations.Nullable;

/**
 * 对 xlib 函数标签的参数的引用识别
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-23
 */
public class XLangXlibTagAttrReference extends XLangReferenceBase {
    private final XlibXDefAttribute defAttr;

    public XLangXlibTagAttrReference(
            XLangAttribute myElement, TextRange myRangeInElement, XlibXDefAttribute defAttr
    ) {
        super(myElement, myRangeInElement);
        this.defAttr = defAttr;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        String path = XmlPsiHelper.getNopVfsPath(defAttr);
        if (path == null) {
            return null;
        }

        SourceLocation loc = defAttr.getLocation();
        Function<PsiFile, PsiElement> targetResolver = (file) -> {
            XLangTag tag = XmlPsiHelper.getPsiElementAt(file, loc, XLangTag.class);

            return tag != null ? tag.getAttribute(XlibConstants.NAME_NAME) : null;
        };

        return new NopVirtualFile(myElement, path, targetResolver);
    }
}
