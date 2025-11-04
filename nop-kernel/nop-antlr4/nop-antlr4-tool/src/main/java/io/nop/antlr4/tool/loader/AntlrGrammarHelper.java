/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.antlr4.tool.loader;

import io.nop.core.lang.xml.XNode;
import org.antlr.v4.codegen.CodeGenerator;
import org.antlr.v4.codegen.OutputModelController;
import org.antlr.v4.codegen.OutputModelFactory;
import org.antlr.v4.codegen.ParserFactory;
import org.antlr.v4.codegen.model.ParserFile;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.ast.GrammarAST;

import java.util.List;

public class AntlrGrammarHelper {
    public static XNode toNode(GrammarAST ast) {
        XNode node = XNode.make(ast.toString());
        if (ast.getChildren() != null) {
            for (GrammarAST child : getChildren(ast)) {
                node.appendChild(toNode(child));
            }
        }
        return node;
    }

    public static List<GrammarAST> getChildren(GrammarAST ast) {
        return (List<GrammarAST>) ast.getChildren();
    }

    public static ParserFile buildParserOutputModel(Grammar g) {
        CodeGenerator gen = CodeGenerator.create(g);
        OutputModelFactory factory = new ParserFactory(gen);
        OutputModelController controller = new OutputModelController(factory);
        factory.setController(controller);
        return (ParserFile) controller.buildParserOutputModel(false);
    }

}
