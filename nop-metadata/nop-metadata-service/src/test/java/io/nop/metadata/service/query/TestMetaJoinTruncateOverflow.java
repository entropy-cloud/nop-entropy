/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service.query;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 MetaJoinExecutor.truncate / MetaAggregationExecutor.truncateCrossDb 的整数溢出处理
 * （plan 1250-2 Phase 4 Proof，AR-08）。
 *
 * <p>AR-08 修复前：truncate 用 Math.toIntExact(Long)，offset > Integer.MAX_VALUE 时抛
 * ArithmeticException（绕过 ErrorCode），生产监控无法定位。
 *
 * <p>修复后：显式抛 ERR_PAGINATION_OFFSET_TOO_LARGE / ERR_PAGINATION_LIMIT_TOO_LARGE 并附 param。
 */
public class TestMetaJoinTruncateOverflow {

    private final MetaJoinExecutor executor = new MetaJoinExecutor();

    @Test
    public void testOffsetOverflowThrowsExplicitErrorCode() {
        List<Map<String, Object>> rows = new ArrayList<>();
        NopException ex = assertThrows(NopException.class,
                () -> invokeTruncate(rows, null, (long) Integer.MAX_VALUE + 1L),
                "offset > Integer.MAX_VALUE must throw ERR_PAGINATION_OFFSET_TOO_LARGE");
        assertEquals(MetaJoinExecutor.ERR_PAGINATION_OFFSET_TOO_LARGE.getErrorCode(),
                ex.getErrorCode());
        assertEquals((long) Integer.MAX_VALUE + 1L, ((Number) ex.getParam("offset")).longValue());
    }

    @Test
    public void testLimitOverflowThrowsExplicitErrorCode() {
        List<Map<String, Object>> rows = new ArrayList<>();
        NopException ex = assertThrows(NopException.class,
                () -> invokeTruncate(rows, (long) Integer.MAX_VALUE + 1L, null),
                "limit > Integer.MAX_VALUE must throw ERR_PAGINATION_LIMIT_TOO_LARGE");
        assertEquals(MetaJoinExecutor.ERR_PAGINATION_LIMIT_TOO_LARGE.getErrorCode(),
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
        // offset=2, limit=5 → indices [2,7), 5 rows
        List<Map<String, Object>> result = invokeTruncate(rows, 5L, 2L);
        assertEquals(5, result.size(), "normal limit/offset must work as before");
    }

    @Test
    public void testAggregationTruncateCrossDbOffsetOverflow() {
        // MetaAggregationExecutor.truncateCrossDb is private static
        List<Map<String, Object>> rows = new ArrayList<>();
        NopException ex = assertThrows(NopException.class,
                () -> invokeAggregationTruncate(rows, null, (long) Integer.MAX_VALUE + 1L),
                "aggregation offset overflow must throw ERR_PAGINATION_OFFSET_TOO_LARGE");
        assertEquals(MetaJoinExecutor.ERR_PAGINATION_OFFSET_TOO_LARGE.getErrorCode(),
                ex.getErrorCode());
    }

    @Test
    public void testAggregationTruncateCrossDbLimitOverflow() {
        List<Map<String, Object>> rows = new ArrayList<>();
        NopException ex = assertThrows(NopException.class,
                () -> invokeAggregationTruncate(rows, (long) Integer.MAX_VALUE + 1L, null),
                "aggregation limit overflow must throw ERR_PAGINATION_LIMIT_TOO_LARGE");
        assertEquals(MetaJoinExecutor.ERR_PAGINATION_LIMIT_TOO_LARGE.getErrorCode(),
                ex.getErrorCode());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> invokeTruncate(List<Map<String, Object>> rows, Long limit, Long offset) {
        try {
            Method m = MetaJoinExecutor.class.getDeclaredMethod("truncate",
                    List.class, Long.class, Long.class);
            m.setAccessible(true);
            return (List<Map<String, Object>>) m.invoke(executor, rows, limit, offset);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            return rethrow(ite.getCause());
        } catch (Exception e) {
            throw new NopException(ApiErrors.ERR_WRAP_EXCEPTION, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> invokeAggregationTruncate(List<Map<String, Object>> rows,
                                                                       Long limit, Long offset) {
        try {
            Method m = MetaAggregationExecutor.class.getDeclaredMethod("truncateCrossDb",
                    List.class, Long.class, Long.class);
            m.setAccessible(true);
            return (List<Map<String, Object>>) m.invoke(null, rows, limit, offset);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            return rethrow(ite.getCause());
        } catch (Exception e) {
            throw new NopException(ApiErrors.ERR_WRAP_EXCEPTION, e);
        }
    }

    /** Sneaky-throw to preserve exact ErrorCode type for assertThrows. */
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
