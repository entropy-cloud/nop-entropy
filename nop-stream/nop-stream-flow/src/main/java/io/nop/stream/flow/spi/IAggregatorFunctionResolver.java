/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.spi;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.typeinfo.BasicTypeInfo;

import java.util.function.Function;

/**
 * Build-time resolution SPI for parameterized aggregates (WI8c). The flow builder
 * looks the provider up by type in the {@code BeanContainer} at build time; the
 * nop-stream-sql module supplies the WI9-backed implementation. flow-only
 * applications have no provider — declaring {@code aggregatorRef} then fails fast
 * with a message naming the missing dependency.
 *
 * <p>Implementations own the full resolution semantics (adjudicated in plan 15):
 * <ul>
 *   <li>unknown fnId → fail fast (the builtin catalog resolves null)</li>
 *   <li>expression compile failure → fail fast</li>
 *   <li>argument count violation (count allows 0..1 args, others exactly 1) → fail fast</li>
 *   <li>argument type violation (sum/avg require numeric columns, min/max require
 *       Comparable, count any; unknown column types skip the check) → fail fast</li>
 * </ul>
 * The returned function's accumulation semantics must come from the aggregate
 * evaluator — this SPI only parses and assembles, never re-implements aggregation.
 */
public interface IAggregatorFunctionResolver {

    /**
     * Resolves one declared aggregator entry into a ready AggregateFunction.
     *
     * @param fnId        the aggregate function id (e.g. sum/count/avg/min/max)
     * @param expr        the value expression text (may be null for COUNT(*))
     * @param columnTypes column-key → type bridge built from the declared schema
     *                    (never null; may return null for unknown columns, which
     *                    skips type validation)
     * @return the assembled aggregate function
     * @throws io.nop.stream.core.exceptions.StreamException on any resolution failure
     */
    AggregateFunction<Object, Object, Object> resolve(String fnId, String expr,
                                                      Function<String, BasicTypeInfo<?>> columnTypes);
}
