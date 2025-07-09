package io.nop.idea.plugin.lang;

import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.lang.script.XLangScriptLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * XScript 脚本的 {@link LanguageInjector}
 * <p/>
 * 用于识别脚本语言，以提供高亮、引用、自动补全等编码支持
 * <p/>
 * 通过 <code>InjectedLanguageManager.getInstance(host.getProject()).findInjectedElementAt(host, offset);</code>
 * 可以从目标元素内查找到已注入的其他语言节点
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-25
 */
public class XLangScriptLanguageInjector implements LanguageInjector {

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

        String langType = tag.getAttributeValue("lang");
        if (StringHelper.isEmpty(langType)) {
            langType = "xlang";
        }

        Language lang = switch (langType) {
            case "xlang" -> XLangScriptLanguage.INSTANCE;
            default -> null;
        };

        if (lang != null) {
            // Note: 第二个参数为有效文本在 host 中的范围，而 XmlText 的文本是不包含 CDATA 标签的，
            // 因此，直接根据文本内容确定有效文本的范围即可
            registrar.addPlace(lang, TextRange.create(0, host.getTextLength()), null, null);
        }
    }
}
