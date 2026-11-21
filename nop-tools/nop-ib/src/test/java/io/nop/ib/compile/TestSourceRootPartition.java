package io.nop.ib.compile;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主/测试源根分区单元测试（影子类缺陷回归钉，plan 2575 follow-up）。
 *
 * <p>缺陷形态：混合集合曾整集合走 testCompile=true（{@code -d target/test-classes}），
 * 同一变更同时改主源+测试源时主源字节码以影子类形式写入 test-classes，被测试 fork 的
 * test-classes-first classpath 加载，遮蔽 target/classes 正确产物（实测"改了不生效"
 * 与变异探针假绿）。修复后混合集合必须拆成两路（main → test 顺序）。
 */
public class TestSourceRootPartition {

    @Test
    public void testMixedSetSplitsMainFirst() {
        IncrementalCompileExecutor.SourceSplit split = IncrementalCompileExecutor.splitBySourceRoot(List.of(
                "mod-a/src/main/java/io/x/Foo.java",
                "mod-a/src/test/java/io/x/FooTest.java",
                "mod-b/src/main/java/io/y/Bar.java",
                "mod-b/src/test/java/io/y/BarTest.java"));
        assertTrue(split.hasBoth());
        // 顺序保持稳定（main 全在前、test 全在后），供"先主后测试"两路编译
        assertEquals(List.of("mod-a/src/main/java/io/x/Foo.java", "mod-b/src/main/java/io/y/Bar.java"),
                split.mainSources());
        assertEquals(List.of("mod-a/src/test/java/io/x/FooTest.java", "mod-b/src/test/java/io/y/BarTest.java"),
                split.testSources());
    }

    @Test
    public void testPureMainSetDoesNotSplit() {
        IncrementalCompileExecutor.SourceSplit split = IncrementalCompileExecutor.splitBySourceRoot(List.of(
                "mod-a/src/main/java/io/x/Foo.java"));
        assertFalse(split.hasBoth());
        assertFalse(split.hasTest());
        assertEquals(1, split.mainSources().size());
    }

    @Test
    public void testPureTestSet() {
        IncrementalCompileExecutor.SourceSplit split = IncrementalCompileExecutor.splitBySourceRoot(List.of(
                "mod-a/src/test/java/io/x/FooTest.java"));
        assertFalse(split.hasBoth());
        assertTrue(split.hasTest());
        assertTrue(split.mainSources().isEmpty());
    }

    @Test
    public void testNonStandardLayoutCountsAsMain() {
        // 非标准布局（无 /src/main 或 /src/test 前缀）视为主源——与缺陷前的
        // anyMatch("/src/test/") 判定语义一致，只修正混合集合的错误归并
        IncrementalCompileExecutor.SourceSplit split = IncrementalCompileExecutor.splitBySourceRoot(List.of(
                "mod-a/src/java/io/x/Foo.java"));
        assertFalse(split.hasTest());
        assertEquals(1, split.mainSources().size());
    }

    /**
     * Repo-root module (empty basedir, single-module project): sources are
     * repo-relative WITHOUT a leading slash — "src/test/..." must still route
     * to the test pass. Live-observed defect: CalcTest.java was compiled by the
     * MAIN pass with the main classpath (no junit on it) into target/classes
     * (shadow class + spurious "程序包 org.junit.jupiter.api 不存在").
     */
    @Test
    public void testRepoRootModuleTestSourceWithoutLeadingSlash() {
        IncrementalCompileExecutor.SourceSplit split = IncrementalCompileExecutor.splitBySourceRoot(List.of(
                "src/main/java/demo/Calc.java",
                "src/test/java/demo/CalcTest.java"));
        assertTrue(split.hasBoth());
        assertEquals(List.of("src/main/java/demo/Calc.java"), split.mainSources());
        assertEquals(List.of("src/test/java/demo/CalcTest.java"), split.testSources());
    }

    @Test
    public void testImmutableSplit() {
        IncrementalCompileExecutor.SourceSplit split = IncrementalCompileExecutor.splitBySourceRoot(List.of(
                "mod-a/src/main/java/io/x/Foo.java", "mod-a/src/test/java/io/x/FooTest.java"));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> split.mainSources().add("x"));
    }
}
