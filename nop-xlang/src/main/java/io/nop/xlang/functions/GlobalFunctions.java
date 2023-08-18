/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.functions;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.lang.EvalMethod;
import io.nop.api.core.annotations.lang.Macro;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.RawText;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.MaskedValue;
import io.nop.commons.util.objects.OptionalValue;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.functions.EvalFunctionalAdapter;
import io.nop.core.lang.json.jpath.JPath;
import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.AssignmentExpression;
import io.nop.xlang.ast.CallExpression;
import io.nop.xlang.ast.ConcatExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.IdentifierKind;
import io.nop.xlang.ast.IfStatement;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.SwitchCase;
import io.nop.xlang.ast.SwitchStatement;
import io.nop.xlang.ast.XLangASTKind;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.ast.definition.ScopeVarDefinition;
import io.nop.xlang.utils.ExprEvalHelper;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.nop.xlang.XLangErrors.ARG_ACTUAL_TYPE;
import static io.nop.xlang.XLangErrors.ARG_ARG_COUNT;
import static io.nop.xlang.XLangErrors.ARG_EXPECTED;
import static io.nop.xlang.XLangErrors.ARG_EXPECTED_TYPE;
import static io.nop.xlang.XLangErrors.ARG_EXPR;
import static io.nop.xlang.XLangErrors.ARG_INJECT_PARAM;
import static io.nop.xlang.XLangErrors.ARG_MAX_COUNT;
import static io.nop.xlang.XLangErrors.ARG_MIN_COUNT;
import static io.nop.xlang.XLangErrors.ARG_VAR_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_INJECT_PARAM_NOT_NAME_OR_TYPE;
import static io.nop.xlang.XLangErrors.ERR_EXEC_INVALID_ARG_COUNT;
import static io.nop.xlang.XLangErrors.ERR_EXEC_NOT_LITERAL_VALUE;
import static io.nop.xlang.XLangErrors.ERR_EXEC_TOO_FEW_ARGS;
import static io.nop.xlang.XLangErrors.ERR_EXEC_TOO_MANY_ARGS;
import static io.nop.xlang.XLangErrors.ERR_MACRO_INVALID_ARG_AST;
import static io.nop.xlang.XLangErrors.ERR_XLANG_INVALID_VAR_NAME;

@Locale("zh-CN")
public class GlobalFunctions {

    @Description("返回OptionalValue包装对象")
    public static OptionalValue optional(Object value) {
        return OptionalValue.of(value);
    }

    @Description("返回当前时间")
    public static Timestamp now() {
        return CoreMetrics.currentTimestamp();
    }

    @Description("返回今天的日期")
    public static LocalDate today() {
        return CoreMetrics.today();
    }

    @Description("返回当前的日期和时间")
    public static LocalDateTime currentDateTime() {
        return CoreMetrics.currentDateTime();
    }

    @Description("编译并执行xpl语言片段，outputMode=none")
    @Macro
    public static Expression xpl(@Name("scope") IXLangCompileScope scope, @Name("expr") CallExpression expr) {
        return TemplateMacroImpls.xpl(scope, expr);
    }

    @Description("编译并执行xpl语言片段，outputMode=html")
    @Macro
    public static Expression tpl(@Name("scope") IXLangCompileScope scope, @Name("expr") CallExpression expr) {
        return TemplateMacroImpls.tpl(scope, expr);
    }

    @Description("编译并执行xpl语言片段，outputMode=xml")
    @Macro
    public static Expression xml(@Name("scope") IXLangCompileScope scope, @Name("expr") CallExpression expr) {
        return TemplateMacroImpls.xml(scope, expr);
    }


    @Description("编译并执行xpl语言片段，outputMode=sql")
    @Macro
    public static Expression sql(@Name("scope") IXLangCompileScope scope, @Name("expr") CallExpression expr) {
        return TemplateMacroImpls.xml(scope, expr);
    }


    @Description("编译并返回JPath对象")
    @Macro(resultType = JPath.class)
    public static Expression jpath(@Name("scope") IXLangCompileScope scope, @Name("expr") CallExpression expr) {
        return TemplateMacroImpls.jpath(scope, expr);
    }

    @Description("编译XLang语言中的XPath选择表达式，并返回IXSelector对象")
    @Macro(resultType = IXSelector.class)
    public static Expression xpath(@Name("scope") IXLangCompileScope scope, @Name("expr") CallExpression expr) {
        return TemplateMacroImpls.xpath(scope, expr);
    }

    @Description("解析GraphQL字段选择表达式，返回FieldSelectionBean")
    @Macro(resultType = FieldSelectionBean.class)
    public static Expression selection(@Name("scope") IXLangCompileScope scope, @Name("expr") CallExpression expr) {
        return TemplateMacroImpls.selection(scope, expr);
    }

