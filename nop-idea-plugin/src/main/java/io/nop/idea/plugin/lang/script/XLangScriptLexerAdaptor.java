package io.nop.idea.plugin.lang.script;

import io.nop.xlang.parse.antlr.XLangLexer;
import org.antlr.intellij.adaptor.lexer.ANTLRLexerAdaptor;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-27
 */
public class XLangScriptLexerAdaptor extends ANTLRLexerAdaptor {

    public XLangScriptLexerAdaptor() {
        super(XLangScriptLanguage.INSTANCE, new XLangLexer(null));
    }
}
