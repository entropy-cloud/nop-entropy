package io.nop.auth.web.page;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.config.AppConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.web.WebConfigs;
import io.nop.web.page.PageProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归：NopAuthOpLog 实体本身有名为 {@code operation} 的数据列（业务操作），框架操作按钮列若也用
 * {@code name="operation"} 会与之同名——flux 表格按 name 合并/覆盖列，表现为"操作列出现多个"。
 * 操作列必须使用保留名 {@code __operation}，且整页列名唯一。
 */
@NopTestConfig
public class TestNopAuthOpLogColumns extends JunitBaseTestCase {

    @Inject
    PageProvider pageProvider;

    @BeforeEach
    public void setUpConfig() {
        AppConfig.getConfigProvider().updateConfigValue(WebConfigs.CFG_WEB_RENDER_MODE, "flux");
        ResourceComponentManager.instance().clearCache("xlib");
    }

    @AfterEach
    public void tearDownConfig() {
        AppConfig.getConfigProvider().updateConfigValue(WebConfigs.CFG_WEB_RENDER_MODE, "amis");
        ResourceComponentManager.instance().clearCache("xlib");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNoDuplicateColumnName() {
        Map<String, Object> page = pageProvider.getPage("/nop/auth/pages/NopAuthOpLog/main.page.yaml", null);
        Map<String, Object> body = (Map<String, Object>) page.get("body");
        List<Object> columns = (List<Object>) body.get("columns");

        Set<String> names = new HashSet<>();
        boolean hasOperationColumn = false;
        for (Object col : columns) {
            Object name = ((Map<String, Object>) col).get("name");
            if (name == null)
                continue;
            assertTrue(names.add((String) name), "duplicate column name in generated page: " + name);
            if ("__operation".equals(name))
                hasOperationColumn = true;
        }
        assertTrue(hasOperationColumn, "row-action column must use reserved name __operation");
    }
}
