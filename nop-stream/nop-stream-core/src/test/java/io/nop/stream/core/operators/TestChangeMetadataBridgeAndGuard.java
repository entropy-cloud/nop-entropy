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
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.sink.CdcChangeAwareSink;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.streamrecord.StreamRecord;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_SINK_NOT_CHANGE_AWARE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI7 (plan 2336): the two ends of the engine-level change metadata wiring —
 * the source-emission bridge (a {@code ChangeSemanticView} value stamps the built
 * record) and the sink guard (non-INSERT metadata reaching a sink that does not
 * declare {@link CdcChangeAwareSink} fails typed).
 */
class TestChangeMetadataBridgeAndGuard {

    /** A minimal semantic value (a stand-in for the Debezium ChangeEvent bridge). */
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

    private static final class RecordingSink implements SinkFunction<String> {
        final List<String> values = new ArrayList<>();

        @Override
        public void consume(String value) throws Exception {
            values.add(value);
        }
    }

    private static final class ChangeAwareSink implements SinkFunction<String>, CdcChangeAwareSink {
        final List<String> values = new ArrayList<>();

        @Override
        public void consume(String value) throws Exception {
            values.add(value);
        }
    }

    // ==================== bridge ====================

    @Test
    void bridgeStampsSemanticValues() {
        StreamRecord<String> record = new StreamRecord<>("x");
        StreamSourceOperator.applyChangeMetadata(record, new SemanticValue("x", RowKind.DELETE, "db.orders"));
        assertEquals(RowKind.DELETE, record.getRowKind());
        assertEquals("db.orders", record.getTableId());
    }

    @Test
    void bridgeIsNoopForPlainValues() {
        StreamRecord<String> record = new StreamRecord<>("x");
        StreamSourceOperator.applyChangeMetadata(record, "plain");
        assertNull(record.getRowKind(), "historic default: plain values keep insert-only semantics");
        assertNull(record.getTableId());
    }

    // ==================== guard ====================

    @Test
    void guardPassesInsertOnlyRecordsToAnySink() throws Exception {
        RecordingSink sink = new RecordingSink();
        StreamSinkOperator<String> op = new StreamSinkOperator<>(sink);
        op.processElement(new StreamRecord<>("plain"));
        op.processElement(new StreamRecord<>("cdc").withChangeMetadata(RowKind.INSERT, "t"));
        assertEquals(2, sink.values.size(), "insert-only records flow to every sink");
    }

    @Test
    void guardFailsTypedForChangeRecordOnUnawareSink() {
        RecordingSink sink = new RecordingSink();
        StreamSinkOperator<String> op = new StreamSinkOperator<>(sink);
        StreamRecord<String> record = new StreamRecord<>("x")
                .withChangeMetadata(RowKind.DELETE, "db.orders");

        StreamException ex = assertThrows(StreamException.class, () -> op.processElement(record));
        assertEquals(ERR_STREAM_SINK_NOT_CHANGE_AWARE.getErrorCode(), ex.getErrorCode());
        assertTrue(String.valueOf(ex).contains(sink.getClass().getName()),
                "error must name the offending sink type: " + ex);
        assertTrue(sink.values.isEmpty(), "the record must not reach the unaware sink");
    }

    @Test
    void changeAwareSinkConsumesChangeRecords() throws Exception {
        ChangeAwareSink sink = new ChangeAwareSink();
        StreamSinkOperator<String> op = new StreamSinkOperator<>(sink);
        op.processElement(new StreamRecord<>("x").withChangeMetadata(RowKind.UPDATE_AFTER, "db.orders"));
        assertEquals(List.of("x"), sink.values, "declared change-aware sinks pass the guard");
    }
}
