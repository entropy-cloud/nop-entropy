/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.expr;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Program;

public interface IXLangExprParser {

    /**
     * 解析表达式
     *
     * @param loc
     * @param source
     * @param scope
     * @return
     */
    default Expression parseSimpleExpr(SourceLocation loc, String source, IXLangCompileScope scope) {
        return parseSimpleExpr(loc, source, scope, true);
    }

    default Program parseFullExpr(SourceLocation loc, String source, IXLangCompileScope scope) {
        return parseFullExpr(loc, source, scope, true);
    }

    /**
     * 解析包含嵌入表达式的模板，格式为 ${abc}
     *
     * @param loc    表达式源码对应的源文件位置
     * @param source 表达式源码
     * @param phase  表达式对应的编译阶段。不同阶段的表达式具有不同的前导字符，例如转换阶段的嵌入表达式为 %{}形式，而编译阶段为#{},执行阶段为${}
     * @param scope  编译期上下文
     * @return 编译结果，已经完成所有编译期优化
     */
    default Expression parseTemplateExpr(SourceLocation loc, String source, boolean singleExpr, ExprPhase phase,
                                         IXLangCompileScope scope) {
        return parseTemplateExpr(loc, source, singleExpr, phase, scope, true);
    }

    IExecutableExpression buildExecutable(Expression expr, boolean optimize, IXLangCompileScope scope);

    Expression parseSimpleExpr(SourceLocation loc, String source, IXLangCompileScope scope, boolean resolveMacro);

    Program parseFullExpr(SourceLocation loc, String source, IXLangCompileScope scope, boolean resolveMacro);

    /**
     * 解析包含嵌入表达式的模板，格式为 ${abc}
     *
     * @param loc    表达式源码对应的源文件位置
     * @param source 表达式源码
     * @param phase  表达式对应的编译阶段。不同阶段的表达式具有不同的前导字符，例如转换阶段的嵌入表达式为 %{}形式，而编译阶段为#{},执行阶段为${}
     * @param scope  编译期上下文
     * @return 编译结果，已经完成所有编译期优化
     */
    Expression parseTemplateExpr(SourceLocation loc, String source, boolean singleExpr, ExprPhase phase,
                                 IXLangCompileScope scope, boolean resolveMacro);
}