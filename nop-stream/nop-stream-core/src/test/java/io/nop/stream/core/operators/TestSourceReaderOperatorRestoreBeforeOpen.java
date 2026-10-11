package io.nop.stream.core.operators;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.source.Boundedness;
import io.nop.stream.core.source.Source;
import io.nop.stream.core.source.SourceReader;
import io.nop.stream.core.source.SourceReaderContext;
import io.nop.stream.core.source.SimpleVersionedSerializer;
import io.nop.stream.core.source.SourceSplit;
import io.nop.stream.core.source.SplitEnumerator;
import io.nop.stream.core.source.SplitEnumeratorContext;
import io.nop.stream.core.source.coordinator.SourceCoordinatorRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-6.7-008（plan 2387）：SourceReaderOperator.restoreState 在 restore-先于-open 时序
 * （SupervisionLoop.rebuildTask → RescaleStateAssembler.restoreOperatorsFromState 作用于
 * 新建链，open() 未发生）不得静默丢弃 per-split 位点——暂存后在 open() 内、
 * reader.start() 之前 flush（SourceReader.restoreState 契约 "Called before start()"）。
 * 恢复→重开→续读的 reader 侧位点不丢；reader 已存在时直接恢复路径行为不变。
 */
class TestSourceReaderOperatorRestoreBeforeOpen {

    /** 独立 vertexId，避免静态 SourceCoordinatorRegistry 跨测试串扰。 */
    private static final int VERTEX_ID = 987_654;

    private SourceReaderOperator<String> operator;

    @AfterEach
    void cleanupRegistry() {
        if (operator != null) {
            try {
                operator.close();
            } catch (Exception ignored) {
                // teardown best-effort: close() failure must not mask the test result;
                // the reader stub's close() is a no-op in practice.
            }
        }
        SourceCoordinatorRegistry.unregister(VERTEX_ID);
    }

    /**
     * 缺陷场景：restoreState 先于 open()（恢复路径作用于新建链）。修复前位点被
     * reader==null 守卫静默丢弃；修复后暂存并在 open() 内 flush 进 reader，且发生在
     * reader.start() 之前（替换语义先于 enumerator 交付追加、先于 start）。
     */
    @Test
    void testRestoreBeforeOpenDeliversCursorsToReaderBeforeStart() throws Exception {
        RecordingSource source = new RecordingSource();
        operator = new SourceReaderOperator<>(source, VERTEX_ID);
        operator.setSubtaskIdentity(0, 1);

        OperatorSnapshotResult snapshot = new OperatorSnapshotResult();
        snapshot.putOperatorState(SourceReaderOperator.READER_SPLITS_KEY,
                List.of(new SimpleSplit("split-restore")));

        // restore 先于 open——缺陷时序
        operator.restoreState(snapshot);
        assertTrue(source.reader.restoredSplits.isEmpty(),
                "cursors must be stashed, not delivered to a not-yet-created reader");

        operator.open();

        assertEquals(List.of("split-restore"), source.reader.restoredSplits,
                "restored cursors must reach the reader at open() (P1-6.7-008)");
        assertTrue(source.reader.restoredBeforeStart,
                "restore flush must happen before reader.start() per SourceReader contract");
    }

    /** 既有行为回归：reader 已存在（open 先于 restore）时直接恢复路径不变。 */
    @Test
    void testRestoreAfterOpenKeepsDirectPath() throws Exception {
        RecordingSource source = new RecordingSource();
        operator = new SourceReaderOperator<>(source, VERTEX_ID);
        operator.setSubtaskIdentity(0, 1);
        operator.open();

        OperatorSnapshotResult snapshot = new OperatorSnapshotResult();
        snapshot.putOperatorState(SourceReaderOperator.READER_SPLITS_KEY,
                List.of(new SimpleSplit("split-direct")));

        operator.restoreState(snapshot);

        assertEquals(List.of("split-direct"), source.reader.restoredSplits,
                "direct restore path (reader already open) must be unchanged");
    }

