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
import com.intellij.psi.xml.XmlElement;
import io.nop.idea.plugin.lang.psi.XLangTag;
import org.jetbrains.annotations.Nullable;

/**
 * 对 xlib 函数标签的名字空间的引用识别
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-22
 */
public class XLangXlibTagNsReference extends XLangReferenceBase {
    private final String tagNs;
    private final XmlElement target;

    public XLangXlibTagNsReference(
            XLangTag myElement, TextRange myRangeInElement, String tagNs, XmlElement target
    ) {
        super(myElement, myRangeInElement);
        this.tagNs = tagNs;
        this.target = target;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        return target;
    }
}
