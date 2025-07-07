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
        // Note: 不需要为 dot 节点之后的空白添加占位节点，只要延迟到在
        // PsiReference#resolve 中才获取引用元素，即可正常触发代码补全
        if (root instanceof IFileElementType) {
            return ((XLangParser) parser).program();
        }
        return ((XLangParser) parser).statement();
    }
}
