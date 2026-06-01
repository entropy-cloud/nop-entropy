/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.i18n;

import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestI18nMessageManager {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        I18nMessageManager.instance().loadAllI18nMessages();
        I18nMessageManager.instance().clearAllI18nMessages();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testNormalizeLocaleFallsBackToLanguage() {
        assertEquals("en", I18nMessageManager.instance().normalizeLocale("en-US"));
    }

    @Test
    public void testGetMessageFallsBackToLanguageMessages() {
        assertEquals("Item1", I18nMessageManager.instance().getMessage("en-US", "dict.option.test.my.1", null));
    }
}
