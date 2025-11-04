/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCharSequenceHelper {
    @Test
    public void testMatch() {
        assertTrue(CharSequenceHelper.startsWith("abc", "abc"));
        assertTrue(CharSequenceHelper.endsWith("abc", "abc"));

        assertTrue(CharSequenceHelper.startsWith(CharSequenceHelper.trim(" abc "), "abc"));

        assertTrue(CharSequenceHelper.startsWith(CharSequenceHelper.trim(""), ""));
    }
}
