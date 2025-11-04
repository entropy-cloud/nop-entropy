/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.antlr4.tool.loader;

import io.nop.antlr4.tool.model.AstGrammar;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.URLHelper;
import io.nop.core.resource.VirtualFileSystem;
import org.antlr.v4.Tool;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.ast.GrammarRootAST;

import java.io.File;

import static io.nop.antlr4.tool.AntlrToolErrors.ARG_PATH;
import static io.nop.antlr4.tool.AntlrToolErrors.ERR_ANTLR_INVALID_GRAMMAR;
import static io.nop.antlr4.tool.AntlrToolErrors.ERR_GRAMMAR_RESOURCE_PATH_NOT_FILE;

public class GrammarLoader {
    private File sourceDir;
    private File libDir;
    private File outputDir;

    private String packageName;

    private boolean genListener = true;
    private boolean genVisitor = true;
    private boolean genDependencies = false;
    private boolean verbose = false;

    private boolean genATNDot = false;

    public boolean isGenATNDot() {
        return genATNDot;
    }

    public void setGenATNDot(boolean genATNDot) {
        this.genATNDot = genATNDot;
    }

    public boolean isGenListener() {
        return genListener;
    }

    public void setGenListener(boolean genListener) {
        this.genListener = genListener;
    }

    public boolean isGenVisitor() {
        return genVisitor;
    }

    public void setGenVisitor(boolean genVisitor) {
        this.genVisitor = genVisitor;
    }

    public boolean isGenDependencies() {
        return genDependencies;
    }

    public void setGenDependencies(boolean genDependencies) {
        this.genDependencies = genDependencies;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public File getSourceDir() {
        return sourceDir;
    }

    public void setSourceDir(File sourceDir) {
        this.sourceDir = sourceDir;
    }

    public File getLibDir() {
        return libDir;
    }

    public void setLibDir(File libDir) {
        this.libDir = libDir;
    }

    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    public void setSourceDirPath(String path) {
        File file = VirtualFileSystem.instance().getResource(path).toFile();
        if (file == null)
            throw new NopException(ERR_GRAMMAR_RESOURCE_PATH_NOT_FILE).param(ARG_PATH, path);
        setSourceDir(file);
    }

    public void setLibDirPath(String path) {
        File file = VirtualFileSystem.instance().getResource(path).toFile();
        if (file == null)
            throw new NopException(ERR_GRAMMAR_RESOURCE_PATH_NOT_FILE).param(ARG_PATH, path);
        setLibDir(file);
    }

    public void setOutputPath(String path) {
        File file = VirtualFileSystem.instance().getResource(path).toFile();
        if (file == null)
            throw new NopException(ERR_GRAMMAR_RESOURCE_PATH_NOT_FILE).param(ARG_PATH, path);
        setOutputDir(file);
    }

    public Grammar loadAntlrGrammar(String name) {
        if (name.startsWith("file:")) {
            name = URLHelper.toURI(name).getSchemeSpecificPart();
        }
        Tool tool = new CustomTool();
        tool.inputDirectory = sourceDir;
        if (libDir != null)
            tool.libDirectory = libDir.getAbsolutePath();
        tool.genPackage = packageName;

        if (outputDir != null) {
            tool.gen_listener = genListener;
            tool.gen_visitor = genVisitor;
            tool.gen_dependencies = genDependencies;
            tool.generate_ATN_dot = genATNDot;
            tool.outputDirectory = outputDir.getAbsolutePath();
        }
        // tool.processGrammarsOnCommandLine();

        Grammar lexerGrammar = null;
        if (name.endsWith("Parser.g4")) {
            lexerGrammar = loadLexer(tool, name);
        }

        GrammarRootAST grammarRootAST = tool.parseGrammar(name);
        final Grammar g = tool.createGrammar(grammarRootAST);
        g.fileName = name;
        if (lexerGrammar != null)
            g.importVocab(lexerGrammar);

        tool.process(g, shouldGenCode());

        if (tool.getNumErrors() > 0)
            throw new NopException(ERR_ANTLR_INVALID_GRAMMAR).param(ARG_PATH, name);

        return g;
    }

    private Grammar loadLexer(Tool tool, String name) {
        String lexerFile = name.substring(0, name.length() - "Parser.g4".length()) + "Lexer.g4";
        File file = new File(lexerFile);
        if (!file.isAbsolute()) {
            file = new File(tool.inputDirectory, lexerFile);
        }
        if (file.exists()) {
            GrammarRootAST grammarRootAST = tool.parseGrammar(lexerFile);
            final Grammar g = tool.createGrammar(grammarRootAST);
            g.fileName = lexerFile;
            tool.process(g, shouldGenCode());
            return g;
        }
        return null;
    }

    public boolean shouldGenCode() {
        return outputDir != null;
    }

    public AstGrammar loadAstGrammar(String name) {
        Grammar g = loadAntlrGrammar(name);
        AstGrammar grammar = new AstGrammarBuilder().buildFromAntlrGrammar(g);
        // ParserFile file = AntlrGrammarHelper.buildParserOutputModel(g);
        // analyzeGrammar(file, grammar);
        return grammar;
    }
    //
    // /**
    // * 将antlr工具的分析结果设置到AstGrammar对象上。主要是设置AstRule.needList属性。
    // */
    // private void analyzeGrammar(ParserFile file, AstGrammar grammar) {
    // for (RuleFunction fn : file.parser.funcs) {
    // AstRule rule = grammar.getRule(fn.name);
    // if (rule == null)
    // continue;
    //
    // for (Decl decl : fn.ruleCtx.getters) {
    // if (decl instanceof ContextRuleListGetterDecl) {
    // RuleRef node = rule.getPropertyByRuleName(decl.name);
    // if (node != null) {
    // node.setNeedList(true);
    // }
    // }
    // }
    // }
    // }
}
