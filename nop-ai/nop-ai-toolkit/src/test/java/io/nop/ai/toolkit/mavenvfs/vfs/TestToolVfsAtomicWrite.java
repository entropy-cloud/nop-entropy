package io.nop.ai.toolkit.mavenvfs.vfs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.2-C1-005（plan 2557 裁定 C，行为钉）：mavenvfs 两站点原子写——
 * {@code newOutputStream}（OutputStream 契约：写 temp，close→flush+move，
 * 失败→删 temp）与 {@code copyToVirtual}（temp + ATOMIC_MOVE，降级
 * REPLACE_EXISTING）。失败保旧 + 并发读写窗口不出现半写态。
 */
public class TestToolVfsAtomicWrite {

    private File baseDir;
    private File deltaDir;
    private DeltaVirtualFileSystem vfs;

    @BeforeEach
    public void setUp() throws IOException {
        baseDir = Files.createTempDirectory("vfs-atomic-base-").toFile();
        deltaDir = Files.createTempDirectory("vfs-atomic-delta-").toFile();
        vfs = new DeltaVirtualFileSystem(baseDir, deltaDir);
    }

    @AfterEach
    public void tearDown() throws IOException {
        deleteRecursively(baseDir.toPath());
        deleteRecursively(deltaDir.toPath());
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 清理临时目录 best-effort，删除失败不影响断言主路径
                }
            });
        }
    }

    private static String read(File file) throws IOException {
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    private static void write(File file, String content) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    }

    private long tempCount() {
        File[] files = deltaDir.listFiles((dir, name) -> name.contains(".tmp"));
        return files == null ? 0 : Arrays.stream(files).filter(File::isFile).count();
    }

    @Test
    public void outputStreamReplacesTargetOnlyOnClose() throws IOException {
        File target = new File(deltaDir, "x/a.txt");
        write(target, "old-content");

        OutputStream out = vfs.getOutputStream("x/a.txt");
        out.write("new-content".getBytes(StandardCharsets.UTF_8));

        // 并发读写面：close 之前读到的仍是旧内容（无半写态）
        assertEquals("old-content", read(target), "pre-close target keeps old content (crash-safe window)");

        out.close();
        assertEquals("new-content", read(target), "close flushes+moves the new content in");
        assertEquals(0, tempCount(), "no temp file lingers after successful close");
    }

    @Test
    public void abandonedStreamKeepsOldContent() throws IOException {
        // 崩溃窗口面：拿到流写完但未 close（等价于写中途失败/进程消亡）→ 目标保旧
        File target = new File(deltaDir, "y/b.txt");
        write(target, "old-content");

        OutputStream out = vfs.getOutputStream("y/b.txt");
        out.write("new-content".getBytes(StandardCharsets.UTF_8));
        // 故意不 close（abandon）——模拟 crash

        assertEquals("old-content", read(target), "abandoned write must not clobber old content");
    }

    @Test
    public void closeFailureCleansTempAndPropagates() throws IOException {
        File target = new File(deltaDir, "z/c.txt");
        write(target, "old-content");

        OutputStream out = vfs.getOutputStream("z/c.txt");
        out.write("new-content".getBytes(StandardCharsets.UTF_8));
        // 失败注入：把目标路径换成非空目录 → close 的 move（ATOMIC_MOVE 及
        // REPLACE_EXISTING 降级）必然失败
        Files.deleteIfExists(target.toPath());
        Files.createDirectories(target.toPath());
        Files.writeString(target.toPath().resolve("blocker"), "x", StandardCharsets.UTF_8);

        assertThrows(IOException.class, out::close, "close surfaces the move failure");
        // 目标保持目录形态（半写内容从未落进目标），temp 已清理
        assertTrue(Files.isDirectory(target.toPath()));
        assertEquals(0, tempCount(), "temp cleaned up on failure");
    }

    @Test
    public void doubleCloseIsIdempotent() throws IOException {
        File target = new File(deltaDir, "w/d.txt");
        OutputStream out = vfs.getOutputStream("w/d.txt");
        out.write("data".getBytes(StandardCharsets.UTF_8));
        out.close();
        out.close(); // 第二次 close 不应抛/不应再 move
        assertEquals("data", read(target));
        assertEquals(0, tempCount());
    }

    @Test
    public void copyToVirtualReplacesTargetAtomically() throws IOException {
        File target = new File(deltaDir, "copy/e.txt");
        write(target, "old-content");
        File source = new File(baseDir, "source.txt");
        write(source, "copied-content");

        vfs.copyToVirtual(source, "copy/e.txt");

        assertEquals("copied-content", read(target));
        assertEquals(0, tempCount(), "no temp file lingers after copyToVirtual");
    }

    @Test
    public void copyToVirtualFailureKeepsOldTarget() throws IOException {
        File target = new File(deltaDir, "copyfail/f.txt");
        write(target, "old-content");

        // 失败注入：source 是目录 → Files.copy(dir, tempFile) 失败（保旧目标）
        File dirSource = new File(baseDir, "dirsource");
        Files.createDirectories(dirSource.toPath());

        assertThrows(Exception.class, () -> vfs.copyToVirtual(dirSource, "copyfail/f.txt"));
        assertEquals("old-content", read(target), "failed copy keeps old target content");
        assertEquals(0, tempCount(), "no temp file lingers after failed copy");
    }
}
