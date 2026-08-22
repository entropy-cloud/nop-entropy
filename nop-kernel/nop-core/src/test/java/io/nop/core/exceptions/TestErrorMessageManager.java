/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.exceptions;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestErrorMessageManager {

    /**
     * new ErrorMessageManager() 会把自己注册为全局错误消息管理器，测试后恢复默认实例
     */
    @AfterEach
    public void restoreGlobalManager() {
        NopException.registerErrorMessageManager(ErrorMessageManager.instance());
    }

    @Test
    public void testClearErrorCodeMappingsAlsoClearsSubMappings() {
        ErrorMessageManager mgr = new ErrorMessageManager();

        ErrorCodeMapping mapping = new ErrorCodeMapping();
        mapping.setStatus(400);

        Map<String, ErrorCodeMapping> mappings = new HashMap<>();
        // 细化映射格式: errorCode?paramName=paramValue
        mappings.put("test.clear-sub-mappings-code?bizFatal=true", mapping);
        mgr.addErrorCodeMappings(mappings);

        Map<String, Object> params = new HashMap<>();
        params.put("bizFatal", "true");
        assertSame(mapping, mgr.getErrorCodeMapping("test.clear-sub-mappings-code", params));

        // 清空映射后细化映射也应被清空
        mgr.clearErrorCodeMappings();
        assertNull(mgr.getErrorCodeMapping("test.clear-sub-mappings-code", params));
    }
}
