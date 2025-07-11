package io.nop.idea.plugin.lang.reference;

import java.util.Objects;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import io.nop.api.core.util.SourceLocation;
import io.nop.idea.plugin.lang.psi.XLangAttribute;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.xlang.xdef.IXDefAttribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-10
 */
public class XLangAttributeReference extends XLangReferenceBase implements PsiPolyVariantReference {

    public XLangAttributeReference(XLangAttribute myElement, TextRange myRangeInElement) {
        super(myElement, myRangeInElement);
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        ResolveResult[] results = multiResolve(false);

        return results.length > 0 ? results[0].getElement() : null;
    }

    /** 返回多个引用元素 */
    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        XLangAttribute attr = (XLangAttribute) myElement;
        IXDefAttribute attrDef = attr.getXDefAttr();
        if (attrDef == null) {
            return ResolveResult.EMPTY_ARRAY;
        }

        // 若引用属性自身，则直接返回
        if (attr.isSelfDefAttr(attrDef)) {
            return new ResolveResult[] {
                    new PsiElementResolveResult(attr)
            };
        }

        SourceLocation loc = attrDef.getLocation();
        if (loc == null) {
            return ResolveResult.EMPTY_ARRAY;
        }

        // Note: SourceLocation#getPath() 得到的 jar 中的 vfs 路径会添加 classpath:_vfs 前缀
        String path = loc.getPath().replace("classpath:_vfs", "");

        return XmlPsiHelper.findPsiFilesByNopVfsPath(attr, path).stream() //
                           .map((file) -> {
                               PsiElement target = XmlPsiHelper.getPsiElementAt(file, loc, XmlAttribute.class);

                               if (target == null) {
                                   target = XmlPsiHelper.getPsiElementAt(file, loc, XmlTag.class);

                                   if (target instanceof XmlTag t) {
                                       target = t.getAttribute(attrDef.getName());
                                   }
                               }
                               return target;
                           }) //
                           .filter(Objects::nonNull) //
                           .map(PsiElementResolveResult::new) //
                           .toArray(ResolveResult[]::new);
    }
}
