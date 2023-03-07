/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.eval;

import io.nop.api.core.util.Guard;
import io.nop.core.lang.json.delta.IDeltaExtendsGenerator;

import java.util.Map;

/**
 * 在XLang模块中提供表达式的解析功能
 */
public class EvalExprProvider {
    private static IExpressionExecutor _executor = DefaultExpressionExecutor.INSTANCE;

    private static IEvalExprParser _exprParser;

    private static IDeltaExtendsGenerator deltaExtendsGenerator;

    private static IPredicateEvaluator featurePredicateEvaluator;

    public static IPredicateEvaluator getFeaturePredicateEvaluator() {
        return featurePredicateEvaluator;
    }

    public static void registerFeaturePredicateEvaluator(IPredicateEvaluator evaluator) {
        featurePredicateEvaluator = evaluator;
    }

    public static void registerGlobalExecutor(IExpressionExecutor executor) {
        _executor = Guard.notNull(executor, "executor");
    }

    public static IDeltaExtendsGenerator getDeltaExtendsGenerator() {
        return deltaExtendsGenerator;
    }

    public static void registerDeltaExtendsGenerator(IDeltaExtendsGenerator generator) {
        deltaExtendsGenerator = generator;
    }

    public static IExpressionExecutor getGlobalExecutor() {
        return _executor;
    }

    public static IEvalExprParser getDefaultExprParser() {
        return _exprParser;
    }

    public static void registerDefaultExprParser(IEvalExprParser exprParser) {
        _exprParser = exprParser;
    }

    public static boolean isGlobalVarName(String varName) {
        return varName.charAt(0) == '$';
    }

    public static Object execute(IExecutableExpression expr, IEvalScope scope) {
        return scope.getExpressionExecutor().execute(expr, scope);
    }

    public static IEvalScope newEvalScope(Map<String, Object> context) {
        EvalScopeImpl scope = new EvalScopeImpl(context);
        scope.setExpressionExecutor(_executor);
        return scope;
    }

    public static IEvalScope newEvalScope() {
        EvalScopeImpl scope = new EvalScopeImpl();
        scope.setExpressionExecutor(_executor);
        return scope;
    }
}
