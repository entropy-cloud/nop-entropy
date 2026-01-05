//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.parse;

import io.nop.antlr4.common.AbstractParseTreeParser;
import io.nop.antlr4.common.ParseTreeResult;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import io.nop.xlang.parse.antlr.XLangParser;
import io.nop.xlang.parse.antlr.XLangLexer;

public class XLangParseTreeParser extends AbstractParseTreeParser {
    static final XLangParseTreeParser _INSTANCE = new XLangParseTreeParser();

    public static final XLangParseTreeParser instance() {
        return _INSTANCE;
    }

    @Override
    protected ParseTreeResult doParse(CharStream stream) {
        XLangLexer lexer = new XLangLexer(stream);
        config(lexer);
        TokenStream ts = new CommonTokenStream(lexer);

        XLangParser parser = new XLangParser(ts);

        return twoPhaseParse(parser, p -> p.program());
    }

    static final int[] PRIMARY_EXPECTED_TOKENS = new int[]{
       
            XLangLexer.Comma,
       
            XLangLexer.SemiColon,
       
            XLangLexer.CloseParen,
       
            XLangLexer.CloseBrace,
       
            XLangLexer.CloseBracket,
       
    };

    @Override
    protected int[] getPrimaryExpectedTokens() {
        return PRIMARY_EXPECTED_TOKENS;
    }
}
