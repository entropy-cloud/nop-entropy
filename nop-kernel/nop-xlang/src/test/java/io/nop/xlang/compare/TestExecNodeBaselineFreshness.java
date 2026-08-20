package io.nop.xlang.compare;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 矩阵基线新鲜度测试（I3 Phase 1 落地）：live 目录扫描 vs {@link ExecNodeBaseline} 四分区。
 *
 * <p>红灯机制：exec/ 包新增文件（前端演进的未注册节点类）若未同步登记分区，本测试 FAIL——
 * 红灯注入对照见 {@link #testRedLightInjectionClassifierRejectsUnknownClass()}（未知类名判
 * 未归属 = 新鲜度 FAIL 路径；已登记类名判绿）。
 */
public class TestExecNodeBaselineFreshness {

    private static final String EXEC_SOURCE_DIR = "src/main/java/io/nop/xlang/exec";

    private static Set<String> scanLiveExecFiles() {
        File dir = new File(EXEC_SOURCE_DIR);
        assertTrue(dir.isDirectory(), "exec source dir not found (must run from nop-xlang module): " + dir);
        return Arrays.stream(dir.listFiles((d, name) -> name.endsWith(".java")))
                .map(f -> f.getName().substring(0, f.getName().length() - ".java".length()))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    public void testLiveScanMatchesDeclaredBaseline() {
        Set<String> live = scanLiveExecFiles();
        Set<String> declared = new TreeSet<>(ExecNodeBaseline.allPartitions().keySet());
        assertEquals(new TreeSet<>(), new TreeSet<>(Sets.difference(live, declared)),
                "unassigned live exec/ files (register them in ExecNodeBaseline partitions)");
        assertEquals(new TreeSet<>(), new TreeSet<>(Sets.difference(declared, live)),
                "declared baseline entries without live file (stale partition entry)");
    }

    @Test
    public void testPartitionsAreDisjoint() {
        Set<String> seen = new TreeSet<>();
        for (String name : ExecNodeBaseline.aFamily()) {
            assertTrue(seen.add(name), "duplicate across partitions: " + name);
        }
        for (String name : ExecNodeBaseline.RESIDUAL_MERGED) {
            assertTrue(seen.add(name), "duplicate across partitions: " + name);
        }
        for (String name : ExecNodeBaseline.I2_SUBSET) {
            assertTrue(seen.add(name), "duplicate across partitions: " + name);
        }
        for (String name : ExecNodeBaseline.bFamily()) {
            assertTrue(seen.add(name), "duplicate across partitions: " + name);
        }
        for (String name : ExecNodeBaseline.excluded()) {
            assertTrue(seen.add(name), "duplicate across partitions: " + name);
        }
    }

    @Test
    public void testExcludedEntriesAllHaveReasons() {
        for (String name : ExecNodeBaseline.excluded()) {
            String reason = ExecNodeBaseline.EXCLUDED_REASONS.get(name);
            assertTrue(reason != null && !reason.isBlank(), "excluded entry missing per-class reason: " + name);
        }
        assertEquals(ExecNodeBaseline.excluded(),
                ExecNodeBaseline.EXCLUDED_REASONS.keySet(), "reason map must cover exactly the excluded set");
    }

    /**
     * 红灯注入对照（红/绿可控）：未登记类名 → isClassified=false（新鲜度测试将 FAIL）；
     * 已登记类名 → isClassified=true（绿）。
     */
    @Test
    public void testRedLightInjectionClassifierRejectsUnknownClass() {
        assertFalse(ExecNodeBaseline.isClassified("SomeFutureExecutable"), "unknown class must be unclassified (red)");
        assertTrue(ExecNodeBaseline.isClassified("LiteralExecutable"), "registered class must be classified (green)");
        assertTrue(ExecNodeBaseline.isClassified("IfExecutable"), "B-family class must be classified pending (green)");
    }

    /** 最小集合差工具（避免为断言引入额外依赖）。 */
    static final class Sets {
        static Set<String> difference(Set<String> a, Set<String> b) {
            Set<String> ret = new LinkedHashSet<>(a);
            ret.removeAll(b);
            return ret;
        }
    }
}
