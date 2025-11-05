package io.nop.orm.eql.parse;

import io.nop.antlr4.common.ParseTreeResult;
import io.nop.orm.eql.parse.antlr.EqlLexer;
import io.nop.orm.eql.parse.antlr.EqlParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;

public class EqlExprParseTreeParser extends EqlParseTreeParser {
    @Override
    protected ParseTreeResult doParse(CharStream stream) {
        EqlLexer lexer = new EqlLexer(stream);
        config(lexer);
        TokenStream ts = new CommonTokenStream(lexer);

        EqlParser parser = new EqlParser(ts);

        return twoPhaseParse(parser, p -> p.sqlExpr());
    }
}
