package io.nop.idea.plugin.lang.script;

import java.util.ArrayList;
import java.util.List;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import io.nop.xlang.parse.antlr.XLangParser;
import org.antlr.intellij.adaptor.parser.ANTLRParserAdaptor;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

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
        ParseTree tree;

        if (root instanceof IFileElementType) {
            tree = ((XLangParser) parser).program();
        } else {
            tree = ((XLangParser) parser).statement();
        }
        return convertParseTree(tree);
    }

    protected ParseTree convertParseTree(ParseTree tree) {
        if (tree instanceof TerminalNode) {
            return tree;
        } //
        else if (tree instanceof XLangParser.Eos__Context eos) {
            TerminalNode child = eos.SemiColon();

            return child != null ? child : eos.EOF();
        } //
        else if (tree instanceof ParserRuleContext ctx) {
            List<ParseTree> children = new ArrayList<>(ctx.children.size());
            ctx.children.forEach((child) -> {
                child = convertParseTree(child);
                if (child != null) {
                    children.add(child);
                }
            });

            ctx.children = children;
        }

        return tree;
    }
}
