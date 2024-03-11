package io.nop.core.lang.sql;

import io.nop.commons.text.marker.Markers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestSQL {
    @Test
    public void testRemoveRange() {
        SQL.SqlBuilder sb = SQL.begin();
        sb.append("01");
        sb.appendWithValueMarker("XX", "a", "A", false);
        sb.append('4');
        sb.append("5UVW");
        sb.appendMarker(new Markers.ValueMarker(6, 8, "b", "B", false));

        System.out.println(sb.end().getFormattedText());

        SQL sql = sb.removeRange(4, 5).end();

        System.out.println(sql.getFormattedText());
        assertEquals("01/*XX*/'A'5/*UV*/'B'W", sql.getFormattedText());

        sb.insertAt(1, SQL.begin().sql("+?+", "v").end());
        assertEquals("0+/*?*/'v'+1/*XX*/'A'5/*UV*/'B'W", sb.end().getFormattedText());

        SQL fullSql = SQL.begin()
                .sql("where a=3 ").addFilterMarker(SyntaxMarker.TAG_AND, "MyEntity", "t")
                .transformMarker(marker -> {
                    if (marker instanceof SyntaxMarker) {
                        SyntaxMarker syntaxMarker = (SyntaxMarker) marker;
                        switch (syntaxMarker.getType()) {
                            case FILTER:
                                return sql;
                        }
                    }
                    return null;
                })
                .end();

        assertEquals("where a=3 01/*XX*/'A'5/*UV*/'B'W", fullSql.getFormattedText());
    }
}
