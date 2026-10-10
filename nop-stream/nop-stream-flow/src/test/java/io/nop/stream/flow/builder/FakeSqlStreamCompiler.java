package io.nop.stream.flow.builder;

import java.util.Map;

public class FakeSqlStreamCompiler implements io.nop.stream.flow.spi.ISqlStreamCompiler {

    /** WI37: records the emit mode passed by expandSqlModel (CLOSE when absent). */
    public io.nop.stream.core.windowing.StreamEmitMode lastEmit;

    /** WI25: records the field-lateness map passed by expandSqlModel (empty = none). */
    public Map<String, Long> lastFieldLateness;

    /** U2: records the SQL text passed by expandSqlTransforms. */
    public String lastTransformSql;

    @Override
    public String compileSql(String sql, Map<String, String> schema, String sinkBean,
                             io.nop.stream.core.windowing.StreamEmitMode emit,
                             Map<String, Long> fieldLateness) {
        this.lastEmit = emit;
        this.lastFieldLateness = fieldLateness;
        return "<stream x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "xmlns:x=\"/nop/schema/xdsl.xdef\" name=\"fake-compiled\" version=\"1\">"
                + "<transforms>"
                + "<source id=\"fakeSrc\" bean=\"fakeSrcFn\"/>"
                + "<map id=\"fakeMap\"><source>return event;</source></map>"
                + "<sink id=\"out\" bean=\"" + sinkBean + "\"/>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"fake-e0\" from=\"fakeSrc\" to=\"fakeMap\"/>"
                + "<edge id=\"fake-e1\" from=\"fakeMap\" to=\"out\"/>"
                + "</edges>"
                + "</stream>";
    }

    /**
     * U2: canned single-operator fragment (entry source + one filter) so flow-module
     * expansion tests can exercise the splice mechanics without the sql module on the
     * classpath. Shape mirrors StreamSqlCompiler.compileTransform's contract.
     */
    @Override
    public String compileTransform(String sql, Map<String, String> schema,
                                   io.nop.stream.core.windowing.StreamEmitMode emit,
                                   Map<String, Long> fieldLateness) {
        this.lastTransformSql = sql;
        this.lastEmit = emit;
        this.lastFieldLateness = fieldLateness;
        return "<stream x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "xmlns:x=\"/nop/schema/xdsl.xdef\" name=\"fake-fragment\" version=\"1\">"
                + "<transforms>"
                + "<source id=\"sqlTransformEntry\" bean=\"fragmentEntryPlaceholder\"/>"
                + "<filter id=\"fakeTFilter\"><source>return true;</source></filter>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"fakeT-e0\" from=\"sqlTransformEntry\" to=\"fakeTFilter\"/>"
                + "</edges>"
                + "</stream>";
    }
}
