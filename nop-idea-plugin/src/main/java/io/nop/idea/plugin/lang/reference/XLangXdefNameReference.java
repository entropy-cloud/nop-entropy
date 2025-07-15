package io.nop.idea.plugin.lang.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.utils.PsiClassHelper;
import org.jetbrains.annotations.Nullable;

/**
 * {@link io.nop.xlang.xdef.XDefKeys#NAME xdef:name} 的值引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-15
 */
public class XLangXdefNameReference extends XLangReferenceBase {
    private final String beanPackage;
    private final String attrValue;

    public XLangXdefNameReference(
            XmlElement myElement, TextRange myRangeInElement, //
            String beanPackage, String attrValue
    ) {
        super(myElement, myRangeInElement);
        this.beanPackage = beanPackage;
        this.attrValue = attrValue;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        String className = beanPackage + '.' + attrValue;

        PsiClass clazz = PsiClassHelper.findClass(myElement, className);

        if (clazz == null) {
            String msg = NopPluginBundle.message("xlang.annotation.reference.xdef-name-class-not-found", className);
            setUnresolvedMessage(msg);

            return null;
        }
        return clazz;
    }
}
