/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.compile;

import io.nop.stream.flow.spi.ISqlStreamCompiler;

import java.util.Map;

/**
 * WI17 (plan 25 r2 B4/B5): the nop-stream-sql provider of the flow-side
 * {@code <sql>} compilation SPI — a thin delegation to {@link StreamSqlCompiler}
 * (the full fail-fast matrix and product contract live there). Registered through the
 * Nop module app-beans mechanism so any application with nop-stream-sql on the
 * classpath can declare {@code <sql>} models.
 */
public class StreamSqlCompilerProvider implements ISqlStreamCompiler {

    private static final long serialVersionUID = 1L;

    @Override
    public String compileSql(String sql, Map<String, String> schema, String sinkBean) {
        return StreamSqlCompiler.compile(null, sql, schema, sinkBean);
    }
}
