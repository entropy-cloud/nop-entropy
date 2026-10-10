/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.api.core.stream.ChangeSemanticView;
import io.nop.api.core.stream.RowKind;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.typeinfo.BasicTypeInfo;
import io.nop.stream.core.graph.StreamGraph;
import io.nop.stream.core.graph.StreamGraphGenerator;
import io.nop.stream.core.graph.StreamNode;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.transformation.SourceTransformation;
import io.nop.stream.core.util.OutputTag;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * U4 (deferred-unlock, plan 2377): the declared tableId on a source reaches the
 * emission points and stamps {@code withChangeMetadata(INSERT, tableId)} — with
 * value-provided {@code ChangeSemanticView} winning over the declaration, and the
 * declaration surviving {@code copyForSubtask} (every executed source in a real job
 * is a copy).
 */
class TestDeclaredTableIdStamping {

    /** Emits plain values — the historic default path. */
    static final class PlainSource implements SourceFunction<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void run(SourceContext<String> ctx) {
            ctx.collect("a");
            ctx.collectWithTimestamp("b", 42L);
        }

        @Override
        public void cancel() {
        }
    }

    /** Emits values that carry their own CDC semantics (Debezium bridge stand-in). */
    static final class SemanticSource implements SourceFunction<SemanticValue> {
        private static final long serialVersionUID = 1L;

        @Override
        public void run(SourceContext<SemanticValue> ctx) {
            ctx.collect(new SemanticValue("x", RowKind.DELETE, "value.table"));
        }

        @Override
        public void cancel() {
        }
    }

    static final class SemanticValue implements ChangeSemanticView {
        final String v;
        final RowKind kind;
        final String table;

        SemanticValue(String v, RowKind kind, String table) {
            this.v = v;
            this.kind = kind;
            this.table = table;
        }

        @Override
        public RowKind getRowKind() {
            return kind;
        }

        @Override
        public String getTableId() {
            return table;
        }
    }

    /** Records the collected records so their metadata can be asserted. */
    static final class RecordingOutput<T> implements Output<StreamRecord<T>> {
        final List<StreamRecord<T>> records = new ArrayList<>();

        @Override
        public void collect(StreamRecord<T> record) {
            records.add(record);
        }

        @Override
        public void emitWatermark(Watermark mark) {
        }

        @Override
        public void emitWatermarkStatus(WatermarkStatus status) {
        }

        @Override
        public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
        }

        @Override
        public void emitLatencyMarker(LatencyMarker marker) {
        }

        @Override
        public void emitBarrier(CheckpointBarrier barrier) {
        }

        @Override
        public void close() {
        }
    }

    private static <T> List<StreamRecord<T>> runOperator(StreamSourceOperator<T> op) throws Exception {
        RecordingOutput<T> output = new RecordingOutput<>();
        op.setOutput(output);
        op.run();
        return output.records;
    }

    @Test
    void declaredTableIdStampsInsertOnBothEmissionPaths() throws Exception {
        List<StreamRecord<String>> records = runOperator(
                new StreamSourceOperator<>(new PlainSource(), "db.orders"));
        assertEquals(2, records.size());
        for (StreamRecord<String> record : records) {
            assertEquals(RowKind.INSERT, record.getRowKind(),
                    "declared tableId stamps INSERT on collect and collectWithTimestamp");
            assertEquals("db.orders", record.getTableId());
        }
    }

    @Test
    void viewValueWinsOverDeclaredTableId() throws Exception {
        List<StreamRecord<SemanticValue>> records = runOperator(
                new StreamSourceOperator<>(new SemanticSource(), "db.declared"));
        assertEquals(1, records.size());
        assertEquals(RowKind.DELETE, records.get(0).getRowKind(),
                "the value's own CDC identity must not be overridden by the declaration");
        assertEquals("value.table", records.get(0).getTableId());
    }

    @Test
    void undeclaredSourceKeepsHistoricNullMetadata() throws Exception {
        List<StreamRecord<String>> records = runOperator(new StreamSourceOperator<>(new PlainSource()));
        assertEquals(2, records.size());
        for (StreamRecord<String> record : records) {
            assertNull(record.getRowKind(), "no declaration = historic insert-only default");
            assertNull(record.getTableId());
        }
    }

    @Test
    void copyForSubtaskCarriesDeclaration() {
        StreamSourceOperator<String> original = new StreamSourceOperator<>(new PlainSource(), "db.users");
        assertEquals("db.users", original.copyForSubtask().getDeclaredTableId(),
                "every executed source in a real job (any out-edge) is a copy — the declaration must survive");
        assertEquals("db.users", original.copyForSubtask(2).getDeclaredTableId(),
                "the sharded copy variant must carry the declaration too");
    }

    @Test
    void graphGeneratorCarriesDeclarationFromTransformationToOperator() {
        SourceTransformation<String> transformation = new SourceTransformation<>(
                "Source", new PlainSource(), BasicTypeInfo.of(String.class), 1);
        transformation.setDeclaredTableId("db.orders");

        StreamGraph graph = new StreamGraphGenerator()
                .generate(Collections.singletonList(transformation));
        StreamNode node = graph.getStreamNode(transformation.getId());
        StreamSourceOperator<?> operator =
                (StreamSourceOperator<?>) node.getOperatorFactory().createStreamOperator(null);
        assertEquals("db.orders", operator.getDeclaredTableId(),
                "the declaration must ride transformation → factory → operator");
    }

    @Test
    void blankDeclarationNormalizesToNull() {
        SourceTransformation<String> transformation = new SourceTransformation<>(
                "Source", new PlainSource(), BasicTypeInfo.of(String.class), 1);
        transformation.setDeclaredTableId("   ");
        assertNull(transformation.getDeclaredTableId(),
                "blank declaration = undeclared (no stamping), callers never guard the blank case");
    }
}
