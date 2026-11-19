package io.nop.rg.core.io;

import io.nop.rg.core.NopRgException;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * 单次内存映射只读文件读取器（FFM API，plan 2263 IO-01）。
 *
 * <p>try-with-resources 确定性释放：{@link #close()} 关闭 Arena 即解除映射。
 * 关闭后访问 segment 抛 IllegalStateException。
 *
 * <p>空文件契约（plan 2273 A7）：空文件（size=0）不做映射，{@link #getSegment()} 返回 null，
 * 调用方须以 {@link #getSize()} 判空后再使用 segment。
 *
 * <p>外部截断边界声明（plan 2564 W-03，声明边界而非承诺消除）：映射存活期内文件被外部
 * 截断/变短的行为<b>未定义</b>——越新 EOF 的段访问可能触发 fault（POSIX SIGBUS，JVM 实测
 * 转换为 {@link InternalError} 形态）或（如 macOS UBS）静默续供旧页。调用方（SearchCoordinator
 * 任务边界）负责将该形态转换为携带文件相对路径的 NopRgException（"search fault while
 * scanning: &lt;rel&gt;"）；TOCTOU 窗口本体只读、无数据损坏面，不修（设计裁定 B 限缩）。
 */
public final class MappedFileReader implements AutoCloseable {
    private final FileChannel channel;
    private final Arena arena;
    private final MemorySegment segment;
    private final long size;
    private boolean closed;

    public MappedFileReader(Path file) {
        try {
            this.channel = FileChannel.open(file, StandardOpenOption.READ);
            this.arena = Arena.ofConfined();
            boolean ok = false;
            try {
                this.size = channel.size();
                this.segment = size == 0 ? null : channel.map(FileChannel.MapMode.READ_ONLY, 0, size, arena);
                ok = true;
            } finally {
                if (!ok) {
                    closeChannelAndArena();
                }
            }
            // Wave 5 item 17：madvise(MADV_SEQUENTIAL|MADV_WILLNEED) 顺序预读提示（对齐 rg mmap.rs:89
            // 的 madvise(SEQUENTIAL)；best-effort，任何失败静默忽略——Windows 上 madvise 不存在）
            if (segment != null) {
                adviseSequential(segment.address(), size);
            }
        } catch (IOException e) {
            throw new NopRgException("map file failed: " + file, e);
        }
    }

    public long getSize() {
        return size;
    }

    /**
     * @return 只读映射段；空文件（size=0）返回 null（见类 javadoc 空文件契约），关闭后访问抛
     *         IllegalStateException
     */
    public MemorySegment getSegment() {
        return segment;
    }

    @Override
    public void close() {
        closeChannelAndArena();
    }

    private void closeChannelAndArena() {
        if (closed) {
            return;
        }
        closed = true;
        arena.close();
        try {
            channel.close();
        } catch (IOException e) {
            throw new NopRgException("close file channel failed", e);
        }
    }

    /**
     * Wave 5 item 17（roadmap，2026-11-19）：best-effort madvise。POSIX 平台对映射段施加
     * MADV_SEQUENTIAL（顺序访问预测，对齐 rg mmap.rs:89）；符号缺失/参数错误/
     * 平台不支持时静默返回——advice 只是性能提示，语义不受影响。
     */
    private static void adviseSequential(long address, long size) {
        // macOS 跳过（对齐 rg mmap.rs:73-79 的裁定：macOS VM 锁与并发 map/unmap 竞争下
        // madvise 实测引发挂死；rg 同因在 macOS 禁用 mmap）。仅 Linux 启用。
        if (!System.getProperty("os.name", "").equals("Linux")) {
            return;
        }
        // MADV_SEQUENTIAL = 2（<sys/mman.h>）。句柄与参数段静态缓存（ADVISE_HANDLE/ADVISE_ARG），
        // 避免每文件新建 Arena 的 native 分配开销与并发竞争（roadmap item 17 实测教训）。
        try {
            if (ADVISE_HANDLE == null) {
                return;
            }
            long base = pageAligned(address);
            ADVISE_HANDLE.invoke(base, size + (address - base), 2);
        } catch (Throwable ignored) {
            // best-effort：无 madvise（Windows）/downcall 失败/权限不足等一律静默
        }
    }

    private static long pageAligned(long address) {
        return address & ~(PAGE_SIZE - 1); // 地址须页对齐（mmap 基址本对齐，防御性处理）
    }

    private static final long PAGE_SIZE = detectPageSize();
    private static final MethodHandle ADVISE_HANDLE = initAdviseHandle();

    private static long detectPageSize() {
        try {
            Optional<MemorySegment> sysconf = Linker.nativeLinker().defaultLookup().find("sysconf");
            if (sysconf.isPresent()) {
                MethodHandle sc = Linker.nativeLinker().downcallHandle(sysconf.get(),
                        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
                // _SC_PAGESIZE = 29（POSIX：Linux 与 macOS 数值相同；Windows 无 sysconf，走默认）
                long v = (long) sc.invoke(29);
                if (v > 0) {
                    return v;
                }
            }
        } catch (Throwable ignored) {
            // fall through to default
        }
        return 4096;
    }

    private static MethodHandle initAdviseHandle() {
        try {
            Optional<MemorySegment> symbol = Linker.nativeLinker().defaultLookup().find("madvise");
            if (symbol.isEmpty()) {
                return null;
            }
            // 地址参数以 JAVA_LONG 传递（指针宽度寄存器直传，避免地址参数段分配）
            return Linker.nativeLinker().downcallHandle(symbol.get(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
                            ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
        } catch (Throwable ignored) {
            return null;
        }
    }
}
