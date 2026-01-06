//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.parse;

import io.nop.antlr4.common.AbstractParseTreeParser;
import io.nop.antlr4.common.ParseTreeResult;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import io.nop.orm.eql.parse.antlr.EqlParser;
import io.nop.orm.eql.parse.antlr.EqlLexer;

public class EqlParseTreeParser extends AbstractParseTreeParser {
    static final EqlParseTreeParser _instance = new EqlParseTreeParser();

    public static final EqlParseTreeParser instance() {
        return _instance;
    }

    @Override
    protected ParseTreeResult doParse(CharStream stream) {
        EqlLexer lexer = new EqlLexer(stream);
        config(lexer);
        TokenStream ts = new CommonTokenStream(lexer);

        EqlParser parser = new EqlParser(ts);

        return twoPhaseParse(parser, p -> p.sqlProgram());
    }

    static final int[] PRIMARY_EXPECTED_TOKENS = new int[]{
       
    };

    @Override
    protected int[] getPrimaryExpectedTokens() {
        return PRIMARY_EXPECTED_TOKENS;
    }
}
