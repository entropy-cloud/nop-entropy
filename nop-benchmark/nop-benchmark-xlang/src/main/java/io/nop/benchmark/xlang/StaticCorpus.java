/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.xlang;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.EvalExprProvider;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 静态基准语料（I12 Phase 2 D2 裁定一）：e2e 模块 main classpath 生产形态单元全集的
 * **xpl 子集**（52 = corpus 物化 48 + e2e 原生 4），三向对比耗时基准裁选 46 单元。
 *
 * <p><b>子集裁选（显式记录，套件侧不受影响——Phase 1 全量 74 单元直跑）</b>：
 * <ul>
 * <li>3 个 xlib 标签单元排除——标签经 invoker 帧协议调用（attrs/slots 实参组 + 帧），
 * 非独立编译单元的逐单元计时形态；其 body 语义已由 corpus xpl 单元族覆盖。</li>
 * <li>6 个异常单元排除（exception-method / exception-prop / exception-convert /
 * exception-fn-throw / exception-throw / e2e exception.xpl）——主路径 = 抛异常，
 * 异常构造成本主导，不适合同批耗时对比（正确性语义由套件三层断言承载）。</li>
 * </ul>
 *
 * <p>输入变量 = corpus 声明的镜像（载体内语料知识，非 test-jar 消费）；setup 期断言
 * 全部 52 xpl 键 ∈ 生产扫描清单（manifest 成员资格——防静默测错语料）。
 */
public final class StaticCorpus {

    /** 耗时基准单元（46） */
    public static final class Unit {
        private final String path;
        private final Map<String, Object> vars;

        Unit(String path, Map<String, Object> vars) {
            this.path = path;
            this.vars = vars;
        }

        public String getPath() {
            return path;
        }

        public IEvalScope newScope() {
            // 可变拷贝：assign() 系列单元向 scope 变量写入（Map.of 不可变——harness 同款拷贝语义）
            return EvalExprProvider.newEvalScope(new LinkedHashMap<>(vars));
        }
    }

    private static final String CORPUS = "/test/xlang/e2e/corpus/";
    private static final String E2E = "/test/xlang/e2e/";

    private StaticCorpus() {
    }

    public static Map<String, Object> vars(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2)
            m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    /** 耗时基准语料（46 单元；载体内硬编码语料知识 + 变量镜像） */
    public static List<Unit> timedUnits() {
        List<Unit> units = new ArrayList<>();
        // ---- corpus static（11 − exception-method = 10）----
        units.add(new Unit(CORPUS + "static/arith-plus.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static/arith-string-concat.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static/combo.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static/compare-lt.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static/literal-int.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static/literal-string.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static/logic-and-or.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static/method-instance.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static/method-static-side-effect.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static/slot-identifier.xpl", Map.of()));
        // ---- corpus static-a（20 − exception-prop − exception-convert = 18）----
        units.add(new Unit(CORPUS + "static-a/scope-chain.xpl", vars("x", 5)));
        units.add(new Unit(CORPUS + "static-a/scope-assign.xpl", vars("n", 5)));
        units.add(new Unit(CORPUS + "static-a/scope-self-assign.xpl", vars("n", 5)));
        units.add(new Unit(CORPUS + "static-a/global-var.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-a/type-convert.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-a/type-instanceof.xpl", vars("d", new Date(0))));
        units.add(new Unit(CORPUS + "static-a/typeof.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-a/obj-new.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-a/list-spread.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-a/map-access.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-a/map-write.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-a/binding-array.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-a/binding-object.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-a/debug-call.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-a/slot-write.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-a/residual-ops.xpl", vars("x", 5)));
        units.add(new Unit(CORPUS + "static-a/null-checks.xpl", vars("x", 5)));
        units.add(new Unit(CORPUS + "static-a/prop-in.xpl", Map.of()));
        // ---- corpus static-b（17 − exception-fn-throw − exception-throw = 15）----
        units.add(new Unit(CORPUS + "static-b/ctrl-for-break-continue.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-b/ctrl-forin.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-b/ctrl-loops.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-b/ctrl-switch.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-b/fn-arrow-call.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-b/fn-closure-cell.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-b/fn-local-call.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-b/tpl-collect-node.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-b/tpl-collect-sql.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-b/tpl-collect-text.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-b/tpl-node-simple.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-b/tpl-node.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-b/tpl-text.xpl", Map.of()));
        units.add(new Unit(CORPUS + "static-b/tpl-xml-extattrs.xpl",
                vars("cnt", vars("b", 2))));
        units.add(new Unit(CORPUS + "static-b/tpl-xml.xpl", Map.of()));
        // ---- e2e 原生（4 − exception.xpl = 3）----
        units.add(new Unit(E2E + "control.xpl", vars("name", "xlang")));
        units.add(new Unit(E2E + "expr.xpl", vars("name", "xlang")));
        units.add(new Unit(E2E + "template.xpl", vars("name", "xlang")));
        return units;
    }

    /** 被裁选排除的 6 个异常单元（显式记录面——报告引用） */
    public static List<String> excludedExceptionUnits() {
        return List.of(CORPUS + "static/exception-method.xpl",
                CORPUS + "static-a/exception-prop.xpl",
                CORPUS + "static-a/exception-convert.xpl",
                CORPUS + "static-b/exception-fn-throw.xpl",
                CORPUS + "static-b/exception-throw.xpl",
                E2E + "exception.xpl");
    }

    /**
     * 生产形态核验：全部 52 xpl 键 ∈ 扫描清单，且清单总数 = 55（52 xpl + 3 标签）。
     * 任何缺席即 setup fail-fast（JMH error，No Silent No-Op）。
     */
    public static void verifyAgainstProductionManifest(Set<String> scanList) {
        if (scanList.size() != 55)
            throw new IllegalStateException("production scan list must carry 55 units (52 xpl + 3 tags), was: "
                    + scanList.size());
        List<String> xplKeys = new ArrayList<>();
        for (String key : scanList)
            if (key.endsWith(".xpl"))
                xplKeys.add(key);
        if (xplKeys.size() != 52)
            throw new IllegalStateException("scan list xpl keys must be 52, was: " + xplKeys.size());
        for (String excluded : excludedExceptionUnits())
            if (!scanList.contains(excluded))
                throw new IllegalStateException("excluded exception unit missing from manifest: " + excluded);
        for (Unit unit : timedUnits())
            if (!scanList.contains(unit.getPath()))
                throw new IllegalStateException("timed unit missing from production manifest: " + unit.getPath());
    }
}
