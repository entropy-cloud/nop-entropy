package io.nop.idea.plugin.lang.script;

import com.intellij.lang.Language;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-27
 */
public class XLangScriptLanguage extends Language {
    public static final XLangScriptLanguage INSTANCE = new XLangScriptLanguage();

    private XLangScriptLanguage() {
        super((Language) null, "XLangScript");
    }
}
