package io.nop.idea.plugin.lang;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import io.nop.commons.util.StringHelper;
import org.jetbrains.annotations.NotNull;

/**
 * XScript 脚本的 {@link LanguageInjector}
 * <p/>
 * 用于识别脚本语言，以提供高亮、引用、自动补全等编码支持
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-25
 */
public class XScriptLanguageInjector implements LanguageInjector {

    @Override
    public void getLanguagesToInject(
            @NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces registrar
    ) {
        if (!(host instanceof XmlText)) {
            return;
        }

        PsiElement parent = host.getParent();
        if (!(parent instanceof XmlTag tag)) {
            return;
        }

        if (!"c:script".equals(tag.getName())) {
            return;
        }

        String lang = tag.getAttributeValue("lang");
        if (StringHelper.isEmpty(lang)) {
            lang = "xlang";
        }

        registrar.addPlace(JavaLanguage.INSTANCE, TextRange.create(0, host.getTextLength()), null, null);
    }
}
