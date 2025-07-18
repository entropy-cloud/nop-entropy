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
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.vfs.NopVirtualFile;
import io.nop.xlang.xdef.IXDefNode;
import org.jetbrains.annotations.Nullable;

/**
 * 对节点定义的引用：指向节点的定义位置
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-17
 */
public class XLangTagReference extends XLangReferenceBase {

    public XLangTagReference(XLangTag myElement, TextRange myRangeInElement) {
        super(myElement, myRangeInElement);
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        XLangTag tag = (XLangTag) myElement;
        IXDefNode defNode = tag.getSchemaDefNode();

        String path = XmlPsiHelper.getNopVfsPath(defNode);
        if (path == null) {
            return null;
        }

        SourceLocation loc = defNode.getLocation();
        Function<PsiFile, PsiElement> targetResolver = (file) -> XmlPsiHelper.getPsiElementAt(file,
                                                                                              loc,
                                                                                              XLangTag.class);

        return new NopVirtualFile(myElement, path, targetResolver);
    }
}
