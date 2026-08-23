/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.query;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSqlLikeUtils {

    @Test
    public void testLikeHappyPath() {
        assertTrue(SqlLikeUtils.like("abc", "a%"));
        assertTrue(SqlLikeUtils.like("abc", "a_c"));
        assertEquals(false, SqlLikeUtils.like("abc", "b%"));
    }

    @Test
    public void testInvalidEscapeCharacter() {
        NopException e = assertThrows(NopException.class, () -> SqlLikeUtils.sqlToRegexLike("a%", "ab"));
        assertEquals("nop.err.core.sql.like-invalid-escape-char", e.getErrorCode());

        NopException e2 = assertThrows(NopException.class, () -> SqlLikeUtils.sqlToRegexSimilar("a%", "ab"));
        assertEquals("nop.err.core.sql.like-invalid-escape-char", e2.getErrorCode());
    }

    @Test
    public void testInvalidEscapeSequence() {
        // 转义字符出现在模式串末尾
        NopException e = assertThrows(NopException.class, () -> SqlLikeUtils.sqlToRegexLike("a\\", "\\"));
        assertEquals("nop.err.core.sql.like-invalid-escape-sequence", e.getErrorCode());

        // 转义字符后跟的不是 _ % 或转义字符本身
        NopException e2 = assertThrows(NopException.class, () -> SqlLikeUtils.sqlToRegexLike("a\\b", "\\"));
        assertEquals("nop.err.core.sql.like-invalid-escape-sequence", e2.getErrorCode());
    }

    @Test
    public void testInvalidSimilarRegex() {
        // 字符枚举 [b 缺少右括号
        NopException e = assertThrows(NopException.class, () -> SqlLikeUtils.similar("a", "a[b"));
        assertEquals("nop.err.core.sql.similar-invalid-regex", e.getErrorCode());
    }
}
