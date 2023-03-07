/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.dict;

import io.nop.api.core.beans.DictBean;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.type.GenericClassKind;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDictProvider {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testLoad() {
        DictBean dict = DictProvider.instance().requireDict("en", "test/my", null);
        dict.getLabelByValue(1).equals("Item1");
    }

    @Test
    public void testEnum() {
        DictBean dict = DictProvider.instance().requireDict("en", GenericClassKind.class.getName(), null);
        assertTrue(dict.getLabelByValue("interface").endsWith("INTERFACE"));
    }
}
