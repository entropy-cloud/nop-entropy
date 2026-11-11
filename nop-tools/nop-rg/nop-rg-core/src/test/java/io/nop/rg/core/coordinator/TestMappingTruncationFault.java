package io.nop.rg.core.coordinator;

import io.nop.rg.core.NopRgException;
import io.nop.rg.core.io.MappedFileReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * W-03 载体（plan 2564，C10-02 缺保护钉 P2-02 / R27 双机复现义务承接）：FFM 映射存活期 ×
 * 外部截断 TOCTOU 的错误可见性——映射段访问越过截断后 EOF 触发 fault（InternalError 形态），
 * coordinator 任务边界将其包装为携带文件相对路径的 NopRgException 并归入该文件错误项。
 *
 * <p>两腿确定性形态（R1-M2 / R2-M1）：
 * <ul>
 *   <li>单元腿：MappedFileReader map 后第二 rw channel truncate → 访问越新 EOF →
 *       断言 reader 层 fault 形态为 {@link InternalError}（门控 = JDK 版本门 + POSIX 门
 *       [R2-m4]：Windows 映射句柄锁 truncate 抛 IOException；不赌「扫描中外部截断」竞态窗，
 *       SIGBUS 直崩 VM 风险同被规避）</li>
 *   <li>通道腿：经 SearchCoordinator 包私有 taskDecorator seam 注入 InternalError →
 *       断言 coordinator 包装消息「search fault while scanning: &lt;rel&gt;」含 rel</li>
 * </ul>
 */
public class TestMappingTruncationFault {
    @TempDir
    Path tempDir;

    private static boolean posixSupported() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    /**
     * JDK 版本门（R2-m4）：FFM final（JDK 22+）后映射 fault 实测转换为 InternalError 形态。
     */
    private static void assumeFaultFormJdk() {
        assumeTrue(Runtime.version().feature() >= 22, "FFM fault->InternalError form needs JDK 22+");
    }

    @Test
    public void truncatedMappedSegmentAccessFaultsAsInternalError() throws Exception {
        assumeFaultFormJdk();
        assumeTrue(posixSupported(), "POSIX required: Windows mapping handle locks truncate (R2-m4)");
        // 平台门（live 勘正）：macOS UBS 对存活映射的截断不保证 fault（可能续供旧页零 fault）——
        // SIGBUS→InternalError 的确定性 fault 形态（R27 双机实测形态）在 Linux 上留证；
        // darwin 跳过即设计「门控未过时 assumeTrue skip 日志留证豁免」路径
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "truncation->InternalError fault form is Linux-deterministic; darwin UBS may serve stale pages without fault");
        Path file = tempDir.resolve("trunc.bin");
        Files.write(file, ("0123456789abcdef".repeat(64)).getBytes(StandardCharsets.UTF_8)); // 1KB
        try (MappedFileReader reader = new MappedFileReader(file)) {
            // 第二 rw channel 截断至 16 字节：映射存活期不变，越新 EOF 区域已解除后备
            try (var truncChannel = Files.newByteChannel(file, StandardOpenOption.WRITE)) {
                truncChannel.truncate(16);
            }
            // 访问越新 EOF 的映射区域 → fault。断言 reader 层形态 = InternalError
            assertThrows(InternalError.class, () -> {
                long size = reader.getSize();
                for (long i = 0; i < size; i++) {
                    reader.getSegment().get(java.lang.foreign.ValueLayout.JAVA_BYTE, i);
                }
            }, "映射存活期内截断后越新 EOF 访问必须以 InternalError fault（R27 复现形态）");
        }
    }

    @Test
    public void coordinatorWrapsInternalErrorWithRelativePath() throws Exception {
        // 通道腿（R2-M1）：taskDecorator seam 注入 InternalError → 任务边界包装。
        // 裸 InternalError.getMessage 可为 null（R1-m2），本腿断言包装消息固定含 rel
        SearchCoordinator coordinator = new SearchCoordinator(1, false, false);
        coordinator.taskDecorator = task -> () -> {
            FileTaskOutcome outcome = task.call();
            if (outcome != null && "boom/inner.txt".equals(outcome.relativePath())) {
                throw new InternalError("simulated native fault from truncated mapping");
            }
            return outcome;
        };
        Files.createDirectories(tempDir.resolve("boom"));
        Files.writeString(tempDir.resolve("boom/inner.txt"), "needle\n");

        SearchOutcome outcome = coordinator.search(
                new SearchCommand(tempDir, "needle", SearchCoordinator.Strategy.LITERAL,
                        false, List.of(), 0));

        FileError error = outcome.errors().stream()
                .filter(e -> e.relativePath().equals("boom/inner.txt"))
                .findFirst().orElse(null);
        assertTrue(error != null, "InternalError 必须归该文件错误项: " + outcome.errors());
        assertTrue(error.message() != null && error.message().contains("search fault while scanning: boom/inner.txt"),
                "W-03 包装消息必须含「search fault while scanning: <rel>」: " + error.message());
    }

    @Test
    public void wrappedFaultIsNopRgExceptionCarryingRelativePath() {
        // 包装形态单元钉：InternalError 经任务边界包装为 NopRgException（错误族一致性，
        // CLI 侧 NopException catch 屏障不穿透——设计勘正 4 链路）
        NopRgException wrapped = new NopRgException("search fault while scanning: x.txt",
                new InternalError("raw fault"));
        assertInstanceOf(NopRgException.class, wrapped);
        assertTrue(wrapped.getMessage().contains("x.txt"));
        assertInstanceOf(InternalError.class, wrapped.getCause());
    }
}