    @Description("类SQL语法的排序条件，解析为List<OrderFieldBean>对象")
    @Macro
    public static Expression order_by(@Name("scope") IXLangCompileScope scope, @Name("orderBy") CallExpression expr) {
        return TemplateMacroImpls.order_by(scope, expr);
    }

    @Description("得到本函数调用处的源码位置")
    @Macro(resultType = SourceLocation.class)
    public static Expression location(@Name("scope") IXLangCompileScope scope, @Name("expr") CallExpression expr) {
        if (!expr.getArguments().isEmpty())
            throw new NopEvalException(ERR_EXEC_TOO_MANY_ARGS).param(ARG_EXPR, expr).param(ARG_MAX_COUNT, 0);

        return Literal.valueOf(expr.getLocation(), expr.getLocation());
    }

    @Description("字符串拼接，空字符串会比自动忽略")
    @Macro(resultType = String.class)
    public static Expression concat(@Name("scope") IXLangCompileScope scope, @Name("expr") CallExpression expr) {
        for (Expression arg : expr.getArguments()) {
            arg.setASTParent(null);
        }
        return ConcatExpression.valueOf(expr.getLocation(), expr.getArguments());
    }

    @Description("设置scope变量。在表达式中无法直接调用$scope.setLocalValue函数来设置变量值，只能通过assign函数进行。例如 assign(\"a\",1)")
    @Macro
    public static Expression assign(@Name("scope") IXLangCompileScope scope, @Name("expr") CallExpression expr) {
        if (expr.getArguments().size() != 2)
            throw new NopEvalException(ERR_EXEC_INVALID_ARG_COUNT).param(ARG_EXPR, expr).param(ARG_EXPECTED, 2)
                    .param(ARG_ARG_COUNT, expr.getArguments().size());

        if (!(expr.getArguments().get(0) instanceof Literal))
            throw new NopEvalException(ERR_MACRO_INVALID_ARG_AST).param(ARG_EXPR, expr.getArguments().get(0))
                    .param(ARG_ACTUAL_TYPE, expr.getArguments().get(0).getASTKind())
                    .param(ARG_EXPECTED_TYPE, XLangASTKind.Literal);

        Literal literal = (Literal) expr.getArguments().get(0);
        if (!StringHelper.isValidSimpleVarName(literal.getStringValue()))
            throw new NopEvalException(ERR_XLANG_INVALID_VAR_NAME).param(ARG_VAR_NAME, literal.getStringValue());

        Expression value = expr.getArguments().get(1);
        value.setASTParent(null);
        Identifier id = Identifier.valueOf(literal.getLocation(), literal.getStringValue());
        id.setIdentifierKind(IdentifierKind.SCOPE_VAR_REF);
        ScopeVarDefinition var = new ScopeVarDefinition(id.getName());
        var.setAllowAssignment(true);
        id.setResolvedDefinition(var);
        return AssignmentExpression.valueOf(expr.getLocation(), id, XLangOperator.ASSIGN, value);
    }

    @Description("返回当前scope环境中的变量")
    @EvalMethod
    public static Object get(IEvalScope scope, @Name("name") String name) {
        return scope.getValue(name);
    }

    @Description("按照outputMode=node来执行xpl函数，并返回XNode对象")
    @EvalMethod
    public static XNode eval_node(IEvalScope scope, @Name("func") IEvalFunction func) {
        return ExprEvalHelper.generateNode(ctx -> func.call0(null, ctx.getEvalScope()), scope);
    }

    @Description("执行表达式")
    @EvalMethod
    public static Object eval(IEvalScope scope, @Name("expr") IEvalAction expr,
                              @Name("vars") Map<String, Object> vars) {
        scope = scope.newChildScope();
        if (!vars.isEmpty()) {
            scope.setLocalValues(null, vars);
        }
        return expr.invoke(scope);
    }

    @Description("获取常量表达式的值")
    @Macro
    public static Expression static_eval(@Name("scope") IXLangCompileScope scope, @Name("expr") CallExpression expr) {
        if (expr.getArguments().size() != 1)
            throw new NopEvalException(ERR_EXEC_INVALID_ARG_COUNT).param(ARG_EXPR, expr).param(ARG_EXPECTED, 1)
                    .param(ARG_ARG_COUNT, expr.getArguments().size());
        Expression arg = expr.getArgument(0);
        if (arg instanceof Literal) {
            return arg.deepClone();
        }

        CallExpression ret = new CallExpression();
        ret.setLocation(expr.getLocation());
        ret.setCallee(Identifier.valueOf(expr.getLocation(), "static_eval2"));
        List<Expression> args = new ArrayList<>(2);
        args.add(Literal.valueOf(expr.getLocation(), expr.getLocation()));
        args.add(arg.deepClone());
        ret.setArguments(args);
        return ret;
    }

