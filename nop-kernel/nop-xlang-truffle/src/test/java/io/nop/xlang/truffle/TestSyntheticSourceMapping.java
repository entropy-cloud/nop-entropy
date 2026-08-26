package io.nop.xlang.truffle;

import com.oracle.truffle.api.source.SourceSection;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.truffle.translate.SyntheticSources;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 合成 Source 回映射单测（Phase 2）：同一节点位置的 SourceSection 回映射到与解释器一致的
 * 源位置（path + line + col 往返一致；对拍第三层断言的前置）。无位置节点不虚构 section。
 */
public class TestSyntheticSourceMapping {

    @Test
    public void testRoundTripLineColPreserved() {
        SourceLocation loc = SourceLocation.fromLine("/xlang-compare/static/exception-method.xpl", 2, 5);
        SourceSection section = SyntheticSources.sectionOf(loc);
        assertEquals(loc.getPath(), section.getSource().getName());
        assertEquals(2, section.getStartLine());
        assertEquals(5, section.getStartColumn());

        SourceLocation mapped = SyntheticSources.toSourceLocation(section);
        assertEquals(loc.getPath(), mapped.getPath());
        assertEquals(loc.getLine(), mapped.getLine());
        assertEquals(loc.getCol(), mapped.getCol());
    }

    @Test
    public void testLineOnlyLocation() {
        SourceLocation loc = SourceLocation.fromLine("xlang-compare/dynamic/a.expr", 1);
        SourceSection section = SyntheticSources.sectionOf(loc);
        SourceLocation mapped = SyntheticSources.toSourceLocation(section);
        assertEquals("xlang-compare/dynamic/a.expr", mapped.getPath());
        assertEquals(1, mapped.getLine());
    }

    @Test
    public void testNullLocationYieldsNoSection() {
        assertNull(SyntheticSources.sectionOf(null));
        assertNull(SyntheticSources.toSourceLocation(null));
    }

    @Test
    public void testSectionMatchesCorpusExpectedErrorLocation() {
        List.of(
                SourceLocation.fromLine("xlang-compare/static/exception-method.xpl", 2),
                SourceLocation.fromLine("xlang-compare/dynamic/exception-method.expr", 1)
        ).forEach(loc -> {
            SourceLocation mapped = SyntheticSources.toSourceLocation(SyntheticSources.sectionOf(loc));
            assertEquals(loc.getPath(), mapped.getPath(), "path must map back (harness exception-location layer)");
            assertEquals(loc.getLine(), mapped.getLine(), "line must map back (harness exception-location layer)");
        });
    }

    /**
     * check2 P3：同一路径的所有 section 必须复用同一个共享 Source 实例（按路径缓存），
     * 而不是每个节点各建一个 Source——共享 Engine 的 source 管理不再随节点数线性增长。
     */
    @Test
    public void testSamePathSharesSourceInstance() {
        String path = "synthetic-source-share-test.xpl";
        // 三个坐标都在初始容量（pow2 网格 4 行 × 8 列）内，不触发重建
        SourceSection s1 = SyntheticSources.sectionOf(SourceLocation.fromLine(path, 3, 7));
        SourceSection s2 = SyntheticSources.sectionOf(SourceLocation.fromLine(path, 4, 2));
        SourceSection s3 = SyntheticSources.sectionOf(SourceLocation.fromLine(path, 3, 7));
        assertSame(s1.getSource(), s2.getSource(), "sections of the same path must share one Source");
        assertSame(s1.getSource(), s3.getSource());

        // 行列仍精确回映射
        assertEquals(3, s1.getStartLine());
        assertEquals(7, s1.getStartColumn());
        assertEquals(4, s2.getStartLine());
        assertEquals(2, s2.getStartColumn());

        // 不同路径不串实例
        SourceSection other = SyntheticSources.sectionOf(
                SourceLocation.fromLine("synthetic-source-share-other.xpl", 3, 7));
        assertNotSame(s1.getSource(), other.getSource());
    }

    /** 请求超出网格容量时按几何增长重建，行列回映射仍然精确。 */
    @Test
    public void testCapacityGrowthPreservesMapping() {
        String path = "synthetic-source-growth-test.xpl";
        // 首个小网格
        assertNotNull(SyntheticSources.sectionOf(SourceLocation.fromLine(path, 2, 3)));
        // 远超初始容量的行列触发重建
        SourceSection big = SyntheticSources.sectionOf(SourceLocation.fromLine(path, 5000, 300));
        assertEquals(5000, big.getStartLine());
        assertEquals(300, big.getStartColumn());
        SourceLocation mapped = SyntheticSources.toSourceLocation(big);
        assertEquals(5000, mapped.getLine());
        assertEquals(300, mapped.getCol());

        // 重建后，旧行列的新 section 与大行列复用同一（重建后的）实例
        SourceSection small = SyntheticSources.sectionOf(SourceLocation.fromLine(path, 2, 3));
        assertSame(big.getSource(), small.getSource());
        assertEquals(2, small.getStartLine());
        assertEquals(3, small.getStartColumn());
    }
}
