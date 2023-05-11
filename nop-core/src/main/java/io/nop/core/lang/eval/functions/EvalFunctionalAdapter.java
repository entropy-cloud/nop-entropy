/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.eval.functions;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.functional.IEqualsChecker;
import io.nop.commons.functional.IFunctionN;
import io.nop.commons.functional.ITriFunction;
import io.nop.commons.functional.ITriPredicate;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;
import io.nop.core.resource.IResourceObjectLoader;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.*;

import static io.nop.api.core.convert.ConvertHelper.*;

/**
 * 实现IEvalFunction与常见java FunctionalInterface之间的适配转换
 */
public class EvalFunctionalAdapter
        implements Runnable, Callable<Object>, Supplier<Object>, BooleanSupplier, IntSupplier, DoubleSupplier,
        LongSupplier, Consumer<Object>, BiConsumer<Object, Object>, IntConsumer, DoubleConsumer, LongConsumer,
        Predicate<Object>, BiPredicate<Object, Object>, ITriPredicate<Object, Object, Object>, Function<Object, Object>,
        BiFunction<Object, Object, Object>, ITriFunction<Object, Object, Object, Object>, IFunctionN<Object>,
        LongToDoubleFunction, DoubleToLongFunction, DoubleToIntFunction, IntToDoubleFunction, LongToIntFunction,
        IntToLongFunction, ObjDoubleConsumer<Object>, ObjIntConsumer<Object>, ObjLongConsumer<Object>,
        ToDoubleBiFunction<Object, Object>, ToDoubleFunction<Object>, ToIntBiFunction<Object, Object>,
        ToIntFunction<Object>, ToLongBiFunction<Object, Object>, ToLongFunction<Object>, IntFunction<Object>,
        DoubleFunction<Object>, LongFunction<Object>, IntBinaryOperator, LongBinaryOperator, DoubleBinaryOperator,
        IEvalAction, IEvalPredicate, IEqualsChecker<Object>, IPropertyGetter, IPropertySetter, IResourceObjectLoader {
    public static final Set<Class<?>> SUPPORTED_INTERFACES = new HashSet<>(Arrays.asList(Runnable.class, Callable.class,
            Supplier.class, BooleanSupplier.class, IntSupplier.class, DoubleSupplier.class, LongSupplier.class,
            Consumer.class, BiConsumer.class, IntConsumer.class, DoubleConsumer.class, LongConsumer.class,
            Predicate.class, BiPredicate.class, ITriPredicate.class, Function.class, BiFunction.class,
            ITriFunction.class, IFunctionN.class, LongToDoubleFunction.class, DoubleToLongFunction.class,
            DoubleToIntFunction.class, IntToDoubleFunction.class, LongToIntFunction.class, IntToLongFunction.class,
            ObjDoubleConsumer.class, ObjIntConsumer.class, ObjLongConsumer.class, ToDoubleBiFunction.class,
            ToDoubleFunction.class, ToIntBiFunction.class, ToIntFunction.class, ToLongBiFunction.class,
            ToLongFunction.class, IntFunction.class, DoubleFunction.class, LongFunction.class, IntBinaryOperator.class,
            LongBinaryOperator.class, DoubleBinaryOperator.class, IEvalAction.class, IEvalPredicate.class,
            IEqualsChecker.class, IPropertyGetter.class, IPropertySetter.class));

    // private static final Object[] EMPTY_ARGS = new Object[0];

    private final SourceLocation loc;
    private final IEvalFunction function;
    private final IEvalScope scope;

    public EvalFunctionalAdapter(SourceLocation loc, IEvalFunction function, IEvalScope scope) {
        this.loc = loc;
        this.function = function;
        this.scope = scope;
    }

    @Override
    public Object apply(Object o) {
        return function.call1(null, o, scope);
    }

    @Override
    public Object apply(Object o, Object o2) {
        return function.call2(null, o, o2, scope);
    }

    @Override
    public Object apply(Object o, Object o2, Object o3) {
        return function.call3(null, o, o2, o3, scope);
    }

    @Override
    public boolean test(Object o, Object o2) {
        return ConvertHelper.toTruthy(apply(o, o2));
    }

    @Override
    public boolean test(Object o, Object o2, Object o3) {
        return ConvertHelper.toTruthy(apply(o, o2, o3));
    }

    @Override
    public void run() {
        function.call0(null, scope);
    }

    @Override
    public Object call() {
        return get();
    }

    @Override
    public void accept(Object o, Object o2) {
        function.call2(null, o, o2, scope);
    }

    @Override
    public void accept(Object o) {
        function.call1(null, o, scope);
    }

    @Override
    public boolean test(Object o) {
        return ConvertHelper.toTruthy(apply(o));
    }

    @Override
    public Object get() {
        return function.call0(null, scope);
    }

    @Override
    public Object applyN(Object[] args) {
        return function.invoke(null, args, scope);
    }

    @Override
    public boolean getAsBoolean() {
        return ConvertHelper.toTruthy(get(), this::buildError);
    }

    NopException buildError(ErrorCode code) {
        return new NopEvalException(code).loc(loc);
    }

    @Override
    public double getAsDouble() {
        return toPrimitiveDouble(get(), this::buildError);
    }

    @Override
    public int getAsInt() {
        return toPrimitiveInt(get(), this::buildError);
    }

    @Override
    public Object apply(double value) {
        return apply(Double.valueOf(value));
    }

    @Override
    public Object apply(int value) {
        return apply(Integer.valueOf(value));
    }

    @Override
    public void accept(double value) {
        accept(Double.valueOf(value));
    }

    @Override
    public void accept(int value) {
        accept(Integer.valueOf(value));
    }

    @Override
    public void accept(long value) {
        accept(Long.valueOf(value));
    }

    @Override
    public Object apply(long value) {
        return apply(Long.valueOf(value));
    }

    @Override
    public long getAsLong() {
        return toPrimitiveLong(get(), this::buildError);
    }

    class NegatePredicate implements PredicateEx {
        @Override
        public boolean test(Object o, Object o2) {
            return !EvalFunctionalAdapter.this.test(o, o2);
        }

        @Override
        public boolean test(Object o) {
            return !EvalFunctionalAdapter.this.test(o);
        }
    }

    @Override
    public PredicateEx negate() {
        return new NegatePredicate();
    }

    public EvalFunctionalAdapter andThen(Function then) {
        return new EvalFunctionalAdapter(loc, new ThenEvalFunction(function, then), scope);
    }

    @Override
    public int applyAsInt(long value) {
        return toPrimitiveInt(apply(Long.valueOf(value)), this::buildError);
    }

    @Override
    public long applyAsLong(int value) {
        return toPrimitiveLong(apply(Integer.valueOf(value)), this::buildError);
    }

    @Override
    public void accept(Object o, double value) {
        accept(o, Double.valueOf(value));
    }

    @Override
    public void accept(Object o, int value) {
        accept(o, Integer.valueOf(value));
    }

    @Override
    public void accept(Object o, long value) {
        accept(o, Long.valueOf(value));
    }

    @Override
    public double applyAsDouble(Object o, Object o2) {
        return toPrimitiveDouble(apply(o, o2), this::buildError);
    }

    @Override
    public double applyAsDouble(Object value) {
        return toPrimitiveDouble(apply(value), this::buildError);
    }

    @Override
    public int applyAsInt(Object o, Object o2) {
        return toPrimitiveInt(apply(o, o2), this::buildError);
    }

    @Override
    public int applyAsInt(Object value) {
        return toPrimitiveInt(apply(value), this::buildError);
    }

    @Override
    public long applyAsLong(Object o, Object o2) {
        return toPrimitiveLong(apply(o, o2), this::buildError);
    }

    @Override
    public long applyAsLong(Object value) {
        return toPrimitiveLong(apply(value), this::buildError);
    }

    @Override
    public int applyAsInt(double value) {
        return toPrimitiveInt(apply(Double.valueOf(value)), this::buildError);
    }

    @Override
    public long applyAsLong(double value) {
        return toPrimitiveLong(apply(Double.valueOf(value)), this::buildError);
    }

    @Override
    public double applyAsDouble(int value) {
        return toPrimitiveDouble(apply(Integer.valueOf(value)), this::buildError);
    }

    @Override
    public double applyAsDouble(long value) {
        return toPrimitiveDouble(apply(Long.valueOf(value)), this::buildError);
    }

    @Override
    public double applyAsDouble(double left, double right) {
        return toPrimitiveDouble(apply(Double.valueOf(left), Double.valueOf(right)), this::buildError);
    }

    @Override
    public int applyAsInt(int left, int right) {
        return toPrimitiveInt(apply(Integer.valueOf(left), Integer.valueOf(right)), this::buildError);
    }

    @Override
    public long applyAsLong(long left, long right) {
        return toPrimitiveLong(apply(Long.valueOf(left), Long.valueOf(right)), this::buildError);
    }

    @Override
    public Object invoke(IEvalContext ctx) {
        return function.call1(null, ctx, ctx.getEvalScope());
    }

    @Override
    public boolean passConditions(IEvalContext ctx) {
        return ConvertHelper.toTruthy(invoke(ctx));
    }

    @Override
    public boolean isEquals(Object o1, Object o2) {
        return test(o1, o2);
    }

    @Override
    public Object getProperty(Object obj, String propName, IEvalScope scope) {
        return function.call2(null, obj, propName, scope);
    }

    @Override
    public void setProperty(Object obj, String propName, Object value, IEvalScope scope) {
        function.call3(null, obj, propName, value, scope);
    }

    @Override
    public Object loadObjectFromPath(String path) {
        return apply(path);
    }
}