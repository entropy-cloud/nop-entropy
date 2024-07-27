package io.nop.orm.eql.parse;

import io.nop.antlr4.common.ParseTreeResult;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.ITextResourceParser;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.parse.antlr.EqlParser;

public class EqlExprASTParser implements ITextResourceParser<SqlExpr> {
    @Override
    public SqlExpr parseFromResource(IResource resource, boolean ignoreUnknown) {
        EqlParseTreeParser parser = newParser();
        ParseTreeResult parseTree = parser.parseFromResource(resource, ignoreUnknown);
        if (parseTree == null)
            return null;
        return transform(parseTree);
    }

    @Override
    public SqlExpr parseFromText(SourceLocation loc, String text) {
        EqlParseTreeParser parser = newParser();
        ParseTreeResult parseTree = parser.parseFromText(loc, text);
        if (parseTree == null)
            return null;
        return transform(parseTree);
    }

    protected EqlParseTreeParser newParser() {
        return new EqlExprParseTreeParser();
    }

    protected SqlExpr transform(ParseTreeResult parseTree) {
        SqlExpr ret = new EqlASTBuildVisitor()
                .visitSqlExpr((EqlParser.SqlExprContext) parseTree.getParseTree());
        return ret;
    }
}
