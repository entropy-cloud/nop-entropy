/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.i18n;

import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestJsonI18nHelper {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testTransform() {
        Map<String, Object> ret = new HashMap<>();
        ret.put("name", "@i18n:test|A");
        ret.put("name2", "@i18n:test2");
        ret.put("value", "1={@i18n:test.view|B}");

        Map<String, Object> copy = new HashMap<>(ret);
        JsonI18nHelper.bindExprToI18nKey(copy);
        System.out.println(copy);
        assertEquals("{name=A, i18n:value=1={@i18n:test.view|B}, value=1=B, i18n:name=test, i18n:name2=test2}",
                copy.toString());

        JsonI18nHelper.i18nKeyToBindExpr(copy);
        assertEquals(ret, copy);
    }
}
