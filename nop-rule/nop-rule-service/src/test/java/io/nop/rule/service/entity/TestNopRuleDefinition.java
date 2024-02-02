/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.service.entity;

import io.nop.api.core.config.AppConfig;
import io.nop.core.CoreConfigs;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.rule.dao.entity.NopRuleDefinition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNopRuleDefinition extends BaseTestCase {
    @BeforeAll
    public static void init() {
        AppConfig.getConfigProvider().updateConfigValue(CoreConfigs.CFG_CORE_MAX_INITIALIZE_LEVEL,
                CoreConstants.INITIALIZER_PRIORITY_ANALYZE);
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * [FIX]: schema.props的类型字段没有被序列化
     */
    @Test
    public void testSerialize() {
        List<Map<String, Object>> inputs = attachmentBean("inputs.json", List.class);
        NopRuleDefinition rule = new NopRuleDefinition();
        rule.setRuleInputs(inputs);
        String xml = rule.getModelTextXmlComponent().getNormalizedXml();
        System.out.println(xml);
        assertTrue(xml.contains("type=\"Long\""));
    }
}