    /**
     * restore→open→run 全链：恢复位点进 reader 后 run() 可正常驱动并退出
     * （bounded stub 无记录即完成，判别力在位点断言——run 腿证明恢复后管线可运行）。
     */
    @Test
    void testRestoreThenOpenThenRunCompletes() throws Exception {
        RecordingSource source = new RecordingSource();
        operator = new SourceReaderOperator<>(source, VERTEX_ID);
        operator.setSubtaskIdentity(0, 1);

        OperatorSnapshotResult snapshot = new OperatorSnapshotResult();
        snapshot.putOperatorState(SourceReaderOperator.READER_SPLITS_KEY,
                List.of(new SimpleSplit("split-run")));

        operator.restoreState(snapshot);
        operator.open();
        operator.run();

        assertEquals(List.of("split-run"), source.reader.restoredSplits,
                "cursors survive restore→open→run (P1-6.7-008)");
        assertTrue(source.reader.started, "reader was started and driven by run()");
        operator.finish();
    }

    // ---------------- 测试桩 ----------------

    static final class SimpleSplit implements SourceSplit {
        private static final long serialVersionUID = 1L;
        private final String splitId;

        SimpleSplit(String splitId) {
            this.splitId = splitId;
        }

        @Override
        public String splitId() {
            return splitId;
        }
    }

    /**
     * 记录型 reader：restoreState 记录位点与相对 start 的先后；
     * isFinished() 恒 true（bounded 空源）——修复被 stash 时 run() 立即退出、
     * 位点断言快败，不挂死。
     */
    static final class RecordingReader implements SourceReader<String, SimpleSplit> {
        private static final long serialVersionUID = 1L;

        final List<String> restoredSplits = Collections.synchronizedList(new ArrayList<>());
        final List<String> addedSplits = Collections.synchronizedList(new ArrayList<>());
        volatile boolean started = false;
        volatile boolean restoredBeforeStart = false;

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void addSplits(List<SimpleSplit> splits) {
            synchronized (addedSplits) {
                splits.forEach(s -> addedSplits.add(s.splitId()));
            }
        }

        @Override
        public Optional<String> pollNext() {
            return Optional.empty();
        }

        @Override
        public List<SimpleSplit> snapshotState(long checkpointId) {
            return Collections.emptyList();
        }

        @Override
        public void restoreState(List<SimpleSplit> splits) {
            // 替换语义（对齐 FileSourceReader）：清空后重填
            restoredSplits.clear();
            synchronized (restoredSplits) {
                splits.forEach(s -> restoredSplits.add(s.splitId()));
            }
            restoredBeforeStart = !started;
        }

        @Override
        public void close() {
        }

        @Override
        public boolean isFinished() {
            return true;
        }
    }

    static final class RecordingEnumerator implements SplitEnumerator<SimpleSplit, String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void start(SplitEnumeratorContext<SimpleSplit> context) {
            // coordinator boot（rebuild 场景）会以 null/空 context 调用——不依赖
        }

        @Override
        public void addReader(int subtaskIndex) {
        }

        @Override
        public void handleSplitRequest(int subtaskIndex, Optional<Throwable> reason) {
        }

        @Override
        public String snapshotState(long checkpointId) {
            return "";
        }

        @Override
        public void restoreState(String state) {
        }

        @Override
        public void close() {
        }
    }

    static final class RecordingSource implements Source<String, SimpleSplit, String> {
        private static final long serialVersionUID = 1L;

        final RecordingReader reader = new RecordingReader();

        @Override
        public SplitEnumerator<SimpleSplit, String> createEnumerator() {
            return new RecordingEnumerator();
        }

        @Override
        public SplitEnumerator<SimpleSplit, String> restoreEnumerator(String checkpointState) {
            return new RecordingEnumerator();
        }

        @Override
        public SourceReader<String, SimpleSplit> createReader(SourceReaderContext readerContext) {
            return reader;
        }

        @Override
        public SimpleVersionedSerializer<String> getEnumeratorStateSerializer() {
            return new StringSerializer();
        }

        @Override
        public SimpleVersionedSerializer<SimpleSplit> getSplitSerializer() {
            return new SimpleSplitSerializer();
        }

        @Override
        public Boundedness getBoundedness() {
            return Boundedness.BOUNDED;
        }
    }

    static final class StringSerializer implements SimpleVersionedSerializer<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public byte[] serialize(String obj) {
            return obj.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public String deserialize(int version, byte[] bytes) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public int getVersion() {
            return 1;
        }
    }

    static final class SimpleSplitSerializer implements SimpleVersionedSerializer<SimpleSplit> {
        private static final long serialVersionUID = 1L;

        @Override
        public byte[] serialize(SimpleSplit obj) {
            return obj.splitId().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public SimpleSplit deserialize(int version, byte[] bytes) {
            return new SimpleSplit(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public int getVersion() {
            return 1;
        }
    }
}
