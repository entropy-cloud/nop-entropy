//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.parse;

import io.nop.antlr4.common.ParseTreeResult;
import io.nop.orm.eql.parse.antlr.EqlParser;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.ITextResourceParser;
import io.nop.orm.eql.ast.SqlProgram;

public class EqlASTParser implements ITextResourceParser<SqlProgram> {
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
