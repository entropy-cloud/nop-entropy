/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.evictors.Evictor;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.Window;
import io.nop.stream.runtime.operators.windowing.functions.InternalIterableProcessWindowFunction;
import io.nop.stream.runtime.operators.windowing.functions.InternalIterableWindowFunction;

/**
 * 可测试的判定辅助类（门禁 ①）：把「call-site 实参 → WindowOperator 构造器字段」的 round-trip
 * 判定提取为独立组件，反例 fixture（如漏传 allowedLateness 的桩 factory）作用于本组件，
 * 无需在真实类上注入违背代码。
 *
 * <p>断言映射（以 call-site 实参表为准，8 实参，映射表内明示）：
 * <table border="1">
 *   <tr><th>call-site 实参</th><th>WindowOperator 构造器字段 / 断言目标</th></tr>
 *   <tr><td>windowAssigner</td><td>构造器 windowAssigner（同一实例）</td></tr>
 *   <tr><td>trigger</td><td>trigger（同一实例）</td></tr>
 *   <tr><td>evictor</td><td>evictor（同一实例或双 null）</td></tr>
 *   <tr><td>allowedLateness</td><td>allowedLateness（数值相等）</td></tr>
 *   <tr><td>function</td><td>wrapped userFunction（apply/process 解包 InternalIterable*
 *       wrappedFunction；aggregate/reduce 无 evictor 经 AggregatingStateDescriptor
 *       getAggregateFunction；有 evictor 经 Buffering*ProcessWindowFunction 内字段）</td></tr>
 *   <tr><td>elementType</td><td>accClass（构造器第 10 参）/ ListStateDescriptor valueType</td></tr>
 *   <tr><td>keySelector</td><td>keySelector（同一实例）</td></tr>
 *   <tr><td>keyClass</td><td>keyClass（同一 Class）</td></tr>
 * </table>
 * 构造器本身 14 参（windowAssigner/windowSerializer/keySelector/keySerializer/keyClass/
 * windowFunction/trigger/allowedLateness/lateDataOutputTag/accClass/stateDesc/mergeFn/evictor/
 * accumulationMode），本组件只断言 call-site 8 实参的传输完备性。
 */
public class WindowRoundTripAssertions {

