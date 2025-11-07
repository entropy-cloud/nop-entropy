/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.impl.source.xml.SchemaPrefixReference;
import com.intellij.psi.impl.source.xml.XmlAttributeImpl;
import io.nop.idea.plugin.lang.reference.XLangAttributeReference;
import io.nop.idea.plugin.lang.reference.XLangXlibTagAttrReference;
import io.nop.idea.plugin.lang.xlib.XlibXDefAttribute;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdef.impl.XDefAttribute;
import io.nop.xlang.xdef.parse.XDefTypeDeclParser;
import org.jetbrains.annotations.NotNull;

/**
 * 属性，由名字（含名字空间）、等号和 {@link XLangAttributeValue} 组成
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-09
 */
public class XLangAttribute extends XmlAttributeImpl {

    @Override
    public String toString() {
        return getClass().getSimpleName() + ':' + getElementType() + "('" + getName() + "')";
    }

    @Override
    public boolean skipValidation() {
        // Note: 禁用 xml 的校验
        return true;
    }

    public XLangTag getParentTag() {
        return (XLangTag) getParent();
    }

    @Override
    public PsiReference @NotNull [] getReferences(@NotNull PsiReferenceService.Hints hints) {
        // 参考 XmlAttributeDelegate#getDefaultReferences
        String ns = getNamespacePrefix();
        String name = getLocalName();

        if (name.isEmpty() || isNamespaceDeclaration()) {
            return super.getReferences(hints);
        }

        // 保留对名字空间的引用，以支持对其做高亮、重命名等
        SchemaPrefixReference ref0 = !ns.isEmpty()
                                     ? new SchemaPrefixReference(this, TextRange.allOf(ns), ns, null)
                                     : null;

        int nameOffset = (ns.isEmpty() ? -1 : ns.length()) + 1;
        TextRange nameTextRange = TextRange.allOf(name).shiftRight(nameOffset);
        PsiReference ref1;

        IXDefAttribute defAttr = getDefAttr();
        if (defAttr instanceof XlibXDefAttribute xlibDefAttr) {
            ref1 = new XLangXlibTagAttrReference(this, nameTextRange, xlibDefAttr);
        } else {
            ref1 = new XLangAttributeReference(this, nameTextRange, defAttr);
        }

        return ref0 != null ? new PsiReference[] { ref0, ref1 } : new PsiReference[] { ref1 };
    }

    /** 获取当前属性在元模型中的定义 */
    public IXDefAttribute getDefAttr() {
        XLangTag tag = getParentTag();
        if (tag == null) {
            return null;
        }

        XLangTagMeta tagMeta = tag.getTagMeta();
        return tagMeta.getDefAttr(this);
    }

    public static boolean isNullOrErrorDefAttr(IXDefAttribute defAttr) {
        return defAttr == null || defAttr instanceof XDefAttributeWithError;
    }

    /** 类型为 {@code any} 的属性定义 */
    public static class XDefAttributeWithTypeAny extends XDefAttribute {
        private static final XDefTypeDecl STD_DOMAIN_ANY = new XDefTypeDeclParser().parseFromText(null, "any");

        public XDefAttributeWithTypeAny(String name) {
            setName(name);
            setType(STD_DOMAIN_ANY);
        }
    }

    /** 含错误信息的属性定义 */
    public static class XDefAttributeWithError extends XDefAttribute {
        final String errorMsg;

        public XDefAttributeWithError(String name, String errorMsg) {
            this.errorMsg = errorMsg;
            setName(name);
        }

        public String getErrorMsg() {
            return this.errorMsg;
        }
    }
}
