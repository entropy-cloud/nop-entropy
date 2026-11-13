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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    private static final Logger LOG = LoggerFactory.getLogger(StreamScalarFunctionResolver.class);

    private static volatile StreamScalarFunctionResolver defaultResolver;

    /**
     * The compile-time lookup instance — the WindowedStreamImpl.setDefaultFactory
     * precedent (the app-beans bean and this static instance are the same object in
     * production; tests set it directly without an IoC container).
     *
     * <p>Runtime rebinding (calling {@link #setDefault} again after a default is
     * already registered) is permitted — the registration initiator is the
     * application side and the framework has no determinable startup-window
     * contract anchor, so fail-fast would turn a legitimate hot swap into an
     * unwarned deployment failure. It is recorded with a WARN instead; the new
     * resolver's function table is authoritative for subsequent per-record
     * lookups without recompilation.
     */
    public static StreamScalarFunctionResolver getDefault() {
        return defaultResolver;
    }

    public static void setDefault(StreamScalarFunctionResolver resolver) {
        StreamScalarFunctionResolver previous = defaultResolver;
        if (previous != null && previous != resolver) {
            LOG.warn("nop.stream.sql.udf.default-rebound: scalar function resolver "
                    + "rebound at runtime; the new resolver's function table is "
                    + "authoritative for subsequent per-record lookups (previous={}, new={})",
                    previous.getClass().getName(), resolver == null ? "null"
                            : resolver.getClass().getName());
        }
        defaultResolver = resolver;
    }

    /**
     * Concurrent registry: {@code register} is callable from any thread at any
     * time (application-side hot swap), while the per-record lookup hot path
     * ({@link #call}) reads without locking. A new registration takes effect
     * immediately; overwriting an existing name affects subsequent lookups
     * immediately (rebinding semantics, see {@link #setDefault}).
     */
    private final Map<String, Function<Object[], Object>> functions = new ConcurrentHashMap<>();

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

    /**
     * CRA 8.45 R4-05 W-09 (1.3-C6-003): identity-guarded unbind of the static
     * default. Only THIS instance being the registered default is unbound — a
     * non-default instance's close is a legal lifecycle event (bean-container
     * propagation, owner doc §3a) and must not touch someone else's registration.
     * The unbind goes through {@link #setDefault(null)} so the rebinding WARN
     * channel leaves the trail; late {@link #call} lookups afterwards hit the
     * pre-existing null fail-fast (plan 2512 channel). Replacement does NOT
     * propagate close: {@code setDefault(A → B)} leaves A untouched — ownership of
     * closing A stays with A's registrant (the WindowedStreamImpl.setDefaultFactory
     * precedent). Close is idempotent; re-registration after close is legal.
     */
    @Override
    public void close() {
        if (defaultResolver == this) {
            setDefault(null);
        }
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
