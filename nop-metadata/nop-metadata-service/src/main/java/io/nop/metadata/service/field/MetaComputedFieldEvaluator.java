package io.nop.metadata.service.field;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.MemberExpression;
import io.nop.xlang.ast.XLangASTVisitor;
import io.nop.xlang.expr.ExprFeatures;
import io.nop.xlang.expr.simple.SimpleExprParser;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 计算列（fieldKind=computed）XLang 表达式解析/校验/求值（设计 14 §3.2，plan
 * 2026-10-26-2047-1 Phase 2）。
 *
 * <p>安全契约：以 {@link ExprFeatures#SIMPLE} 特性集解析（编译期禁 import/new/语句/
 * lambda/自赋值），与 xmeta {@code <transformOut>} 表达式同族但特性面更窄。行上求值
 * 作用域 = 返回行字段 Map（{@link EvalScopeImpl}），无 SQL 下推。
 *
 * <p>{@code XLangCompileTool.compileSimpleExpr} 底层 XplExprParser 启用
 * {@code ExprFeatures.ALL}（"simple" 指表达式文法而非特性面），不满足本设计的安全
 * 契约，故此处直接构造受限特性的 {@link SimpleExprParser}（FilterExprHelper
 * newFilterExprParser 同款用法），再经平台 executable 编译器出可执行体。
 */
public class MetaComputedFieldEvaluator {

    private MetaComputedFieldEvaluator() {
    }

    /** 以 SIMPLE 特性集解析表达式（save-time 校验与 query-time 编译共用同一入口）。 */
    public static Expression parse(String expr) {
        SimpleExprParser parser = new SimpleExprParser();
        parser.setUseEvalException(true);
        parser.enableFeatures(ExprFeatures.SIMPLE);
        return parser.parseExpr(SourceLocation.UNKNOWN, expr);
    }

    /**
     * 收集表达式引用的根标识符（成员访问 {@code a.b} 只收 {@code a}——行作用域为平铺
     * Map，属性名不属于字段白名单面；computed 下标访问 {@code a[b]} 内层表达式照常收集）。
     */
    public static Set<String> collectRootIdentifiers(Expression expr) {
        Set<String> names = new LinkedHashSet<>();
        XLangASTVisitor visitor = new XLangASTVisitor() {
            @Override
            public void visitIdentifier(Identifier node) {
                names.add(node.getName());
            }

            @Override
            public void visitMemberExpression(MemberExpression node) {
                visit(node.getObject());
                if (node.getComputed()) {
                    visit(node.getProperty());
                }
            }
        };
        visitor.visit(expr);
        return names;
    }

    /**
     * 编译为可执行体。compile scope 开启 {@code allowUnregisteredScopeVar}——行字段标识符
     * （不在编译期作用域注册表中）自动注册为 SCOPE_VAR_REF，求值时从行 Map 作用域解析
     * （LexicalScopeAnalysis 回退语义）。
     */
    public static ExprEvalAction compile(Expression expr) {
        XLangCompileTool tool = XLang.newCompileTool();
        tool.getScope().setAllowUnregisteredScopeVar(true);
        IExecutableExpression executable = tool.getCompiler().buildExecutable(expr, true, tool.getScope());
        return new ExprEvalAction(executable);
    }

    /** 行上求值：作用域 = 返回行字段 Map。 */
    public static Object evaluate(ExprEvalAction action, Map<String, Object> row) {
        return action.invoke(new EvalScopeImpl(row));
    }
}
