/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.reference;

import java.util.function.Predicate;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.xlang.xdef.XDefKeys;
import io.nop.xlang.xdsl.XDslKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.nop.idea.plugin.lang.reference.XLangReferenceHelper.XLANG_NAME_COMPARATOR;

/**
 * {@link io.nop.xlang.xdef.XDefKeys#KEY_ATTR xdef:key-attr} 的值引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-13
 */
public class XLangXdefKeyAttrReference extends XLangReferenceBase implements PsiPolyVariantReference {
    private final String attrValue;

    public XLangXdefKeyAttrReference(XmlElement myElement, TextRange myRangeInElement, String attrValue) {
        super(myElement, myRangeInElement);
        this.attrValue = attrValue;
    }

    private XLangTag getParentTag() {
        return PsiTreeUtil.getParentOfType(myElement, XLangTag.class);
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        ResolveResult[] results = multiResolve(false);

        if (results.length == 0) {
            String msg = NopPluginBundle.message("xlang.annotation.reference.xdef-key-attr-not-found", attrValue);
            setUnresolvedMessage(msg);
        }

        return results.length == 1 ? results[0].getElement() : null;
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        XLangTag tag = getParentTag();
        if (tag == null) {
            return ResolveResult.EMPTY_ARRAY;
        }

        return XmlPsiHelper.getAttrsFromChildTag(tag, attrValue).stream() //
                           .map(PsiElementResolveResult::new) //
                           .toArray(ResolveResult[]::new);
    }

    @Override
    public Object @NotNull [] getVariants() {
        // Note: 在自动补全阶段，DSL 结构很可能是不完整的，只能从 xml 角度做分析
        XLangTag tag = getParentTag();
        if (tag == null) {
            return new Object[0];
        }

        return XmlPsiHelper.getCommonAttrNamesFromChildTag(tag) //
                           .stream() //
                           .filter(new TagAttrNameFilter(tag)) //
                           .sorted(XLANG_NAME_COMPARATOR) //
                           .toArray();
    }

    static class TagAttrNameFilter implements Predicate<String> {
        private final XLangTag refTag;

        TagAttrNameFilter(XLangTag refTag) {
            this.refTag = refTag;
        }

        @Override
        public boolean test(String name) {
            XDefKeys xdefKeys = refTag.getXDefKeys();
            XDslKeys xdslKeys = refTag.getXDslKeys();

            String ns = StringHelper.getNamespace(name);

            return !xdefKeys.NS.equals(ns) && !xdslKeys.NS.equals(ns);
        }
    }
}
