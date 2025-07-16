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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.messages.NopPluginBundle;
import org.jetbrains.annotations.Nullable;

/**
 * 对父标签上的属性的引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-13
 */
public class XLangParentTagAttrReference extends XLangReferenceBase {
    private final String attrValue;

    public XLangParentTagAttrReference(XmlElement myElement, TextRange myRangeInElement, String attrValue) {
        super(myElement, myRangeInElement);
        this.attrValue = attrValue;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        XLangTag tag = PsiTreeUtil.getParentOfType(myElement, XLangTag.class);
        if (tag == null) {
            return null;
        }

        XmlAttribute target = tag.getAttribute(attrValue);

        if (target == null) {
            String msg = NopPluginBundle.message("xlang.annotation.reference.parent-tag-attr-not-found", attrValue);
            setUnresolvedMessage(msg);
        }
        return target;
    }
}