    static Object readField(Object target, String fieldName) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignore) {
                c = c.getSuperclass();
            } catch (Exception e) {
                throw new IllegalStateException("failed to read " + fieldName + " on " + target.getClass(), e);
            }
        }
        throw new IllegalStateException("no field " + fieldName + " on " + target.getClass());
    }

    @SuppressWarnings("unchecked")
    static <W extends Window> List<String> checkRoundTrip(
            WindowOperator<?, ?, ?, ?, ?> op,
            TestWindowRoundTripInvariant.CallSite callSite,
            Object function,
            boolean withEvictor) {
        List<String> violations = new ArrayList<>();

        WindowAssigner<?, W> opAssigner = (WindowAssigner<?, W>) readField(op, "windowAssigner");
        if (opAssigner != TestWindowRoundTripInvariant.WINDOW_ASSIGNER) {
            violations.add("windowAssigner: expected call-site instance, got " + opAssigner);
        }

        Trigger<?, ?> opTrigger = (Trigger<?, ?>) readField(op, "trigger");
        if (opTrigger != TestWindowRoundTripInvariant.TRIGGER) {
            violations.add("trigger: expected call-site instance, got " + opTrigger);
        }

        Evictor<?, ?> opEvictor = (Evictor<?, ?>) readField(op, "evictor");
        Evictor<?, ?> expectedEvictor = withEvictor ? TestWindowRoundTripInvariant.EVICTOR : null;
        if (opEvictor != expectedEvictor) {
            violations.add("evictor: expected " + expectedEvictor + ", got " + opEvictor);
        }

        long opAllowedLateness = (Long) readField(op, "allowedLateness");
        if (opAllowedLateness != TestWindowRoundTripInvariant.ALLOWED_LATENESS) {
            violations.add("allowedLateness: expected " + TestWindowRoundTripInvariant.ALLOWED_LATENESS
                    + ", got " + opAllowedLateness);
        }

        Object userFunction = readField(op, "userFunction");
        checkFunctionReached(op, callSite, function, withEvictor, userFunction, violations);

        // elementType → accClass（构造器第 10 参）与 ListStateDescriptor valueType。
        // P1-01：AGGREGATE call-site 传 Object.class（泛型擦除），工厂从 live 函数的
        // createAccumulator() 推断真实累加器类型（Object 类型槽会让 RocksDB 读路径把
        // long[]/int[] 累加器还原成 ArrayList）——因此 AGGREGATE 的期望值是推断类型。
        Object accClass = readField(op, "accClass");
        Class<?> expectedAccClass = TestWindowRoundTripInvariant.ELEMENT_TYPE;
        if (callSite == TestWindowRoundTripInvariant.CallSite.AGGREGATE && function instanceof AggregateFunction) {
            Object acc = ((AggregateFunction<?, ?, ?>) function).createAccumulator();
            expectedAccClass = acc != null ? acc.getClass() : TestWindowRoundTripInvariant.ELEMENT_TYPE;
        }
        if (accClass != expectedAccClass) {
            violations.add("accClass (elementType slot): expected " + expectedAccClass
                    + ", got " + accClass);
        }
        Object stateDesc = readField(op, "windowStateDescriptor");
        if (stateDesc instanceof ListStateDescriptor) {
            Class<?> valueType = ((ListStateDescriptor<?>) stateDesc).getValueType();
            if (valueType != TestWindowRoundTripInvariant.ELEMENT_TYPE) {
                violations.add("ListStateDescriptor valueType: expected "
                        + TestWindowRoundTripInvariant.ELEMENT_TYPE + ", got " + valueType);
            }
        }

        Object opKeySelector = readField(op, "keySelector");
        if (opKeySelector != TestWindowRoundTripInvariant.KEY_SELECTOR) {
            violations.add("keySelector: expected call-site instance, got " + opKeySelector);
        }

        Object opKeyClass = readField(op, "keyClass");
        if (opKeyClass != TestWindowRoundTripInvariant.KEY_CLASS) {
            violations.add("keyClass: expected " + TestWindowRoundTripInvariant.KEY_CLASS + ", got " + opKeyClass);
        }

        return violations;
    }

    @SuppressWarnings("unchecked")
    private static void checkFunctionReached(
            WindowOperator<?, ?, ?, ?, ?> op,
            TestWindowRoundTripInvariant.CallSite callSite,
            Object function,
            boolean withEvictor,
            Object userFunction,
            List<String> violations) {
        switch (callSite) {
            case APPLY:
                if (!(userFunction instanceof InternalIterableWindowFunction)) {
                    violations.add("function: apply must wrap user function in InternalIterableWindowFunction, got "
                            + (userFunction == null ? "null" : userFunction.getClass().getName()));
                    return;
                }
                if (readField(userFunction, "wrappedFunction") != function) {
                    violations.add("function: wrappedFunction must be the call-site WindowFunction instance");
                }
                break;
            case PROCESS:
                if (!(userFunction instanceof InternalIterableProcessWindowFunction)) {
                    violations.add("function: process must wrap user function in InternalIterableProcessWindowFunction, got "
                            + (userFunction == null ? "null" : userFunction.getClass().getName()));
                    return;
                }
                if (readField(userFunction, "wrappedFunction") != function) {
                    violations.add("function: wrappedFunction must be the call-site ProcessWindowFunction instance");
                }
                break;
            case AGGREGATE:
            case REDUCE: {
                if (!withEvictor) {
                    Object stateDesc = readField(op, "windowStateDescriptor");
                    if (!(stateDesc instanceof AggregatingStateDescriptor)) {
                        violations.add("function: aggregate/reduce without evictor must carry AggregatingStateDescriptor, got "
                                + (stateDesc == null ? "null" : stateDesc.getClass().getName()));
                        return;
                    }
                    Object aggFn = ((AggregatingStateDescriptor<?, ?, ?>) stateDesc).getAggregateFunction();
                    if (aggFn == null) {
                        violations.add("function: AggregatingStateDescriptor must carry an aggregate function");
                        return;
                    }
                    if (callSite == TestWindowRoundTripInvariant.CallSite.AGGREGATE) {
                        if (aggFn != function) {
                            violations.add("function: descriptor aggregateFunction must be the call-site AggregateFunction instance");
                        }
                    } else {
                        // reduce：函数被 reduceFunctionAsAggregate 包装，行为必须等价于 call-site ReduceFunction
                        ReduceFunction<Integer> reduceFn = (ReduceFunction<Integer>) function;
                        AggregateFunction<Integer, Integer, Integer> agg =
                                (AggregateFunction<Integer, Integer, Integer>) aggFn;
                        try {
                            int expected = reduceFn.reduce(5, 3);
                            int actual = agg.add(5, 3);
                            if (expected != actual) {
                                violations.add("function: reduce wrapper behavior diverges from call-site ReduceFunction: expected "
                                        + expected + " got " + actual);
                            }
                        } catch (Exception e) {
                            violations.add("function: reduce wrapper threw: " + e);
                        }
                    }
                } else {
                    // 有 evictor：走 ListStateDescriptor + Buffering*ProcessWindowFunction 路径
                    if (!(userFunction instanceof InternalIterableProcessWindowFunction)) {
                        violations.add("function: aggregate/reduce with evictor must wrap in InternalIterableProcessWindowFunction, got "
                                + (userFunction == null ? "null" : userFunction.getClass().getName()));
                        return;
                    }
                    Object buffering = readField(userFunction, "wrappedFunction");
                    Object innerFn = readField(buffering, callSite == TestWindowRoundTripInvariant.CallSite.AGGREGATE
                            ? "aggregateFunction" : "reduceFunction");
                    if (innerFn != function) {
                        violations.add("function: buffering wrapper must hold the call-site function instance");
                    }
                }
                break;
            }
            default:
                violations.add("function: unknown call-site " + callSite);
                break;
        }
    }
}
