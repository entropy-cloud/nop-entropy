/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xui.initialize;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * NopAuthResource.icon 的 schema 声明了 stdDomain="icon"（用于驱动 edit-icon/view-icon 控件），
 * 但该标准域此前未注册，导致任何带 icon 的保存都被 {@code SimpleSchemaValidator} 以
 * {@code unknown-std-domain} 拒绝——图标选择在 UI 上"能选不能存"。本测试钉住该标准域已注册。
 */
public class TestIconStdDomain extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testIconStdDomainRegistered() {
        IStdDomainHandler handler = StdDomainRegistry.instance().getStdDomainHandler(IconStdDomainHandler.STD_DOMAIN_ICON);
        assertNotNull(handler, "icon 标准域必须注册，否则 NopAuthResource.icon 保存报 unknown-std-domain");
        assertEquals(IconStdDomainHandler.STD_DOMAIN_ICON, handler.getName());
        assertEquals(PredefinedGenericTypes.STRING_TYPE, handler.getGenericType(false, null));
    }
}
