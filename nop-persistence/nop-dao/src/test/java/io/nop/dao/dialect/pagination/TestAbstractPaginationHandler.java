/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect.pagination;

import io.nop.api.core.beans.LongRangeBean;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 处置新增：prepareStatement 的 limit 钳制——Long.MAX_VALUE 表示"取全部"，
 * 强转 int 溢出为负值会使 setMaxRows 语义错误；超 int 范围时不设置 maxRows（不限制）。
 */
public class TestAbstractPaginationHandler {

    static class RecordingHandler extends AbstractPaginationHandler {
        final List<Integer> maxRows = new ArrayList<>();

        @Override
        public io.nop.core.lang.sql.SqlExprList buildPageExpr(io.nop.core.lang.sql.ISqlExpr limit,
                                                              io.nop.core.lang.sql.ISqlExpr offset,
                                                              io.nop.core.lang.sql.ISqlExpr sqlExpr) {
            return null;
        }
    }

    private static RecordingHandler record() {
        return new RecordingHandler();
    }

    private static PreparedStatement stub(RecordingHandler handler) {
        return (PreparedStatement) Proxy.newProxyInstance(TestAbstractPaginationHandler.class.getClassLoader(),
                new Class[]{PreparedStatement.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if (method.getName().equals("setMaxRows"))
                            handler.maxRows.add((Integer) args[0]);
                        return null;
                    }
                });
    }

    @Test
    public void testNormalLimitSetAsMaxRows() throws Exception {
        RecordingHandler handler = record();
        handler.prepareStatement(LongRangeBean.of(0, 10), stub(handler));
        assertEquals(List.of(10), handler.maxRows);
    }

    @Test
    public void testLongMaxValueLimitDoesNotSetMaxRows() throws Exception {
        RecordingHandler handler = record();
        // Long.MAX_VALUE = 取全部：不设置 maxRows（此前 (int) Long.MAX_VALUE = -1，语义错误）
        handler.prepareStatement(LongRangeBean.of(0, Long.MAX_VALUE), stub(handler));
        assertTrue(handler.maxRows.isEmpty(), "huge limit must not overflow into setMaxRows");
    }

    @Test
    public void testZeroLimitDoesNotSetMaxRows() throws Exception {
        RecordingHandler handler = record();
        handler.prepareStatement(LongRangeBean.of(0, 0), stub(handler));
        assertTrue(handler.maxRows.isEmpty());
    }

    @Test
    public void testMaxIntLimitStillSet() throws Exception {
        RecordingHandler handler = record();
        handler.prepareStatement(LongRangeBean.of(0, Integer.MAX_VALUE), stub(handler));
        assertEquals(List.of(Integer.MAX_VALUE), handler.maxRows);
    }
}
