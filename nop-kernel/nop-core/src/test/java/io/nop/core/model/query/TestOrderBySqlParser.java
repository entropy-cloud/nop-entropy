package io.nop.core.model.query;

import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestOrderBySqlParser {

    private final OrderBySqlParser parser = OrderBySqlParser.INSTANCE;

    @Test
    public void testBlankTextReturnsNull() {
        assertNull(parser.parseFromText(null, null));
        assertNull(parser.parseFromText(null, "   "));
    }

    @Test
    public void testParseSingleFieldDefaultsAsc() {
        List<OrderFieldBean> list = parser.parseFromText(SourceLocation.fromPath("test"), "name");
        assertEquals(1, list.size());
        assertEquals("name", list.get(0).getName());
        assertFalse(list.get(0).isDesc());
        assertNull(list.get(0).getNullsFirst());
    }

    @Test
    public void testParseAscDesc() {
        List<OrderFieldBean> list = parser.parseFromText(null, "a asc, b desc");
        assertEquals(2, list.size());
        assertFalse(list.get(0).isDesc());
        assertTrue(list.get(1).isDesc());
    }

    @Test
    public void testParseNullsFirstLast() {
        List<OrderFieldBean> list = parser.parseFromText(null, "a desc nulls first, b asc nulls last");
        assertEquals(2, list.size());
        assertTrue(list.get(0).isDesc());
        assertEquals(Boolean.TRUE, list.get(0).getNullsFirst());
        assertFalse(list.get(1).isDesc());
        assertEquals(Boolean.FALSE, list.get(1).getNullsFirst());
    }

    @Test
    public void testParsePropPath() {
        List<OrderFieldBean> list = parser.parseFromText(null, "myObj.myProp desc");
        assertEquals(1, list.size());
        assertEquals("myObj.myProp", list.get(0).getName());
    }

    @Test
    public void testTrailingGarbageThrowsInvalidOrderBySql() {
        // 解析完字段后还有残留 token 应报 ERR_QUERY_INVALID_ORDER_BY_SQL
        NopException e = assertThrows(NopException.class,
                () -> parser.parseFromText(null, "a asc )"));
        assertEquals("nop.err.core.query.invalid-order-by-sql", e.getErrorCode());
    }

    @Test
    public void testNullsMissingFirstLastThrowsIncomplete() {
        // nulls 后面既不是 first 也不是 last 应报 token incomplete
        assertThrows(NopException.class, () -> parser.parseFromText(null, "a nulls middle"));
    }
}
