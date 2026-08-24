/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.xlang.xpl.impl.XplTaskLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_COMPONENT_PARSE_MISSING_RESOURCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestXplTaskLoader {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * 空任务文件（无法产出可执行表达式）应抛出带资源路径的 NopException，
     * 而不是在 invoke 处抛裸 NPE
     */
    @Test
    public void testEmptyTaskFileThrowsWithPath() {
        XplTaskLoader loader = new XplTaskLoader();
        NopException e = assertThrows(NopException.class,
                () -> loader.loadObjectFromPath("/test/empty-task.xtask"));
        assertEquals(ERR_COMPONENT_PARSE_MISSING_RESOURCE.getErrorCode(), e.getErrorCode());
        assertEquals("/test/empty-task.xtask", e.getParam(ARG_RESOURCE_PATH));
    }
}
