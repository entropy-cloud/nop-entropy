package io.nop.core.model.table;

import io.nop.commons.util.StringHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestCellRangeMerger {

    /**
     * 0110
     * 1110
     * 1111
     * 1111
     * 0011
     */
    @Test
    public void testMerge() {
        String str = "0110\n1110\n1111\n1111\n0011";

        List<CellRange> ranges = buildRanges(str);
        System.out.println(ranges);
        assertEquals("[B1:C1, A2:C2, A3:D4, C5:D5]", ranges.toString());
    }

    @Test
    public void testRange() {
        String str = "0\n1\n0\n1";

        List<CellRange> ranges = buildRanges(str);
        System.out.println(ranges);
        assertEquals("[A2, A4]", ranges.toString());
    }

    /**
     * 101
     * 111
     * 101
     * 011
     */
    @Test
    public void testMerge2() {
        String str = "101\n111\n101\n011";

        List<CellRange> ranges = buildRanges(str);
        System.out.println(ranges);
        assertEquals("[A1, A3, C1, A2:C2, C3, B4:C4]", ranges.toString());
    }

    /**
     * 010
     * 101
     * 010
     */
    @Test
    public void testMerge3() {
        String str = "010\n101\n010";

        List<CellRange> ranges = buildRanges(str);
        System.out.println(ranges);
        assertEquals("[A2, B1, C2, B3]", ranges.toString());
    }

    List<CellRange> buildRanges(String str) {
        CellRangeMerger merger = new CellRangeMerger();

        List<String> lines = StringHelper.split(str, '\n');
        for (int i = 0, n = lines.size(); i < n; i++) {
            String line = lines.get(i);
            for (int j = 0; j < line.length(); j++) {
                char c = line.charAt(j);
                if (c == '1') {
                    merger.addCell(i, j);
                }
            }
        }
        assertEquals(str, merger.display());
        return merger.getMergedRanges();
    }
}
