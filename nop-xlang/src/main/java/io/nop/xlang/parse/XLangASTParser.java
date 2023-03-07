//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.parse;

import io.nop.antlr4.common.ParseTreeResult;
import io.nop.xlang.parse.antlr.XLangParser;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.ITextResourceParser;
import io.nop.xlang.ast.Program;

public class XLangASTParser implements ITextResourceParser<Program> {
    @Override
    public Program parseFromResource(IResource resource, boolean ignoreUnknown) {
        XLangParseTreeParser parser = newParser();
        ParseTreeResult parseTree = parser.parseFromResource(resource, ignoreUnknown);
        if (parseTree == null)
            return null;
        return transform(parseTree);
    }

    @Override
    public Program parseFromText(SourceLocation loc, String text) {
        XLangParseTreeParser parser = newParser();
        ParseTreeResult parseTree = parser.parseFromText(loc, text);
        if (parseTree == null)
            return null;
        return transform(parseTree);
    }

    protected XLangParseTreeParser newParser() {
        return new XLangParseTreeParser();
    }

    protected Program transform(ParseTreeResult parseTree) {
        Program program = new XLangASTBuildVisitor()
            .visitProgram((XLangParser.ProgramContext) parseTree.getParseTree());
        return program;
    }
}
