//__XGEN_FORCE_OVERRIDE__
package io.nop.mermaid.parse;

import io.nop.antlr4.common.ParseTreeResult;
import io.nop.mermaid.parse.antlr.MermaidParser;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.ITextResourceParser;
import io.nop.mermaid.ast.MermaidDocument;

public class MermaidASTParser implements ITextResourceParser<MermaidDocument> {
    @Override
    public MermaidDocument parseFromResource(IResource resource, boolean ignoreUnknown) {
        MermaidParseTreeParser parser = newParser();
        ParseTreeResult parseTree = parser.parseFromResource(resource, ignoreUnknown);
        if (parseTree == null)
            return null;
        return transform(parseTree);
    }

    @Override
    public MermaidDocument parseFromText(SourceLocation loc, String text) {
        MermaidParseTreeParser parser = newParser();
        ParseTreeResult parseTree = parser.parseFromText(loc, text);
        if (parseTree == null)
            return null;
        return transform(parseTree);
    }

    protected MermaidParseTreeParser newParser() {
        return new MermaidParseTreeParser();
    }

    protected MermaidDocument transform(ParseTreeResult parseTree) {
        MermaidDocument program = new MermaidASTBuildVisitor()
            .visitMermaidDocument((MermaidParser.MermaidDocumentContext) parseTree.getParseTree());
        return program;
    }
}
