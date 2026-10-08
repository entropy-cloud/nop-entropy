/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.spi;

import io.nop.stream.core.common.typeinfo.TypeInformation;

import java.util.function.Function;

/**
 * WI30: build-time SPI for user-defined scalar functions. SQL scalar expressions may
 * call a registered function by name; the flow builder looks the provider up by type
 * in the BeanContainer (the {@code IAggregatorFunctionResolver} precedent — the
 * roadmap's literal "ServiceLoader" wording is superseded by the same IoC mechanism
 * every other stream SPI uses; disclosed in the WI30 plan).
 *
 * <p>Lifecycle: the returned function may hold resources; providers implement
 * {@link AutoCloseable} to propagate close through the module bean container.
 */
public interface IScalarFunctionResolver {

    /** whether a function with this name is registered. */
    boolean contains(String fnName);

    /** all registered function names — surfaced in the unknown-function fail-fast. */
    Iterable<String> registeredNames();

    /**
     * The registered scalar function: maps evaluated argument values to the result.
     *
     * @param fnName the SQL call name (case-sensitive as registered)
     * @param argTypes the declared argument types (compile-time; may be used for
     *                 overload validation)
     */
    Function<Object[], Object> resolve(String fnName, TypeInformation<?>[] argTypes);
}
