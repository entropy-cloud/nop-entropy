package io.nop.idea.plugin.lang.reference;

import java.util.Objects;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import io.nop.api.core.util.SourceLocation;
import io.nop.idea.plugin.lang.psi.XLangAttribute;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.xlang.xdef.IXDefAttribute;
import org.jetbrains.annotations.Nullable;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-10
 */
public class XLangAttributeReference extends XLangReferenceBase {

    public XLangAttributeReference(XLangAttribute myElement, TextRange myRangeInElement) {
        super(myElement, myRangeInElement);
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        XLangAttribute attr = (XLangAttribute) myElement;
        IXDefAttribute attrDef = attr.getXDefAttr();
        if (attrDef == null) {
            return null;
        }

        XLangTag tag = (XLangTag) attr.getParent();
        SourceLocation loc = attrDef.getLocation();
        // Note: SourceLocation#getPath() 得到的 jar 中的 vfs 路径会添加 classpath:_vfs 前缀
        String path = loc != null //
                      ? loc.getPath().replace("classpath:_vfs", "") //
                      : tag.getXDef().resourcePath();

        PsiElement[] targets = XmlPsiHelper.findPsiFilesByNopVfsPath(attr, path)
                                           .stream()
                                           .map((file) -> XmlPsiHelper.getPsiElementAt(file, loc, XmlAttribute.class))
                                           .filter(Objects::nonNull)
                                           .toArray(PsiElement[]::new);

        return targets[0];
    }
}
