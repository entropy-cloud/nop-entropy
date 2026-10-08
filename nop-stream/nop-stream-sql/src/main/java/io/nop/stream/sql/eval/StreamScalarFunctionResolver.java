/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.eval;

import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.flow.spi.IScalarFunctionResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * WI30: the nop-stream-sql provider of the flow-side {@code IScalarFunctionResolver}
 * SPI. Applications register custom scalar functions via {@link #register}; the
 * provider is registered through the Nop module app-beans mechanism (same as
 * {@code StreamAggregatorFunctionResolver}).
 *
 * <p>close(): the provider itself holds no resources, but AutoCloseable propagation
 * gives applications a deterministic teardown hook if they subclass and override.
 */
public class StreamScalarFunctionResolver implements IScalarFunctionResolver, AutoCloseable {

    private static volatile StreamScalarFunctionResolver defaultResolver;

    /**
     * The compile-time lookup instance — the WindowedStreamImpl.setDefaultFactory
     * precedent (the app-beans bean and this static instance are the same object in
     * production; tests set it directly without an IoC container).
     */
    public static StreamScalarFunctionResolver getDefault() {
        return defaultResolver;
    }

    public static void setDefault(StreamScalarFunctionResolver resolver) {
        defaultResolver = resolver;
    }

    private final Map<String, Function<Object[], Object>> functions = new LinkedHashMap<>();

    /** registers (or replaces) a scalar function under the given SQL call name. */
    public void register(String fnName, Function<Object[], Object> fn) {
        functions.put(fnName, fn);
    }

    @Override
    public boolean contains(String fnName) {
        return fnName != null && functions.containsKey(fnName);
    }

    @Override
    public Iterable<String> registeredNames() {
        return Collections.unmodifiableList(new ArrayList<>(functions.keySet()));
    }

    @Override
    public Function<Object[], Object> resolve(String fnName, TypeInformation<?>[] argTypes) {
        return functions.get(fnName);
    }

    /** the registered function, or null — used by the fail-fast to list the known names. */
    public List<String> namesList() {
        return new ArrayList<>(functions.keySet());
    }

    @Override
    public void close() {
        // no owned resources; subclass hook
    }

    /** bootstrap hook — the app-beans bean registers itself as the compile-time default. */
    public void registerAsDefault() {
        setDefault(this);
    }

    /**
     * Runtime entry for generated xpl bodies: the print channel emits
     * {@code StreamScalarFunctionResolver.call('fnName', arg0, ...)} with the class
     * imported (SqlRowAggregateOps precedent — XLang 裸 FQN 不可用，import + 静态调用).
     */
    public static Object call(String fnName, Object... args) {
        StreamScalarFunctionResolver r = defaultResolver;
        if (r == null)
            throw new IllegalStateException(
                    "no IScalarFunctionResolver default registered for UDF call: " + fnName);
        Function<Object[], Object> fn = r.functions.get(fnName);
        if (fn == null)
            throw new IllegalStateException("unknown UDF function: " + fnName);
        return fn.apply(args);
    }
}
