/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.xlang;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态基准语料（I12 Phase 2 D2 裁定二）：benchmark 载体**自带语料定义**（main 域，无
 * test-jar 消费），经既有动态编译出口（compileSimpleExpr / compileFullExpr /
 * compileTemplateExpr）取树，执行直连 {@code XLang.execute} choke point 真实出口
 * （truffle 列——路由裁决到 truffle 后端池运行时）或解释器显式旁路（直驱全局执行器）。
 *
 * <p>预期值 setup 期断言（正确性前置——错误后端的输出即红灯，防测错后端污染计时）。
 */
public final class DynamicCorpus {

    public enum Form {
        SIMPLE, FULL, TEMPLATE
    }

    public static final class Unit {
        final String name;
        final String source;
        final Form form;
        final Map<String, Object> vars;
        final Object expected;

        Unit(String name, String source, Form form, Map<String, Object> vars, Object expected) {
            this.name = name;
            this.source = source;
            this.form = form;
            this.vars = vars;
            this.expected = expected;
        }

        public String getName() {
            return name;
        }

        public String getSourceKey() {
            return "bm:dyn:" + name;
        }

        public IExecutableExpression compile() {
            SourceLocation loc = SourceLocation.fromPath(getSourceKey());
            ExprEvalAction action;
            switch (form) {
                case FULL:
                    action = XLang.newCompileTool().allowUnregisteredScopeVar(true)
                            .compileFullExpr(loc, source);
                    break;
                case TEMPLATE:
                    action = XLang.newCompileTool().allowUnregisteredScopeVar(true)
                            .compileTemplateExpr(loc, source);
                    break;
                case SIMPLE:
                default:
                    action = XLang.newCompileTool().allowUnregisteredScopeVar(true)
                            .compileSimpleExpr(loc, source);
                    break;
            }
            return action.getExpr();
        }

        public IEvalScope newScope() {
            return EvalExprProvider.newEvalScope(new LinkedHashMap<>(vars));
        }
    }

    private DynamicCorpus() {
    }

    public static List<Unit> units() {
        List<Unit> units = new ArrayList<>();
        units.add(new Unit("arith", "3 + 4", Form.SIMPLE, Map.of(), 7));
        units.add(new Unit("concat", "'ab' + '!'", Form.SIMPLE, Map.of(), "ab!"));
        units.add(new Unit("vars", "x + y * 2", Form.SIMPLE, StaticCorpus.vars("x", 3, "y", 4), 11));
        units.add(new Unit("logic", "x > 2 && y < 10", Form.SIMPLE,
                StaticCorpus.vars("x", 3, "y", 4), Boolean.TRUE));
        units.add(new Unit("method", "'hello'.toUpperCase()", Form.SIMPLE, Map.of(), "HELLO"));
        units.add(new Unit("ternary", "x > 0 ? 'pos' : 'neg'", Form.SIMPLE,
                StaticCorpus.vars("x", 5), "pos"));
        units.add(new Unit("list", "xs.size()", Form.SIMPLE,
                StaticCorpus.vars("xs", java.util.List.of(1, 2, 3)), 3));
        units.add(new Unit("iife", "((x) => x + 8)(1)", Form.FULL, Map.of(), 9));
        units.add(new Unit("closure", "let f = x => x + 1; f(2)", Form.FULL, Map.of(), 3));
        units.add(new Unit("template", "a${1 + 2}c", Form.TEMPLATE, Map.of(), "a3c"));
        return units;
    }

    /** 预期值核验（setup 期调用；任何列执行失败/值不符即 fail-fast） */
    public static void verifyInterpreterBaseline() {
        for (Unit unit : units()) {
            IExecutableExpression tree = unit.compile();
            Object value = EvalExprProvider.getGlobalExecutor()
                    .execute(tree, new io.nop.core.lang.eval.EvalRuntime(unit.newScope(),
                            io.nop.core.lang.eval.DisabledEvalOutput.INSTANCE));
            if (!java.util.Objects.equals(unit.expected, value))
                throw new IllegalStateException("dynamic corpus unit " + unit.getName()
                        + " interpreter baseline mismatch: expected=" + unit.expected + ", was=" + value);
        }
    }

    /** 翻译缓存隔离基准的工作集语料（64 棵互异树——"x + i" 变体） */
    public static List<IExecutableExpression> translationWorkload(int size) {
        List<IExecutableExpression> trees = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            SourceLocation loc = SourceLocation.fromPath("bm:cache:work" + i);
            trees.add(XLang.newCompileTool().allowUnregisteredScopeVar(true)
                    .compileSimpleExpr(loc, "x + " + i).getExpr());
        }
        return trees;
    }

    static Map<String, Object> emptyVars() {
        return new LinkedHashMap<>();
    }
}
