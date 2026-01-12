/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.config;

import io.nop.api.core.util.SourceLocation;
import org.junit.jupiter.api.Test;

import static io.nop.api.core.config.AppConfig.varRef;
import static io.nop.api.core.config.AppConfig.withPlaceholder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2026-01-11
 */
public class TestAppConfig {
    private static final SourceLocation s_loc = SourceLocation.fromClass(TestAppConfig.class);

    private static final IConfigReference<String> NOP_CONFIG_KEY1 = //
            withPlaceholder(varRef(s_loc, "nop.config.test.key1", String.class, "key1"));
    private static final IConfigReference<String> NOP_CONFIG_KEY2 = //
            varRef(s_loc, "nop.config.test.key2", String.class, null);

    @Test
    public void testPlaceholder() {
        String key2Value = "key2";
        assertNotEquals(key2Value, NOP_CONFIG_KEY1.getDefaultValue());

        // 首次替换被引用配置项
        String key1Value = "value = ${nop.config.test.key2}";
        AppConfig.getConfigProvider().updateConfigValue(NOP_CONFIG_KEY1, key1Value);
        AppConfig.getConfigProvider().updateConfigValue(NOP_CONFIG_KEY2, key2Value);

        key1Value = NOP_CONFIG_KEY1.get();
        assertEquals("value = " + key2Value, key1Value);
        assertEquals(key1Value, NOP_CONFIG_KEY1.get());
        assertNotEquals(NOP_CONFIG_KEY1.getDefaultValue(), NOP_CONFIG_KEY1.get());

        // 更新被引用配置项后，不再影响引用方
        key2Value = "key2-1";
        AppConfig.getConfigProvider().updateConfigValue(NOP_CONFIG_KEY2, key2Value);
        assertEquals(key1Value, NOP_CONFIG_KEY1.get());

        // 更新配置项后，其引用不再被替换
        key1Value = "key1 value = ${nop.config.test.key2}";
        AppConfig.getConfigProvider().updateConfigValue(NOP_CONFIG_KEY1, key1Value);
        assertEquals(key1Value, NOP_CONFIG_KEY1.get());
    }
}
