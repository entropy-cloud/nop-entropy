/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.spi;

import java.util.Map;

/**
 * WI17 (plan 25 r2 B4): the model-level SQL compilation SPI. The {@code <sql>} element
 * is a model GENERATOR, not a transform: the flow builder looks the provider up by
 * type in the {@code BeanContainer} at build time, hands it the declared SQL text,
 * self-carried schema and sink bean, and replaces the parent model's content with the
 * returned stream-model XML (parsed back through {@code DslModelParser}). The
 * nop-stream-sql module supplies the implementation over the shared EQL parser
 * (D13 (a)); flow-only applications have no provider — declaring {@code <sql>} then
 * fails fast with a message naming the missing dependency.
 *
 * <p>The returned XML must be a complete, self-consistent {@code <stream>} model
 * (transforms, edges, registries, and the sink derived from {@code sinkBean}) that
 * passes stream.xdef validation and builds — the Anti-Hollow contract verified by the
 * sql-side wiring tests.
 */
public interface ISqlStreamCompiler {

    /**
     * Compiles one SQL query into a complete stream-model XML string.
     *
     * @param sql      the SQL text declared by the {@code <sql><source>} body
     * @param schema   the SQL-side self-carried schema (column name → D7 managed type
     *                 name); never null, may be empty
     * @param sinkBean the declared {@code sinkBean} attribute — the compiler appends
     *                 the sink declaration and edge so the product is executable
     * @return the model XML text
     * @throws io.nop.stream.core.exceptions.StreamException on any compile failure
     *                                                        (unsupported construct,
     *                                                        schema violation, ...)
     */
    String compileSql(String sql, Map<String, String> schema, String sinkBean);
}
