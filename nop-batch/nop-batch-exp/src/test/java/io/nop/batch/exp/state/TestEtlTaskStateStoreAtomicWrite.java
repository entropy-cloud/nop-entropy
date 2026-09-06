package io.nop.batch.exp.state;

import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.impl.FileResource;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 状态文件的写入必须是"临时文件+原子rename"：进程中途被kill不留下截断的JSON；
 * EtlTableStateStore.isCompleted()与后台saveTableState并发时必须在锁内访问共享HashMap。
 */
public class TestEtlTaskStateStoreAtomicWrite {

    private File newStateFile(String name) {
        File dir = new File("target/etl-state-test");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File file = new File(dir, name);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        return file;
    }

    @Test
    public void testSaveTableStateWritesCompleteJsonWithoutTempLeftover() {
        File stateFile = newStateFile("state-atomic.json");
        EtlTaskStateStore store = new EtlTaskStateStore(stateFile);

        IBatchTaskContext ctx = new BatchTaskContextImpl();
        store.getTableStore("t1").saveTaskState(true, null, ctx);

        assertTrue(store.isTableCompleted("t1"));

        // 落盘文件必须是完整可解析的JSON，断点重启后可恢复
        EtlTaskState parsed = JsonTool.parseBeanFromResource(new FileResource(stateFile), EtlTaskState.class);
        assertNotNull(parsed);
        assertTrue(parsed.makeTableState("t1").isCompleted());

        // 临时文件在rename后不能残留
        File tmp = new File(stateFile.getParentFile(), stateFile.getName() + ".tmp");
        assertFalse(tmp.exists(), "temp state file must be renamed away, not left behind");
    }

    /**
     * ImportDbTool/ExportDbTool threadCount>1时主线程的isTableCompleted/isCompleted
     * 与后台表任务的saveTableState并发。isCompleted必须在store锁内执行，
     * 否则HashMap并发computeIfAbsent+put会抛ConcurrentModificationException甚至结构损坏。
     */
    @Test
    public void testConcurrentIsCompletedAndSaveDoNotCorruptState() throws Exception {
        int rounds = 30;
        int tables = 24;
        for (int round = 0; round < rounds; round++) {
            File stateFile = newStateFile("state-stress-" + round + ".json");
            EtlTaskStateStore store = new EtlTaskStateStore(stateFile);

            AtomicReference<Throwable> error = new AtomicReference<>();
            AtomicInteger readCount = new AtomicInteger();

            Thread writer = new Thread(() -> {
                try {
                    IBatchTaskContext ctx = new BatchTaskContextImpl();
                    for (int i = 0; i < tables; i++) {
                        store.getTableStore("t" + i).saveTaskState(true, null, ctx);
                    }
                } catch (Throwable e) {
                    error.compareAndSet(null, e);
                }
            }, "etl-writer");

            Thread reader = new Thread(() -> {
                try {
                    int i = 0;
                    while (writer.isAlive()) {
                        String table = "t" + (i++ % tables);
                        store.isTableCompleted(table);
                        ((EtlTaskStateStore.EtlTableStateStore) store.getTableStore(table)).isCompleted();
                        readCount.incrementAndGet();
                    }
                } catch (Throwable e) {
                    error.compareAndSet(null, e);
                }
            }, "etl-reader");

            writer.start();
            reader.start();
            writer.join(30000);
            reader.join(30000);
            assertFalse(writer.isAlive());
            assertFalse(reader.isAlive());
            assertNull(error.get(),
                    "concurrent read/save must not throw or corrupt HashMap: round=" + round);
            assertTrue(readCount.get() > 0);
        }
    }
}
