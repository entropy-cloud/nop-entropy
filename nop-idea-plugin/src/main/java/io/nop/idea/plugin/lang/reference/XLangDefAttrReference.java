package io.nop.idea.plugin.lang.reference;

import java.util.function.Function;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import io.nop.api.core.util.SourceLocation;
import io.nop.idea.plugin.lang.psi.XLangAttribute;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.vfs.NopVirtualFile;
import io.nop.xlang.xdef.IXDefAttribute;
import org.jetbrains.annotations.Nullable;

/**
 * 对属性定义的引用
 * <p/>
 * 指向属性的定义位置
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-10
 */
public class XLangDefAttrReference extends XLangReferenceBase {

    public XLangDefAttrReference(XLangAttribute myElement, TextRange myRangeInElement) {
        super(myElement, myRangeInElement);
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        XLangAttribute attr = (XLangAttribute) myElement;
        IXDefAttribute attrDef = attr.getXDefAttr();
        if (attrDef == null) {
            return null;
        }

        // 若为声明属性（定义属性名及其类型），则直接返回
        if (attr.isDeclaredDefAttr(attrDef)) {
            return attr;
        }

        SourceLocation loc = attrDef.getLocation();
        if (loc == null) {
            return null;
        }

        Project project = getElement().getProject();
        // Note: SourceLocation#getPath() 得到的 jar 中的 vfs 路径会添加 classpath:_vfs 前缀
        String path = loc.getPath().replace("classpath:_vfs", "");

        Function<PsiFile, PsiElement> targetResolver = (file) -> {
            PsiElement target = XmlPsiHelper.getPsiElementAt(file, loc, XmlAttribute.class);

            if (target == null) {
                target = XmlPsiHelper.getPsiElementAt(file, loc, XmlTag.class);

                if (target instanceof XmlTag t) {
                    target = t.getAttribute(attrDef.getName());
                }
            }
            return target;
        };

        return new NopVirtualFile(project, path, targetResolver);
    }
}
