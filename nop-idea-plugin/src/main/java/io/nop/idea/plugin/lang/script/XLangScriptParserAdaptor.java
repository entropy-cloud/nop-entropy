package io.nop.idea.plugin.lang.script;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import io.nop.xlang.parse.antlr.XLangParser;
import org.antlr.intellij.adaptor.parser.ANTLRParserAdaptor;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-27
 */
public class XLangScriptParserAdaptor extends ANTLRParserAdaptor {

    public XLangScriptParserAdaptor() {
        super(XLangScriptLanguage.INSTANCE, new XLangParser(null));
    }

    @Override
    protected ParseTree parse(Parser parser, IElementType root) {
        if (root instanceof IFileElementType) {
            return ((XLangParser) parser).program();
        }
        return ((XLangParser) parser).statement();
    }
}
