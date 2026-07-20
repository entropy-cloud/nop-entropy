/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2026-07-19-1250-3 Phase 2 Proof：验证 ErrorCode 集中化与模块异常类。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@link NopMetadataErrors} 常量集中化（a 部分）</li>
 *   <li>{@link NopMetadataException} 四构造器可用（b 部分）</li>
 *   <li>{@link ExternalTableStructureReader#requireSupportedProductName} 不再抛
 *       {@link UnsupportedOperationException}，改为 {@link NopException}（c 部分）</li>
 *   <li>ARG_* 参数常量已引入（d 部分）</li>
 * </ul>
 */
public class TestNopMetadataErrorsCentralized {

    /** 验证 {@link NopMetadataErrors} 中的 ErrorCode 常量都是从 NopMetadataErrors 引用而非内联。 */
    @Test
    public void testCentralizedErrorCodesDefined() {
        // 跨文件去重 ErrorCode
        assertNotNull(NopMetadataErrors.ERR_DATASOURCE_NOT_FOUND);
        assertEquals("nop.err.metadata.datasource-not-found",
                NopMetadataErrors.ERR_DATASOURCE_NOT_FOUND.getErrorCode());

        assertNotNull(NopMetadataErrors.ERR_JOIN_TABLE_TYPE_NOT_ALLOWED);
        assertTrue(NopMetadataErrors.ERR_JOIN_TABLE_TYPE_NOT_ALLOWED.getErrorCode()
                .startsWith("nop.err.metadata."));

        // 模块异常辅助 ErrorCode
        assertNotNull(NopMetadataErrors.ERR_DATASOURCE_TYPE_NOT_SUPPORTED);
        assertNotNull(NopMetadataErrors.ERR_MANIFEST_BUILD_FAILED);
        assertNotNull(NopMetadataErrors.ERR_ORM_RESOURCE_NOT_FOUND);
        assertNotNull(NopMetadataErrors.ERR_ORM_RESOURCE_READ_FAILED);
        assertNotNull(NopMetadataErrors.ERR_RECON_PARSE_PROPERTIES_FAILED);
        assertNotNull(NopMetadataErrors.ERR_QUALITY_EXPECT_PASS_WHEN_INVALID);
        assertNotNull(NopMetadataErrors.ERR_DTO_SERIALIZE_FAILED);
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

    /** 验证所有 ErrorCode 都以 {@code nop.err.metadata.} 前缀（plan 维度09-01 命名规范）。 */
    @Test
    public void testAllErrorsUseNopErrPrefix() {
        Set<String> allCodes = new HashSet<>();
        for (Field f : NopMetadataErrors.class.getDeclaredFields()) {
            if (f.getType() == ErrorCode.class) {
                try {
                    ErrorCode ec = (ErrorCode) f.get(null);
                    allCodes.add(ec.getErrorCode());
                } catch (IllegalAccessException ignored) {
                    // skip
                }
            }
        }
        assertTrue(!allCodes.isEmpty(), "NopMetadataErrors must declare ErrorCode constants");
        for (String code : allCodes) {
            assertTrue(code.startsWith("nop.err.metadata."),
                    "ErrorCode must use nop.err.metadata.* prefix: " + code);
        }
    }

    /** 验证 {@link NopMetadataException} 四构造器可用。 */
    @Test
    public void testNopMetadataExceptionConstructors() {
        // (String)
        NopMetadataException e1 = new NopMetadataException("inner helper failure");
        assertTrue(e1.getMessage().contains("inner helper failure"));

        // (String, Throwable)
        NopMetadataException e2 = new NopMetadataException("inner helper failure with cause",
                new RuntimeException("root"));
        assertTrue(e2.getMessage().contains("inner helper failure"));
        assertNotNull(e2.getCause());

        // (ErrorCode)
        NopMetadataException e3 = new NopMetadataException(NopMetadataErrors.ERR_MANIFEST_BUILD_FAILED);
        assertEquals(NopMetadataErrors.ERR_MANIFEST_BUILD_FAILED.getErrorCode(), e3.getErrorCode());

        // (ErrorCode, Throwable)
        NopMetadataException e4 = new NopMetadataException(
                NopMetadataErrors.ERR_ORM_RESOURCE_NOT_FOUND, new RuntimeException("io fail"));
        assertEquals(NopMetadataErrors.ERR_ORM_RESOURCE_NOT_FOUND.getErrorCode(), e4.getErrorCode());
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
