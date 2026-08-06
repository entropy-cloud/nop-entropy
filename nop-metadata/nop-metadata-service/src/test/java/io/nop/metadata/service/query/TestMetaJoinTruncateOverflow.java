package io.nop.metadata.service.query;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 CrossDbJoinMerger.truncate 的整数溢出处理（plan 1250-2 Phase 4 Proof，AR-08）。
 */
public class TestMetaJoinTruncateOverflow {

    private final CrossDbJoinMerger merger = new CrossDbJoinMerger();

    @Test
    public void testOffsetOverflowThrowsExplicitErrorCode() {
        List<Map<String, Object>> rows = new ArrayList<>();
        NopException ex = assertThrows(NopException.class,
                () -> merger.truncate(rows, null, (long) Integer.MAX_VALUE + 1L),
                "offset > Integer.MAX_VALUE must throw ERR_PAGINATION_OFFSET_TOO_LARGE");
        assertEquals(NopMetadataErrors.ERR_PAGINATION_OFFSET_TOO_LARGE.getErrorCode(),
                ex.getErrorCode());
        assertEquals((long) Integer.MAX_VALUE + 1L, ((Number) ex.getParam("offset")).longValue());
    }

    @Test
    public void testLimitOverflowThrowsExplicitErrorCode() {
        List<Map<String, Object>> rows = new ArrayList<>();
        NopException ex = assertThrows(NopException.class,
                () -> merger.truncate(rows, (long) Integer.MAX_VALUE + 1L, null),
                "limit > Integer.MAX_VALUE must throw ERR_PAGINATION_LIMIT_TOO_LARGE");
        assertEquals(NopMetadataErrors.ERR_PAGINATION_LIMIT_TOO_LARGE.getErrorCode(),
                ex.getErrorCode());
        assertEquals((long) Integer.MAX_VALUE + 1L, ((Number) ex.getParam("limit")).longValue());
    }

    @Test
    public void testNormalLimitOffsetStillWork() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("k", i);
            rows.add(r);
        }
        List<Map<String, Object>> result = merger.truncate(rows, 5L, 2L);
        assertEquals(5, result.size(), "normal limit/offset must work as before");
    }

    @Test
    public void testAggregationTruncateCrossDbOffsetOverflow() {
        List<Map<String, Object>> rows = new ArrayList<>();
        NopException ex = assertThrows(NopException.class,
                () -> invokeAggregationTruncate(rows, null, (long) Integer.MAX_VALUE + 1L),
                "aggregation offset overflow must throw ERR_PAGINATION_OFFSET_TOO_LARGE");
        assertEquals(NopMetadataErrors.ERR_PAGINATION_OFFSET_TOO_LARGE.getErrorCode(),
                ex.getErrorCode());
    }

    @Test
    public void testAggregationTruncateCrossDbLimitOverflow() {
        List<Map<String, Object>> rows = new ArrayList<>();
        NopException ex = assertThrows(NopException.class,
                () -> invokeAggregationTruncate(rows, (long) Integer.MAX_VALUE + 1L, null),
                "aggregation limit overflow must throw ERR_PAGINATION_LIMIT_TOO_LARGE");
        assertEquals(NopMetadataErrors.ERR_PAGINATION_LIMIT_TOO_LARGE.getErrorCode(),
                ex.getErrorCode());
    }

    // ============================================================
    // AR-09（plan 2026-08-06-0553-3 Phase 1）：负 limit 显式拒绝 + int 溢出 long 运算
    // ============================================================

    @Test
    public void testNegativeLimitThrowsExplicitErrorCode() {
        List<Map<String, Object>> rows = seededRows(10);
        NopException ex = assertThrows(NopException.class,
                () -> merger.truncate(rows, -5L, null),
                "negative limit must throw ERR_PAGINATION_LIMIT_INVALID (not bare IllegalArgumentException)");
        assertEquals(NopMetadataErrors.ERR_PAGINATION_LIMIT_INVALID.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testLimitIntOverflowWithOffsetDoesNotThrow() {
        // 溢出用例必须 seed >= 1 行：空 rows 时 min(0, 0+MAX)=0，subList(0,0) 不抛异常，red 不会触发
        List<Map<String, Object>> rows = seededRows(10);
        List<Map<String, Object>> result = merger.truncate(rows, (long) Integer.MAX_VALUE, 1L);
        assertEquals(9, result.size(),
                "limit=Integer.MAX_VALUE offset=1 on 10 rows must yield 9 rows (long math, no int overflow)");
    }

    @Test
    public void testAggregationTruncateCrossDbNegativeLimit() {
        List<Map<String, Object>> rows = seededRows(10);
        NopException ex = assertThrows(NopException.class,
                () -> invokeAggregationTruncate(rows, -5L, null),
                "aggregation negative limit must throw ERR_PAGINATION_LIMIT_INVALID");
        assertEquals(NopMetadataErrors.ERR_PAGINATION_LIMIT_INVALID.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testAggregationTruncateCrossDbLimitOverflowWithOffsetDoesNotThrow() {
        List<Map<String, Object>> rows = seededRows(10);
        List<Map<String, Object>> result = invokeAggregationTruncate(rows, (long) Integer.MAX_VALUE, 1L);
        assertEquals(9, result.size(),
                "aggregation limit=Integer.MAX_VALUE offset=1 on 10 rows must yield 9 rows (long math)");
    }

    private static List<Map<String, Object>> seededRows(int n) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("k", i);
            rows.add(r);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> invokeAggregationTruncate(List<Map<String, Object>> rows,
                                                                        Long limit, Long offset) {
        try {
            Method m = AggregationHelper.class.getDeclaredMethod("truncateCrossDb",
                    List.class, Long.class, Long.class);
            m.setAccessible(true);
            return (List<Map<String, Object>>) m.invoke(null, rows, limit, offset);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            return rethrow(ite.getCause());
        } catch (Exception e) {
            throw new NopException(ApiErrors.ERR_WRAP_EXCEPTION, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T rethrow(Throwable t) {
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        }
        if (t instanceof Error) {
            throw (Error) t;
        }
        throw new NopException(ApiErrors.ERR_WRAP_EXCEPTION, t);
    }
}
