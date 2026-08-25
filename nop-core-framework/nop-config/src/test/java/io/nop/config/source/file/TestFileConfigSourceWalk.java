/**
 * AbstractFileConfigSource 目录遍历回归测试：
 * 修复前 Files.walk 返回的目录流在 flatMap 中从不关闭（JDK 要求显式 close），
 * 定时刷新周期性泄漏目录句柄；修复后 try-with-resources 收集关闭。
 * 本测试锁定遍历行为（嵌套目录文件全部被发现、排序稳定）不被重构破坏。
 * 流关闭本身无跨平台可行为观测手段（macOS 不锁目录），以结构化修复保证。
 */
package io.nop.config.source.file;

import io.nop.commons.util.objects.ValueWithLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class TestFileConfigSourceWalk {

    static class RecordingFileConfigSource extends AbstractFileConfigSource {
        List<Path> recordedPaths;

        RecordingFileConfigSource(Path dir) {
            super(List.of(dir.toString()), 0L);
        }

        @Override
        public String getName() {
            return "recording";
        }

        @Override
        protected Map<String, ValueWithLocation> loadConfigFromPath(List<Path> paths) {
            this.recordedPaths = new ArrayList<>(paths);
            return new HashMap<>();
        }
    }

    @Test
    public void testNestedDirectoriesWalked() throws IOException {
        Path dir = java.nio.file.Paths.get("target", "config-walk-test-" + System.nanoTime());
        try {
            Path sub1 = Files.createDirectories(dir.resolve("sub1"));
            Path deep = Files.createDirectories(dir.resolve("sub2/deep"));
            Path top = dir.resolve("top.properties");
            Path a = sub1.resolve("a.properties");
            Path b = deep.resolve("b.properties");
            Files.writeString(top, "k=top");
            Files.writeString(a, "k=a");
            Files.writeString(b, "k=b");

            // 构造期即完成一次 loadConfig（refreshInterval=0 无定时器）
            RecordingFileConfigSource source = assertDoesNotThrow(
                    () -> new RecordingFileConfigSource(dir),
                    "目录遍历装载不得抛异常");

            Set<Path> discovered = new HashSet<>(source.recordedPaths);
            assertEquals(Set.of(top, a, b), discovered, "嵌套目录下的文件必须全部被发现（含深层子目录）");

            // sorted(Path::compareTo)：路径有序
            assertEquals(source.recordedPaths.stream().sorted()
                    .collect(java.util.stream.Collectors.toList()), source.recordedPaths,
                    "发现的文件按路径排序");
        } finally {
            deleteRecursively(dir);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path))
            return;
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    // best-effort 清理测试目录
                }
            });
        }
    }
}
