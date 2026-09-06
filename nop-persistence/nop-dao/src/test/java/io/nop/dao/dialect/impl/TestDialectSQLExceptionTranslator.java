/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect.impl;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.dao.dialect.model.DialectErrorCodeModel;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.exceptions.JdbcException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * check2 处置新增：errorCode 消息匹配分支的预编译行为——含下划线的 key 在构造期一次性编译
 * （不再每次翻译重复 Pattern.compile），且 key 按正则语义匹配（duckdb 方言的
 * {@code .+_with_name_.+_does_not_exist.*} 即按此设计，不得字面量化）。
 */
public class TestDialectSQLExceptionTranslator {

    private static DialectModel dialectModel(String name, String... values) {
        DialectErrorCodeModel errorCodeModel = new DialectErrorCodeModel();
        errorCodeModel.setName(name);
        errorCodeModel.setValues(Set.of(values));
        DialectModel model = new DialectModel();
        model.setErrorCodes(List.of(errorCodeModel));
        return model;
    }

    @Test
    public void testMessagePatternsPrecompiledAtConstruction() {
        DialectSQLExceptionTranslator translator = new DialectSQLExceptionTranslator(
                dialectModel("nop.err.dao.sql.bad-sql-grammar", ".+_with_name_.+_does_not_exist.*", "42000"));

        List<Map.Entry<Pattern, ErrorCode>> patterns = translator.buildMessagePatterns();
        assertEquals(1, patterns.size(), "only underscore-containing keys build message patterns");
        Pattern pattern = patterns.get(0).getKey();
        assertEquals(Pattern.compile(".+ with name .+ does not exist.*",
                Pattern.MULTILINE | Pattern.DOTALL | Pattern.CASE_INSENSITIVE).pattern(), pattern.pattern());
    }

    /**
     * duckdb 语义固化：正则形态的 errorCode 值（下划线换空格后）按 matches() 全文匹配错误消息。
     * 无 errorCode 且无 SQLState 的驱动异常才会走该分支。
     */
    @Test
    public void testRegexStyleErrorCodeMatchesMessage() {
        DialectSQLExceptionTranslator translator = new DialectSQLExceptionTranslator(
                dialectModel("nop.err.dao.sql.bad-sql-grammar", ".+_with_name_.+_does_not_exist.*", "42000"));

        SQLException ex = new SQLException("Table with name my_table does not exist!", null, 0);
        assertEquals(0, ex.getErrorCode());
        assertNull(ex.getSQLState(), "precondition: no SQLState so message matching is reachable");

        JdbcException translated = translator.translate("rs.set", ex);
        assertNotNull(translated);
        assertEquals("nop.err.dao.sql.bad-sql-grammar", translated.getErrorCode());
    }

    @Test
    public void testNumericCodeStillWinsOverMessage() {
        DialectSQLExceptionTranslator translator = new DialectSQLExceptionTranslator(
                dialectModel("nop.err.dao.sql.bad-sql-grammar", ".+_with_name_.+_does_not_exist.*", "42000"));

        // 有 errorCode 时按数值匹配，不进入消息分支
        SQLException ex = new SQLException("some message", null, 42000);
        JdbcException translated = translator.translate("rs.set", ex);
        assertEquals("nop.err.dao.sql.bad-sql-grammar", translated.getErrorCode());
    }
}
