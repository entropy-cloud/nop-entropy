package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.sync.ExternalTableStructureReader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2026-07-19-1250-3 Phase 2 Proof：验证 ErrorCode 集中化与模块异常类。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@link NopMetadataErrors} 常量集中化（a 部分）</li>
 *   <li>{@link NopMetadataException} 两构造器可用（b 部分）</li>
 *   <li>{@link ExternalTableStructureReader#requireSupportedProductName} 不再抛
 *       {@link UnsupportedOperationException}，改为 {@link NopException}（c 部分）</li>
 *   <li>ARG_* 参数常量已引入（d 部分）</li>
 * </ul>
 */
public class TestNopMetadataErrorsCentralized {

    /**
     * 验证 {@link NopMetadataErrors} 中的 ErrorCode 常量都是从 NopMetadataErrors 引用而非内联。
     *
     * <p>P2-35 项 4a（plan 2026-08-16-0549-2）：抽样断言用**字面量**作为独立真值（expected 值不与被测常量
     * 同源——修复前构造器测试以 {@code CONSTANT.getErrorCode()} 断言同一常量的传播，恒真镜像）；
     * 死码面（define 无生产消费者）由既有门禁 {@code check-error-param-consistency.mjs} define 面规则守护
     * （P2-10，plan 2026-08-16-0226-2），本测试不重复该面。
     */
    @Test
    public void testCentralizedErrorCodesDefined() {
        // 跨文件去重 ErrorCode：抽样与独立字面量真值逐一核对
        assertNotNull(NopMetadataErrors.ERR_DATASOURCE_NOT_FOUND);
        assertEquals("nop.err.metadata.datasource-not-found",
                NopMetadataErrors.ERR_DATASOURCE_NOT_FOUND.getErrorCode());

        assertNotNull(NopMetadataErrors.ERR_JOIN_TABLE_TYPE_NOT_ALLOWED);
        assertTrue(NopMetadataErrors.ERR_JOIN_TABLE_TYPE_NOT_ALLOWED.getErrorCode()
                .startsWith("nop.err.metadata."));

        // 模块异常辅助 ErrorCode：独立字面量真值核对（不止 assertNotNull）
        assertEquals("nop.err.metadata.datasource-type-not-supported",
                NopMetadataErrors.ERR_DATASOURCE_TYPE_NOT_SUPPORTED.getErrorCode());
        assertEquals("nop.err.metadata.orm-resource-not-found",
                NopMetadataErrors.ERR_ORM_RESOURCE_NOT_FOUND.getErrorCode());
        assertEquals("nop.err.metadata.quality-expect-pass-when-invalid",
                NopMetadataErrors.ERR_QUALITY_EXPECT_PASS_WHEN_INVALID.getErrorCode());
    }

    /** 验证 ARG_* 参数常量已引入，避免魔法字符串。 */
    @Test
    public void testArgConstantsIntroduced() {
        assertEquals("metaTableId", NopMetadataErrors.ARG_META_TABLE_ID);
        assertEquals("dataSourceId", NopMetadataErrors.ARG_DATA_SOURCE_ID);
        assertEquals("joinId", NopMetadataErrors.ARG_JOIN_ID);
        assertEquals("configId", NopMetadataErrors.ARG_CONFIG_ID);
        assertEquals("checkpointId", NopMetadataErrors.ARG_CHECKPOINT_ID);
        assertEquals("qualityRuleId", NopMetadataErrors.ARG_QUALITY_RULE_ID);
        assertEquals("error", NopMetadataErrors.ARG_ERROR);
    }

    /**
     * 验证所有 ErrorCode 都以 {@code nop.err.metadata.} 前缀（plan 维度09-01 命名规范）。
     *
     * <p>P2-35 项 4b（plan 2026-08-16-0549-2）：接口清单由**源码目录动态枚举**发现（扫
     * {@code src/main/java/io/nop/metadata/service/} 下全部 {@code *Errors.java} 并加载），
     * 封 F19 族盲区——新增 {@code *Errors} 接口未纳入扫描即红；同时断言发现的每个接口都
     * 已接入 {@link NopMetadataErrors} 聚合门面（未接线的新接口即红，保证集中化不旁路）。
     */
    @Test
    public void testAllErrorsUseNopErrPrefix() throws Exception {
        Set<Class<?>> errorInterfaces = discoverErrorInterfacesFromSource();
        assertTrue(errorInterfaces.size() >= 10,
                "dynamically discovered *Errors interfaces must be >= 10 (live 2026-08-16), but was "
                        + errorInterfaces.size());

        // 集中化接线核对：发现的每个 *Errors 接口（聚合门面自身除外）都必须是 NopMetadataErrors 的父接口
        Set<String> facadeSupers = new HashSet<>();
        for (Class<?> iface : NopMetadataErrors.class.getInterfaces()) {
            facadeSupers.add(iface.getSimpleName());
        }
        for (Class<?> cls : errorInterfaces) {
            if (cls == NopMetadataErrors.class) {
                continue;
            }
            assertTrue(facadeSupers.contains(cls.getSimpleName()),
                    "interface " + cls.getSimpleName() + " must be wired into NopMetadataErrors "
                            + "(facade supers: " + facadeSupers + ")");
        }

        Set<String> allCodes = new HashSet<>();
        for (Class<?> cls : errorInterfaces) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType() == ErrorCode.class) {
                    try {
                        ErrorCode ec = (ErrorCode) f.get(null);
                        allCodes.add(ec.getErrorCode());
                    } catch (IllegalAccessException e) {
                        throw NopException.adapt(e);
                    }
                }
            }
        }
        assertTrue(!allCodes.isEmpty(), "ErrorCode interfaces must declare ErrorCode constants");
        for (String code : allCodes) {
            assertTrue(code.startsWith("nop.err.metadata."),
                    "ErrorCode must use nop.err.metadata.* prefix: " + code);
        }
    }

    /**
     * 源码目录动态枚举 {@code *Errors.java}：定位 {@code io.nop.metadata.service} 源目录
     * （surefire 工作目录 = service 模块 basedir 时取 {@code src/main/java/...}；仓库根执行时回退
     * {@code nop-metadata/nop-metadata-service/src/main/java/...}），加载全部 {@code *Errors} 类。
     */
    private Set<Class<?>> discoverErrorInterfacesFromSource() throws Exception {
        String rel = "src/main/java/io/nop/metadata/service";
        java.io.File dir = new java.io.File(rel);
        if (!dir.exists()) {
            dir = new java.io.File("nop-metadata/nop-metadata-service/" + rel);
        }
        assertTrue(dir.isDirectory(), "*Errors source dir must exist: " + dir.getAbsolutePath());
        java.io.File[] files = dir.listFiles((d, name) -> name.matches("\\w+Errors\\.java"));
        assertNotNull(files, "*Errors source dir must be listable: " + dir.getAbsolutePath());
        Set<Class<?>> result = new HashSet<>();
        for (java.io.File f : files) {
            String simpleName = f.getName().substring(0, f.getName().length() - ".java".length());
            result.add(Class.forName("io.nop.metadata.service." + simpleName));
        }
        return result;
    }

    /**
     * 验证 {@link NopMetadataException} 两构造器可用。
     *
     * <p>P2-35 项 4a：expected 用独立字面量真值（修复前 {@code assertEquals(CONSTANT.getErrorCode(),
     * e3.getErrorCode())} 与被测物同源恒真——构造器只要回存入参就永远通过，无区分力）。
     */
    @Test
    public void testNopMetadataExceptionConstructors() {
        // (ErrorCode)
        NopMetadataException e3 = new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_NOT_FOUND);
        assertEquals("nop.err.metadata.datasource-not-found", e3.getErrorCode());

        // (ErrorCode, Throwable)
        NopMetadataException e4 = new NopMetadataException(
                NopMetadataErrors.ERR_ORM_RESOURCE_NOT_FOUND, new RuntimeException("io fail"));
        assertEquals("nop.err.metadata.orm-resource-not-found", e4.getErrorCode());
        assertNotNull(e4.getCause());
    }

    /**
     * 验证 {@link ExternalTableStructureReader#requireSupportedProductName} 抛 {@link NopException}
     * 而非 {@link UnsupportedOperationException}（plan 维度09-07）。
     *
     * <p>注：{@code requireSupportedProductName} 为 package-private，跨包测试需放于
     * {@code io.nop.metadata.service.sync} 包内（见 {@code sync.TestExternalTableStructureReader}）。
     * 此处仅验证 ErrorCode 常量本身已迁移到 {@link NopMetadataErrors}：
     */
    @Test
    public void testExternalReaderThrowsNopExceptionNotUnsupported() {
        // 直接验证 ERR_DATASOURCE_TYPE_NOT_SUPPORTED 已 centralize 到 NopMetadataErrors
        assertNotNull(NopMetadataErrors.ERR_DATASOURCE_TYPE_NOT_SUPPORTED);
        assertTrue(NopMetadataErrors.ERR_DATASOURCE_TYPE_NOT_SUPPORTED.getErrorCode()
                .startsWith("nop.err.metadata."));
    }

    /** 验证 {@link NopMetadataErrors#ERR_QUALITY_EXPECT_PASS_WHEN_INVALID} 已定义（plan Phase 5 AR-11 前置）。 */
    @Test
    public void testQualityExpectPassWhenErrorCodeDefined() {
        assertNotNull(NopMetadataErrors.ERR_QUALITY_EXPECT_PASS_WHEN_INVALID);
        assertTrue(NopMetadataErrors.ERR_QUALITY_EXPECT_PASS_WHEN_INVALID.getErrorCode()
                .contains("expect-pass-when"));
    }
}
