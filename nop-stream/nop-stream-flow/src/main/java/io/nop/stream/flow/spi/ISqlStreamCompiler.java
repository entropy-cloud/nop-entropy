/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.spi;

import io.nop.stream.core.windowing.StreamEmitMode;

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
     * U2 (deferred-unlock): reserved id of the FROM-table source vertex in a
     * {@link #compileTransform} product. The builder strips this vertex and splices
     * the fragment's first operator onto the declaring {@code <sqlTransform>} vertex's
     * upstream; a product without exactly one source vertex carrying this id is
     * rejected at build time.
     */
    String TRANSFORM_ENTRY_ID = "sqlTransformEntry";

    /**
     * U2: compiles one SQL query into a TRANSFORM FRAGMENT for the
     * {@code <sqlTransform>} vertex. The product is a complete, xdef-valid
     * {@code <stream>} model XML (same shape as {@link #compileSql}) WITHOUT a sink:
     * the FROM-table source vertex keeps the reserved id {@link #TRANSFORM_ENTRY_ID}
     * and is stripped by the builder, whose upstream edge replaces it. Join queries
     * and UNION (two or more sources) fail fast — multi-entry splicing is not
     * supported. Emit/lateness semantics are identical to {@link #compileSql}.
     *
     * @param sql      the SQL text declared by the {@code <sqlTransform><source>} body
     * @param schema   the SQL-side self-carried schema (column name → D7 managed type
     *                 name); never null, may be empty
     * @param emit     the declared {@code emit} attribute (UPDATE only legal on the
     *                 windowed-aggregate path, same rule as {@link #compileSql})
     * @param fieldLateness the declared {@code <field lateness>} values in millis;
     *                      only the TUMBLE time column's value is honored
     * @return the fragment model XML text
     * @throws io.nop.stream.core.exceptions.StreamException on any compile failure
     */
    String compileTransform(String sql, Map<String, String> schema, StreamEmitMode emit,
                            Map<String, Long> fieldLateness);

    /**
     * Compiles one SQL query into a complete stream-model XML string.
     *
     * @param sql      the SQL text declared by the {@code <sql><source>} body
     * @param schema   the SQL-side self-carried schema (column name → D7 managed type
     *                 name); never null, may be empty
     * @param sinkBean the declared {@code sinkBean} attribute — the compiler appends
     *                 the sink declaration and edge so the product is executable
     * @param emit     the declared {@code <sql emit>} attribute (WI37/D11 two-tier
     *                 emit); never null after the xdef default (CLOSE) is applied —
     *                 {@code UPDATE} is only legal for windowed aggregate queries
     * @param fieldLateness the declared {@code <field lateness>} values in millis
     *                      (WI25/Q1: time-column lateness annotation); empty when no
     *                      field declares lateness — only the TUMBLE time column's
     *                      value is honored, other columns fail fast
     * @return the model XML text
     * @throws io.nop.stream.core.exceptions.StreamException on any compile failure
     *                                                        (unsupported construct,
     *                                                        schema violation, ...)
     */
    String compileSql(String sql, Map<String, String> schema, String sinkBean, StreamEmitMode emit,
                      Map<String, Long> fieldLateness);

    /**
     * WI37 compatibility overload: providers that only know the legacy CLOSE behavior
     * may keep implementing the three-argument form; the emitted mode is then CLOSE.
     */
    default String compileSql(String sql, Map<String, String> schema, String sinkBean) {
        return compileSql(sql, schema, sinkBean, StreamEmitMode.CLOSE, java.util.Collections.emptyMap());
    }

    /**
     * WI37 compatibility overload: emit without per-field lateness.
     */
    default String compileSql(String sql, Map<String, String> schema, String sinkBean, StreamEmitMode emit) {
        return compileSql(sql, schema, sinkBean, emit, java.util.Collections.emptyMap());
    }
}
