package io.nop.pdf.extract;

import io.nop.pdf.extract.struct.TableBlock;

import java.util.function.Predicate;

public class BlockFilters {
    public static Predicate<TableBlock> filterTable(final int minCol,
                                                    final int maxCol) {
        return table -> {
            if (minCol > 0 && table.getColCount() < minCol)
                return false;
            if (maxCol > 0 && table.getColCount() > maxCol)
                return false;
            return true;
        };
    }
}
