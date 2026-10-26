/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.impl;

import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.crypto.impl.AESTextCipher;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.config.enhancer.DefaultConfigValueEnhancer;
import io.nop.config.model.ConfigModel;
import io.nop.config.model.ConfigVarModel;
import io.nop.config.source.DynamicConfigSource;
import io.nop.config.source.StaticConfigSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * plan 2507 C1-007 端到端链路 2（rule 22）：配置源刷新 →
 * {@link DefaultConfigProvider#applyChange()}（W1 applyChangeToUsed 主写点）/
 * {@link DefaultConfigProvider#changeConfigSource(IConfigSource)}（W2 主路径）→
 * {@code DefaultConfigReference.updateValue} holder 单 volatile 发布 → 下游
 * {@link IConfigReference} 读站读到新代 (value, location) 原子对——发布协议从
 * 写点入口到下游出口完整走通。W7（setConfigModel 的 defaultValue 写点）不随
 * holder 代际替换，独立字段语义回归。
 */
public class TestConfigRefreshPublishChain {
    private static final String KEY = "nop.test.refresh.key";
    private static final SourceLocation LOC_1 = SourceLocation.fromPath("test://refresh-loc-1");
    private static final SourceLocation LOC_2 = SourceLocation.fromPath("test://refresh-loc-2");
    private static final SourceLocation LOC_3 = SourceLocation.fromPath("test://refresh-loc-3");

    private static Map<String, ValueWithLocation> vars(SourceLocation loc, String value) {
        Map<String, ValueWithLocation> ret = new HashMap<>();
        ret.put(KEY, ValueWithLocation.vl(loc, value));
        return ret;
    }

    @Test
    public void testRefreshChainPublishesNewHolderPairToUsedRef() {
        DynamicConfigSource source = new DynamicConfigSource("test-refresh-chain");
        source.setConfigVars(vars(LOC_1, "v-1"));

        // 与 ConfigStarter 同构装配：AESTextCipher + DefaultConfigValueEnhancer
        DefaultConfigProvider provider = new DefaultConfigProvider(source,
                new DefaultConfigValueEnhancer(new AESTextCipher()));

        // W2 changeConfigSource：buildConfigRefs + updateRefs → updateValue（holder 单发布）
        provider.changeConfigSource(source);

        IConfigReference<String> ref = provider.getConfigReference(KEY, String.class, null, LOC_1);
        assertEquals("v-1", ref.get());
        assertEquals(LOC_1, ref.getLocation());

        // W1 运行期刷新主写点：applyChange → applyChangeToUsed → updateValue
        // 下游读者读站一次读到新代 (value, location) 原子对
        source.setConfigVars(vars(LOC_2, "v-2"));
        provider.applyChange();

        assertEquals("v-2", ref.get());
        assertEquals(LOC_2, ref.getLocation());

        // W2 换源主路径再验证
        provider.changeConfigSource(new StaticConfigSource("test-refresh-chain-2", vars(LOC_3, "v-3")));
        assertEquals("v-3", ref.get());
        assertEquals(LOC_3, ref.getLocation());

        // W7 setConfigModel：defaultValue 不入 holder——写定后独立于 assigned 代际
        ConfigModel model = new ConfigModel();
        ConfigVarModel varModel = new ConfigVarModel();
        varModel.setName(KEY);
        varModel.setValueType(Object.class);
        varModel.setDefaultValue("dft-new");
        Map<String, ConfigVarModel> varModels = new HashMap<>();
        varModels.put(KEY, varModel);
        model.setVars(varModels);
        provider.setConfigModel(model);

        assertEquals("dft-new", ref.getDefaultValue());
        assertEquals("v-3", ref.get());
        assertEquals(LOC_3, ref.getLocation());
    }
}
