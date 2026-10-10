package io.nop.stream.sql.compile;

import io.nop.stream.flow.spi.ISqlStreamCompiler;

import java.util.Map;

public class StreamSqlCompilerProvider implements ISqlStreamCompiler {

    private static final long serialVersionUID = 1L;

    @Override
    public String compileSql(String sql, Map<String, String> schema, String sinkBean,
                             io.nop.stream.core.windowing.StreamEmitMode emit,
                             Map<String, Long> fieldLateness) {
        return StreamSqlCompiler.compile(null, sql, schema, sinkBean, emit, fieldLateness);
    }

    @Override
    public String compileTransform(String sql, Map<String, String> schema,
                                   io.nop.stream.core.windowing.StreamEmitMode emit,
                                   Map<String, Long> fieldLateness) {
        return StreamSqlCompiler.compileTransform(null, sql, schema, emit, fieldLateness);
    }
}
