//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.parse;

import io.nop.antlr4.common.ParseTreeResult;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.ITextResourceParser;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.parse.antlr.EqlParser;

public class EqlASTParser implements ITextResourceParser<SqlProgram> {

    public SqlExpr parseSqlExpr(SourceLocation loc, String text) {
        EqlParseTreeParser parser = new EqlExprParseTreeParser();
        ParseTreeResult parseTree = parser.parseFromText(loc, text);
        if (parseTree == null)
            return null;
        return new EqlASTBuildVisitor()
                .visitSqlExpr((EqlParser.SqlExprContext) parseTree.getParseTree());
    }

    @Override
    public SqlProgram parseFromResource(IResource resource, boolean ignoreUnknown) {
        EqlParseTreeParser parser = newParser();
        ParseTreeResult parseTree = parser.parseFromResource(resource, ignoreUnknown);
        if (parseTree == null)
            return null;
        return transform(parseTree);
    }

    @Override
    public SqlProgram parseFromText(SourceLocation loc, String text) {
        EqlParseTreeParser parser = newParser();
        ParseTreeResult parseTree = parser.parseFromText(loc, text);
        if (parseTree == null)
            return null;
        return transform(parseTree);
    }

    protected EqlParseTreeParser newParser() {
        return new EqlParseTreeParser();
    }

    protected SqlProgram transform(ParseTreeResult parseTree) {
        SqlProgram program = new EqlASTBuildVisitor()
                .visitSqlProgram((EqlParser.SqlProgramContext) parseTree.getParseTree());
        return program;
    }
}
