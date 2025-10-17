/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.reference;

import java.util.Arrays;
import java.util.Objects;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import io.nop.idea.plugin.lang.psi.XLangAttribute;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.xlang.xdef.IXDefNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.nop.idea.plugin.lang.reference.XLangReferenceHelper.XLANG_NAME_COMPARATOR;

/**
 * {@link io.nop.xlang.xdsl.XDslKeys#PROTOTYPE x:prototype} 的值引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-13
 */
public class XLangXPrototypeReference extends XLangReferenceBase {
    private final String attrValue;

    public XLangXPrototypeReference(XmlElement myElement, TextRange myRangeInElement, String attrValue) {
        super(myElement, myRangeInElement);
        this.attrValue = attrValue;
    }

    private XLangTag getParentTag() {
        return PsiTreeUtil.getParentOfType(myElement, XLangTag.class);
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        XLangTag tag = getParentTag();
        if (tag == null) {
            return null;
        }

        XLangTag parentTag = tag.getParentTag();
        if (parentTag == null) {
            String msg = NopPluginBundle.message("xlang.annotation.reference.x-prototype-no-parent");
            setUnresolvedMessage(msg);

            return null;
        }

        String keyAttr = getKeyAttrName(tag, parentTag);

        XLangTag protoTag = (XLangTag) XmlPsiHelper.getChildTagByAttr(parentTag, keyAttr, attrValue);
        if (protoTag == null) {
            String msg = keyAttr == null
                         ? NopPluginBundle.message("xlang.annotation.reference.x-prototype-tag-not-found",
                                                   attrValue)
                         : NopPluginBundle.message("xlang.annotation.reference.x-prototype-attr-not-found",
                                                   keyAttr,
                                                   attrValue);
            setUnresolvedMessage(msg);

            return null;
        }
        // 不能引用自身
        else if (protoTag == tag) {
            String msg = keyAttr == null
                         ? NopPluginBundle.message("xlang.annotation.reference.x-prototype-tag-self-referenced",
                                                   attrValue)
                         : NopPluginBundle.message("xlang.annotation.reference.x-prototype-attr-self-referenced",
                                                   keyAttr,
                                                   attrValue);
            setUnresolvedMessage(msg);

            return null;
        }

        // 定位到目标属性或标签上
        return getKeyAttrElement(protoTag, keyAttr);
    }

    /** @return {@link io.nop.xlang.xdsl.XDslKeys#PROTOTYPE x:prototype} 可引用的值（即，在兄弟节点上指定的唯一属性值） */
    @Override
    public Object @NotNull [] getVariants() {
        // Note: 在自动补全阶段，DSL 结构很可能是不完整的，只能从 xml 角度做分析
        XLangTag tag = getParentTag();
        XLangTag parentTag = tag != null ? tag.getParentTag() : null;
        if (parentTag == null) {
            return new Object[0];
        }

        String keyAttr = getKeyAttrName(tag, parentTag);

        return Arrays.stream(parentTag.getChildren())
                     .filter((child) -> child != tag && child instanceof XLangTag)
                     .map((child) -> (XLangTag) child)
                     .map((child) -> getKeyAttrElement(child, keyAttr))
                     .filter(Objects::nonNull)
                     .map((child) -> child instanceof XLangAttribute attr ? attr.getValue() : child.getName())
                     .filter(Objects::nonNull)
                     .sorted(XLANG_NAME_COMPARATOR)
                     .toArray();
    }

    private String getKeyAttrName(XLangTag tag, XLangTag parentTag) {
        // 仅从父节点中取引用到的子节点
        // io.nop.xlang.delta.DeltaMerger#mergePrototype
        IXDefNode defNode = tag.getSchemaDefNode();
        IXDefNode parentDefNode = parentTag.getSchemaDefNode();

        String keyAttr = parentDefNode.getXdefKeyAttr();

        if (keyAttr == null) {
            keyAttr = defNode.getXdefUniqueAttr();
        }
        return keyAttr;
    }

    private PsiNamedElement getKeyAttrElement(XLangTag tag, String keyAttr) {
        return keyAttr != null ? tag.getAttribute(keyAttr) : tag;
    }
}
