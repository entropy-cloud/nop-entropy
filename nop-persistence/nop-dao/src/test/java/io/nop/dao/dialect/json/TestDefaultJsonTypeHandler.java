/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect.json;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JSON字面量以单引号包裹时，内容中的单引号必须转义，否则产生SQL语法错误或注入点
 */
public class TestDefaultJsonTypeHandler {

    private final DefaultJsonTypeHandler handler = new DefaultJsonTypeHandler();

    @Test
    public void testNullLiteral() {
        assertEquals("NULL", handler.toLiteral(null, null));
    }

    @Test
    public void testStringWithoutQuote() {
        assertEquals("JSON '{\"a\":1}'", handler.toLiteral("{\"a\":1}", null));
    }

    @Test
    public void testStringWithSingleQuoteEscaped() {
        String literal = handler.toLiteral("{\"name\":\"it's\"}", null);
        // 内部单引号必须翻倍转义，整个字面量恰好是一段合法SQL
        assertEquals("JSON '{\"name\":\"it''s\"}'", literal);
    }

    @Test
    public void testObjectStringWithSingleQuoteEscaped() {
        String literal = handler.toLiteral(Map.of("name", "it's"), null);
        assertEquals("JSON '{\"name\":\"it''s\"}'", literal);
    }
}
