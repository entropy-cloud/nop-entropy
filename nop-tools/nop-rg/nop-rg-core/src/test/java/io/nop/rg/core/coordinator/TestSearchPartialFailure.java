package io.nop.rg.core.coordinator;

import io.nop.rg.core.NopRgException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * W-05 部分成功语义载体（plan 2564，C10-03 缺保护钉 P2-03）：
 * 单文件失败不弃全——其余文件结果照常返回，失败文件归入错误项且错误项含文件路径。
 *
 * <p>腿清单（设计载体表 §二）：
 * <ul>
 *   <li>chmod 000 单文件腿（确定性 stat/map 失败；assumeTrue 非 root + POSIX——root 下
 *       chmod 000 不产生 PermissionDenied，R1-m3）</li>
 *   <li>IOOBE 变体归错误项腿（失效面 8 形态：经 taskDecorator seam 确定性注入
 *       IndexOutOfBoundsException——消息无路径，FileError 以 rel 补齐）</li>
 *   <li>walker fail-fast 穿透腿（失效面 6 冻结豁免：walk 阶段已声明契约维持
 *       coordinator 级失败，不转错误项）</li>
 * </ul>
 */
public class TestSearchPartialFailure {
    @TempDir
    Path tempDir;

    private static boolean isRoot() {
        return "root".equals(System.getProperty("user.name"));
    }

    private static boolean posixSupported() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    private void writeNeedle(String rel) throws Exception {
        Files.writeString(tempDir.resolve(rel), "needle one\nplain\nneedle two\n");
    }

    @Test
    public void chmodZeroFileFailsIntoErrorItemWhileOthersReturn() throws Exception {
        assumeTrue(!isRoot(), "chmod 000 does not deny root (R1-m3)");
        assumeTrue(posixSupported(), "POSIX permissions unavailable");
        writeNeedle("good.txt");
        writeNeedle("locked.txt");
        Files.setPosixFilePermissions(tempDir.resolve("locked.txt"),
                PosixFilePermissions.fromString("---------"));
        // 确认权限确实生效（root 之外的文件系统上 chmod 可能被忽略）
        Set<PosixFilePermission> perms =
                Files.getPosixFilePermissions(tempDir.resolve("locked.txt"));
        assumeTrue(perms.isEmpty(), "chmod 000 not effective on this filesystem");

        SearchCoordinator coordinator = new SearchCoordinator(2, false, false);
        SearchOutcome outcome = coordinator.search(
                new SearchCommand(tempDir, "needle", SearchCoordinator.Strategy.LITERAL,
                        false, List.of(), 0));

        assertTrue(outcome.errors().stream()
                        .anyMatch(e -> e.relativePath().equals("locked.txt")),
                "失败文件必须归错误项且含相对路径: " + outcome.errors());
        assertTrue(outcome.results().containsKey("good.txt"),
                "其余文件结果必须照常返回（首错不弃全）: " + outcome.results());
        assertEquals(2, outcome.results().get("good.txt").lineCount());
    }

    @Test
    public void indexOutOfBoundsVariantIsConvertedToErrorItemWithRelativePath() throws Exception {
        // 失效面 8 形态（陈旧 spans 二次映射确定性 IOOBE）：异常消息无文件路径，
        // FileError 以 rel 补齐；注入经包私有 taskDecorator seam（R2-M1）确定性触发
        writeNeedle("good.txt");
        writeNeedle("boom.txt");
        SearchCoordinator coordinator = new SearchCoordinator(1, false, false);
        coordinator.taskDecorator = task -> () -> {
            FileTaskOutcome outcome = task.call();
            if (outcome != null && "boom.txt".equals(outcome.relativePath())) {
                throw new IndexOutOfBoundsException("Index 1024 out of bounds for length 512");
            }
            return outcome;
        };

        SearchOutcome outcome = coordinator.search(
                new SearchCommand(tempDir, "needle", SearchCoordinator.Strategy.LITERAL,
                        false, List.of(), 0));

        assertTrue(outcome.errors().stream()
                        .anyMatch(e -> e.relativePath().equals("boom.txt")),
                "IOOBE 必须转为该文件错误项且以 rel 补齐路径: " + outcome.errors());
        assertTrue(outcome.results().containsKey("good.txt"),
                "IOOBE 不得弃全——其余文件结果照常: " + outcome.results());
    }

    @Test
    public void walkerFailFastStaysCoordinatorLevelFailure() throws Exception {
        // 失效面 6 冻结豁免：ParallelFileWalker walk 阶段 fail-fast 是已声明契约
        // （javadoc :31-32），异常保持 coordinator 级失败，不转错误项
        assumeTrue(!isRoot(), "chmod 000 does not deny root");
        assumeTrue(posixSupported(), "POSIX permissions unavailable");
        writeNeedle("good.txt");
        Path lockedDir = Files.createDirectories(tempDir.resolve("locked-dir"));
        Files.writeString(lockedDir.resolve("x.txt"), "needle");
        Files.setPosixFilePermissions(lockedDir, PosixFilePermissions.fromString("---------"));
        assumeTrue(Files.getPosixFilePermissions(lockedDir).isEmpty(), "chmod not effective");

        SearchCoordinator coordinator = new SearchCoordinator(1, false, false);
        assertThrows(NopRgException.class, () -> coordinator.search(
                new SearchCommand(tempDir, "needle", SearchCoordinator.Strategy.LITERAL,
                        false, List.of(), 0)));
    }

    @Test
    public void executionExceptionFallbackLegAggregatesInsteadOfAborting() throws Exception {
        // 收集循环兜底腿（R1-m1）：任务边界 catch（InternalError|RuntimeException）之外仍逃逸的
        // 异常（decorator 层 AssertionError 等非 InternalError 的 Error 形态）不再中断收集——
        // 经 ExecutionException → 兜底 FileError 聚合，已完成文件结果保留
        writeNeedle("good.txt");
        writeNeedle("boom.txt");
        AtomicInteger outerThrows = new AtomicInteger();
        SearchCoordinator coordinator = new SearchCoordinator(1, false, false);
        coordinator.taskDecorator = task -> () -> {
            FileTaskOutcome outcome = task.call();
            if (outcome != null && "boom.txt".equals(outcome.relativePath())) {
                outerThrows.incrementAndGet();
                throw new AssertionError("simulated outer-layer fault: boom.txt");
            }
            return outcome;
        };

        SearchOutcome outcome = coordinator.search(
                new SearchCommand(tempDir, "needle", SearchCoordinator.Strategy.LITERAL,
                        false, List.of(), 0));

        assertEquals(1, outerThrows.get());
        // ForkJoin 反射重建异常会丢消息——兜底上下文以异常类型承载（rel 不可得时置空串）
        assertTrue(outcome.errors().stream().anyMatch(e -> e.message() != null
                        && e.message().contains("AssertionError")),
                "兜底腿必须聚合错误而非弃全: " + outcome.errors());
        assertTrue(outcome.results().containsKey("good.txt"));
        assertFalse(outcome.errors().isEmpty());
    }
}
