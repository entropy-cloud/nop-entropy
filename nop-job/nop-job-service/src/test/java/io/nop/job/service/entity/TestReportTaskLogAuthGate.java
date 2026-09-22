package io.nop.job.service.entity;

import io.nop.core.resource.VirtualFileSystem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-WF-03-1 回归：reportTaskLog 必须携带 admin 鉴权声明（修复前
 * 任意登录用户可向任意 taskId 注入伪造日志）。
 */
public class TestReportTaskLogAuthGate  {

    @BeforeAll
    public static void init() {
        // 只初始化到 RESOURCE 层（不含 IoC/数据源），足以读 VFS 中的 xbiz 文件
        io.nop.core.initialize.CoreInitialization.initializeTo(io.nop.core.CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    public static void destroy() {
        io.nop.core.initialize.CoreInitialization.destroy();
    }

    @Test
    public void testReportTaskLogCarriesAuthDeclaration() {
        String xml = VirtualFileSystem.instance()
                .getResource("/nop/job/model/NopJobTaskLog/NopJobTaskLog.xbiz").readText();
        assertTrue(xml.contains("mutation name=\"reportTaskLog\""),
                "reportTaskLog action must be declared in the retained xbiz: " + xml);
        assertTrue(xml.contains("<auth roles=\"admin\" permissions=\"NopJobTaskLog:reportTaskLog\"/>"),
                "reportTaskLog must carry the admin auth gate (F-WF-03-1)");
    }
}
