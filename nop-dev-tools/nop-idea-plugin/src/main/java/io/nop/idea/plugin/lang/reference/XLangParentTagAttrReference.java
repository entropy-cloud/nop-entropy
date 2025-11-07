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
import com.intellij.psi.xml.XmlElement;
import io.nop.idea.plugin.lang.psi.XLangAttribute;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.lang.psi.XLangTagMeta;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.nop.idea.plugin.lang.reference.XLangReferenceHelper.XLANG_NAME_COMPARATOR;

/**
 * 对父标签上的属性的引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-13
 */
public class XLangParentTagAttrReference extends XLangReferenceBase {
    private final String attrName;

    public XLangParentTagAttrReference(XmlElement myElement, TextRange myRangeInElement, String attrName) {
        super(myElement, myRangeInElement);
        this.attrName = attrName;
    }

    private XLangTag getParentTag() {
        return PsiTreeUtil.getParentOfType(myElement, XLangTag.class);
    }

    private XLangAttribute getParentAttr() {
        return PsiTreeUtil.getParentOfType(myElement, XLangAttribute.class);
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        XLangTag tag = getParentTag();
        if (tag == null) {
            return null;
        }

        XLangAttribute target = (XLangAttribute) tag.getAttribute(attrName);

        if (target == null) {
            String msg = NopPluginBundle.message("xlang.annotation.reference.parent-tag-attr-not-found", attrName);
            setUnresolvedMessage(msg);
        }
        // 不能引用属性自身
        else if (target == getParentAttr()) {
            String msg = NopPluginBundle.message("xlang.annotation.reference.parent-tag-attr-self-referenced",
                                                 attrName);
            setUnresolvedMessage(msg);

            return null;
        }

        return target;
    }

    /** @return {@link #attrName} 所在标签上的可引用的属性名 */
    @Override
    public Object @NotNull [] getVariants() {
        // Note: 在自动补全阶段，DSL 结构很可能是不完整的，只能从 xml 角度做分析
        XLangTag tag = getParentTag();
        if (tag == null) {
            return new Object[0];
        }

        XLangAttribute attr = getParentAttr();
        String attrName = attr != null ? attr.getName() : null;

        XLangTagMeta tagMeta = tag.getTagMeta();
        return XmlPsiHelper.getTagAttrNames(tag) //
                           .stream() //
                           .filter(new XLangXdefKeyAttrReference.TagAttrNameFilter(tagMeta)) //
                           .filter((name) -> !name.equals(attrName)) //
                           .sorted(XLANG_NAME_COMPARATOR) //
                           .toArray();
    }
}