    @Internal
    public static Object static_eval2(@Name("loc") SourceLocation loc, @Name("expr") Object expr) {
        if (!(expr instanceof Expression))
            return expr;

        if (!(expr instanceof Literal))
            throw new NopException(ERR_EXEC_NOT_LITERAL_VALUE).loc(loc).param(ARG_EXPR, expr);
        return ((Literal) expr).getValue();
    }

    @Description("将文本字符串包装为RawText对象，从而禁止outputMode=xml/html/sql模式时自动进行的转义编码")
    public static RawText raw(@Name("value") String value) {
        if (value == null)
            return null;

        return new RawText(value);
    }

    @Description("返回掩码包装对象")
    public static MaskedValue masked(@Name("value") Object value) {
        return MaskedValue.masked(value);
    }

    @Description("通过BeanContainer来获取IoC容器中的bean")
    @EvalMethod
    public static Object inject(IEvalScope scope, @Name("beanNameOrType") Object beanNameOrType) {
        if (beanNameOrType instanceof String) {
            return scope.getBeanProvider().getBean(beanNameOrType.toString());
        } else {
            if (!(beanNameOrType instanceof Class))
                throw new NopException(ERR_EXEC_INJECT_PARAM_NOT_NAME_OR_TYPE).param(ARG_INJECT_PARAM, beanNameOrType);
            return scope.getBeanProvider().getBeanByType((Class<?>) beanNameOrType);
        }
    }

    @Description("在资源依赖管理器中增加对本资源对象的依赖跟踪")
    public static void track(@Name("value") Object value) {
        if (value instanceof IComponentModel) {
            String path = ((IComponentModel) value).resourcePath();
            if (path != null)
                ResourceComponentManager.instance().traceDepends(path);
        }
    }

    @Description("类似于Excel的IF函数")
    @Macro
    public static Expression IF(@Name("scope") IEvalScope scope, @Name("expr") CallExpression expr) {
        if (expr.getArguments().size() != 3)
            throw new NopEvalException(ERR_EXEC_INVALID_ARG_COUNT).param(ARG_EXPR, expr).param(ARG_ARG_COUNT, 3);

        for (Expression arg : expr.getArguments()) {
            arg.setASTParent(null);
        }

        IfStatement stm = new IfStatement();
        stm.setLocation(expr.getLocation());
        stm.setTest(expr.getArgument(0));
        stm.setConsequent(expr.getArgument(1));
        stm.setAlternate(expr.getArgument(2));
        return stm;
    }

    @Description("类似于Excel的SWITCH函数")
    @Macro
    public static Expression SWITCH(@Name("scope") IEvalScope scope, @Name("expr") CallExpression expr) {
        if (expr.getArguments().size() < 3)
            throw new NopEvalException(ERR_EXEC_TOO_FEW_ARGS).param(ARG_EXPR, expr).param(ARG_MIN_COUNT, 3);

        for (Expression arg : expr.getArguments()) {
            arg.setASTParent(null);
        }

        List<Expression> args = expr.getArguments();
        SwitchStatement stm = new SwitchStatement();
        stm.setLocation(expr.getLocation());
        stm.setAsExpr(true);
        stm.setDiscriminant(args.get(0));

        int i, n = args.size();

        List<SwitchCase> cases = new ArrayList<>(n / 2);

        for (i = 1; i < n - 1; i += 2) {
            SwitchCase caseExpr = new SwitchCase();
            caseExpr.setLocation(args.get(i).getLocation());
            caseExpr.setTest(args.get(i));
            caseExpr.setConsequent(args.get(i + 1));
            cases.add(caseExpr);
        }
        stm.setCases(cases);

        if (i == n - 1) {
            stm.setDefaultCase(args.get(n - 1));
        }
        return stm;
    }

    @Description("创建XNode节点")
    public static XNode g_make_node(@Name("tagName") String tagName, @Name("attrs") Map<String, Object> attrs) {
        XNode node = XNode.make(tagName);
        node.setAttrs(attrs);
        return node;
    }

    @Description("获取当前多语言设置")
    public static String g_current_locale() {
        return ContextProvider.currentLocale();
    }

    @Description("获取系统缺省的多语言设置")
    public static String g_default_locale() {
        return AppConfig.defaultLocale();
    }


    @Description("将IEvalFunction接口适配为Consumer/Supplier/Function等Java内置函数式接口")
    @EvalMethod
    public static EvalFunctionalAdapter functional(IEvalScope scope, IEvalFunction fn) {
        if (fn == null)
            return null;
        if (fn instanceof EvalFunctionalAdapter)
            return (EvalFunctionalAdapter) fn;
        return new EvalFunctionalAdapter(null, fn, scope);
    }
}