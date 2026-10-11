/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.config;

import io.nop.api.core.util.StaticValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 回归测试：{@link AbstractConfigProvider#reset()} 必须按当前系统属性重采样恢复
 * 基线，而非沿用首次访问时登记的 write-once 快照。否则 fork 存活期内
 * {@code System.setProperty} 污染后再 {@code clearProperty}，污染值会被永久
 * 固化为"基线"，测试框架（NopJunitExtension afterAll）的 reset 无法洗净。
 */
public class TestConfigProviderResetSampling {
    private static final String VAR = "nop.api.test.reset-sampling";

    @Test
    public void testResetResamplesSystemProperty() {
        System.setProperty(VAR, "polluted");
        try {
            SimpleConfigProvider provider = new SimpleConfigProvider();

            // 首次静态访问：快照登记当时的系统属性（污染值）——旧语义的起点
            IConfigReference<String> ref = provider.getStaticConfigReference(VAR, String.class, "default-x", null);
            assertEquals("polluted", ref.get());

            // 动态赋值后清理系统属性
            provider.assignConfigValue(VAR, "assigned");
            assertEquals("assigned", ref.get());

            System.clearProperty(VAR);
            provider.reset();

            // 新语义：clearProperty 后 reset 恢复干净基线（声明缺省值），
            // 旧实现在此返回固化的污染快照 "polluted"
            assertEquals("default-x", ref.get());
            // 快照本身同步刷新，后续 reset 不会再次回灌污染值
            StaticValue<?> snapshot = provider.getStaticConfigValues().get(VAR);
            assertNull(snapshot.get());
        } finally {
            System.clearProperty(VAR);
        }
    }
}
