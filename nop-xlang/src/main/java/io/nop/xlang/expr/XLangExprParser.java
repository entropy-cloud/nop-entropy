/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.expr;

import io.nop.antlr4.common.ParseTreeResult;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Program;
import io.nop.xlang.compile.BuildExecutableProcessor;
import io.nop.xlang.compile.LexicalScopeAnalysis;
import io.nop.xlang.compile.PrintResolvedIdentifier;
import io.nop.xlang.compile.TypeInferenceProcessor;
import io.nop.xlang.compile.TypeInferenceState;
import io.nop.xlang.parse.XLangASTBuildVisitor;
import io.nop.xlang.parse.XLangParseTreeParser;
import io.nop.xlang.parse.antlr.XLangParser;
import io.nop.xlang.xpl.impl.XplExprParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XLangExprParser implements IXLangExprParser {
    private static final Logger LOG = LoggerFactory.getLogger(XLangExprParser.class);

    @Override
    public Program parseFullExpr(SourceLocation loc, String source, IXLangCompileScope scope, boolean resolveMacro) {
        ParseTreeResult result = XLangParseTreeParser.instance().parseFromText(loc, source);
        if (result == null)
            return null;
        Program program = new XLangASTBuildVisitor().visitProgram((XLangParser.ProgramContext) result.getParseTree());
        return program;
    }

    @Override
    public Expression parseSimpleExpr(SourceLocation loc, String source, IXLangCompileScope scope,
                                      boolean resolveMacro) {
        IExpressionParser parser = newExprParser(scope, resolveMacro);
        return parser.parseExpr(loc, source);
    }

    @Override
    public Expression parseTemplateExpr(SourceLocation loc, String source, boolean singleExpr, ExprPhase phase,
                                        IXLangCompileScope scope, boolean resolveMacro) {
        IExpressionParser parser = newExprParser(scope, resolveMacro);
        return parser.parseTemplateExpr(loc, source, singleExpr, phase);
    }

    protected IExpressionParser newExprParser(IXLangCompileScope scope, boolean resolveMacro) {
        return new XplExprParser(this, scope, resolveMacro);
    }

    @Override
    public IExecutableExpression buildExecutable(Expression expr, boolean optimize, IXLangCompileScope scope) {
        expr = new LexicalScopeAnalysis(scope).analyze(expr);

        if (LOG.isTraceEnabled()) {
            PrintResolvedIdentifier print = new PrintResolvedIdentifier();
            print.visit(expr);
            LOG.trace(print.getOutput());
        }

        if (optimize) {
            new TypeInferenceProcessor().processAST(expr, new TypeInferenceState());
            // expr = (Expression) new ExpressionOptimizer().optimize(expr, scope);
        }

        return new BuildExecutableProcessor().processAST(expr, scope);
    }
}