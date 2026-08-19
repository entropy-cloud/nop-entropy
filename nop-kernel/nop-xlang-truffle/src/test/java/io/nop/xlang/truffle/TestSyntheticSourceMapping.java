package io.nop.xlang.truffle;

import com.oracle.truffle.api.source.SourceSection;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.truffle.translate.SyntheticSources;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
