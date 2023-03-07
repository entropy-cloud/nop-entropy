/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.antlr.tool.loader;

import io.nop.antlr4.tool.loader.AntlrGrammarHelper;
import io.nop.antlr4.tool.loader.AstGrammarBuilder;
import io.nop.antlr4.tool.loader.CustomTool;
import io.nop.antlr4.tool.loader.GrammarLoader;
import io.nop.antlr4.tool.model.AstGrammar;
import io.nop.core.unittest.BaseTestCase;
import org.antlr.v4.Tool;
import org.antlr.v4.codegen.model.ParserFile;
import org.antlr.v4.codegen.model.RuleFunction;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.ast.GrammarRootAST;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class TestGrammarLoader extends BaseTestCase {
    @Test
    public void testParseGrammar() {
        File file = new File(getTestResourcesDir(), "antlr4/test");
        GrammarLoader loader = new GrammarLoader();
        loader.setSourceDir(file);
        loader.setLibDir(file);

        Grammar g = loader.loadAntlrGrammar("TestGrammar.g4");
        System.out.print(AntlrGrammarHelper.toNode(g.ast).getDumpString("grammar"));

        AstGrammar grammar = new AstGrammarBuilder().buildFromAntlrGrammar(g);

        System.out.println(grammar.toNode().getDumpString("grammar"));

        // assertEquals(2,grammar.getAstImports().size());
    }

    @Test
    public void testXLangGrammar() {
        File file = new File(getTestResourcesDir(), "antlr4/xlang");
        GrammarLoader loader = new GrammarLoader();
        loader.setSourceDir(file);
        loader.setLibDir(file);

        Grammar g = loader.loadAntlrGrammar("XLangParser.g4");
        System.out.print(AntlrGrammarHelper.toNode(g.ast).getDumpString("grammar"));

        AstGrammar grammar = loader.loadAstGrammar("XLangParser.g4");

        // AstGrammar grammar = new ParseGrammarBuilder().buildFromAntlrGrammar(g);

        System.out.println(grammar.toNode().getDumpString("grammar"));
    }

    @Test
    public void testXLangGrammar2() {
        File file = new File(getTestResourcesDir(), "antlr4/xlang");
        GrammarLoader loader = new GrammarLoader();
        loader.setSourceDir(file);
        loader.setLibDir(file);

        AstGrammar grammar = loader.loadAstGrammar("XLangParser.g4");
        System.out.println(grammar.toNode().getDumpString("grammar"));
    }

    @Test
    public void testCodeGen() {
        File file = new File(getTestResourcesDir(), "antlr4/test");
        GrammarLoader loader = new GrammarLoader();
        loader.setSourceDir(file);
        loader.setLibDir(file);

        Grammar g = loader.loadAntlrGrammar("TestGrammar.g4");

        ParserFile model = AntlrGrammarHelper.buildParserOutputModel(g);
        for (RuleFunction fn : model.parser.funcs) {
            System.out.println(fn.ctxType);
            System.out.println(fn.altLabelCtxs); // 每个分支都标注了label的情况
        }
    }

    @Test
    public void testTool() {
        Tool tool = new CustomTool();
        tool.inputDirectory = new File(getTestResourcesDir(), "antlr4/xlang");
        tool.libDirectory = tool.inputDirectory.getAbsolutePath();
        tool.gen_visitor = true;
        tool.outputDirectory = new File(getTargetDir(), "xgen").getAbsolutePath();
        tool.genPackage = "io.nop.xlang.ast";

        // tool.loadGrammar("XLangLexer.g4");
        // tool.loadGrammar("XLangParser.g4");

        // 必须先根据lexer生成tokens文件
        List<GrammarRootAST> grammars = tool.sortGrammarByTokenVocab(Arrays.asList("XLangLexer.g4", "XLangParser.g4"));
        for (GrammarRootAST grammar : grammars) {
            final Grammar g = tool.createGrammar(grammar);
            g.fileName = grammar.fileName;
            tool.process(g, true);
        }
    }
    //
    // @Test
    // public void testToolGen() throws Exception{
    // Tool tool = new CustomTool();
    // File srcModDir = new File(getModuleDir(),"../nop-antlr4-playground/src/main").getCanonicalFile();
    // tool.inputDirectory = new File(srcModDir, "antlr4/imports");
    // assertTrue(tool.inputDirectory.exists());
    // tool.libDirectory = tool.inputDirectory.getAbsolutePath();
    // tool.gen_visitor = true;
    // tool.outputDirectory = new File(getModuleTargetDir(), "xgen").getAbsolutePath();
    // tool.genPackage = "io.nop.xlang.ast";
    //
    //// tool.loadGrammar("XLangLexer.g4");
    //// tool.loadGrammar("XLangParser.g4");
    //
    // // 必须先根据lexer生成tokens文件
    // List<GrammarRootAST> grammars = tool.sortGrammarByTokenVocab(Arrays.asList("XLangBaseLexer.g4",
    // "XLangBaseParser.g4"));
    // for (GrammarRootAST grammar : grammars) {
    // final Grammar g = tool.createGrammar(grammar);
    // g.fileName = grammar.fileName;
    // tool.process(g, true);
    // }
    // }
}
