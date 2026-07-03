package io.nop.codegen;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.config.AppConfig;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 use-approval codegen 模板包含条件 extends。
 * 注：本测试只验证模板文件内容包含正确的条件判断标记，
 * 不运行 XCodeGenerator 引擎（路径模式复杂，需要真实 codegen 项目环境）。
 */
public class TestUseApprovalCodegen extends BaseTestCase {

    @BeforeAll
    public static void setUp() {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void tearDown() {
        CoreInitialization.destroy();
    }

    @Test
    public void testXbizTemplateHasUseApprovalCondition() {
        String path = "/nop/templates/orm/{appName}-service/src/main/resources/_vfs/{moduleId}/model/{!entityModel.notGenCode}{entityModel.shortName}/_{entityModel.shortName}.xbiz.xgen";
        IResource r = VirtualFileSystem.instance().getResource(path);
        assertNotNull(r, "xbiz template should exist: " + path);
        assertTrue(r.exists(), "xbiz template should exist");

        String text = ResourceHelper.readText(r);
        // Verify the condition exists
        assertTrue(text.contains("containsTag('use-approval')"),
                "xbiz template must reference use-approval tag for x:extends");
        assertTrue(text.contains("approval-support.xbiz"),
                "xbiz template must reference approval-support.xbiz");
        assertTrue(text.contains("containsTag('not-pub')"),
                "xbiz template must still have not-pub condition");
    }

    @Test
    public void testIBizTemplateHasUseApprovalCondition() {
        String path = "/nop/templates/orm/{appName}-dao/src/main/java/{basePackagePath}/biz/{!entityModel.notGenCode}I{entityModel.shortName}Biz.java.xgen";
        IResource r = VirtualFileSystem.instance().getResource(path);
        assertNotNull(r, "IBiz template should exist: " + path);
        assertTrue(r.exists(), "IBiz template should exist");

        String text = ResourceHelper.readText(r);
        assertTrue(text.contains("containsTag('use-approval')"),
                "IBiz template must reference use-approval tag");
        assertTrue(text.contains("IApprovableBiz"),
                "IBiz template must reference IApprovableBiz");
    }
}
