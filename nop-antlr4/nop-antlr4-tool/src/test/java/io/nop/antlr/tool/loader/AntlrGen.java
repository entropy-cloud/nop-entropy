/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.antlr.tool.loader;

import io.nop.antlr4.tool.loader.CustomTool;
import org.antlr.v4.Tool;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.ast.GrammarRootAST;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class AntlrGen {
    public static void main(String[] args) {
        File dir = new File("c:/can/entropy-cloud/nop-xlang/precompile/@model/antlr");

        Tool tool = new CustomTool();
        tool.inputDirectory = dir;
        tool.libDirectory = tool.inputDirectory.getAbsolutePath();
        tool.gen_visitor = true;
        tool.generate_ATN_dot = true;
        tool.outputDirectory = new File(dir, ".antlr").getAbsolutePath();

        // 必须先根据lexer生成tokens文件
        List<GrammarRootAST> grammars = tool.sortGrammarByTokenVocab(Arrays.asList("XLangLexer.g4", "XLangParser.g4"));
        for (GrammarRootAST grammar : grammars) {
            final Grammar g = tool.createGrammar(grammar);
            g.fileName = grammar.fileName;
            tool.process(g, true);
        }
    }
}
