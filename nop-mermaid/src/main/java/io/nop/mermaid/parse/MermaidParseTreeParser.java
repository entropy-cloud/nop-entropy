//__XGEN_FORCE_OVERRIDE__
package io.nop.mermaid.parse;

import io.nop.antlr4.common.AbstractParseTreeParser;
import io.nop.antlr4.common.ParseTreeResult;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import io.nop.mermaid.parse.antlr.MermaidParser;
import io.nop.mermaid.parse.antlr.MermaidLexer;

public class MermaidParseTreeParser extends AbstractParseTreeParser {
    static final MermaidParseTreeParser _instance = new MermaidParseTreeParser();

    public static final MermaidParseTreeParser instance() {
        return _instance;
    }

    @Override
    protected ParseTreeResult doParse(CharStream stream) {
        MermaidLexer lexer = new MermaidLexer(stream);
        config(lexer);
        TokenStream ts = new CommonTokenStream(lexer);

        MermaidParser parser = new MermaidParser(ts);

        return twoPhaseParse(parser, p -> p.mermaidDocument());
    }

    static final int[] PRIMARY_EXPECTED_TOKENS = new int[]{
       
    };

    @Override
    protected int[] getPrimaryExpectedTokens() {
        return PRIMARY_EXPECTED_TOKENS;
    }
}
