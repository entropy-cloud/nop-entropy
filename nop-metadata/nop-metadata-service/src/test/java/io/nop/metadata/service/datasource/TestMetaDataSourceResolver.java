/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service.datasource;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 querySpace→NopMetaDataSource 解析共享组件（架构基线 §4.4 D2）：found / not-found（显式失败） /
 * DISABLED（显式失败） / 多匹配（取首条） 路径，全部显式行为，无静默返回 null。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestMetaDataSourceResolver extends JunitBaseTestCase {

    public TestMetaDataSourceResolver() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IDaoProvider daoProvider;

    private final MetaDataSourceResolver resolver = new MetaDataSourceResolver();

    @Test
    public void testResolveFound() {
        saveDataSource("ds-resolve-ok", "qs_resolve_ok", "jdbc", "ACTIVE");
        IEntityDao<NopMetaDataSource> dsDao = daoProvider.daoFor(NopMetaDataSource.class);
        NopMetaDataSource ds = resolver.resolveActiveOrThrow(dsDao, "qs_resolve_ok");
        assertEquals("ds-resolve-ok", ds.getDataSourceId(), "found datasource must be returned");
        assertEquals("ACTIVE", ds.getStatus(), "returned datasource must be ACTIVE");
    }

    /** querySpace 无匹配 → 显式失败抛 ERR_RESOLVE_NO_DATASOURCE（不静默返回 null）。 */
    @Test
    public void testResolveNotFoundThrows() {
        IEntityDao<NopMetaDataSource> dsDao = daoProvider.daoFor(NopMetaDataSource.class);
        NopException ex = assertThrows(NopException.class,
                () -> resolver.resolveActiveOrThrow(dsDao, "qs_resolve_missing"));
        assertEquals(MetaDataSourceResolver.ERR_RESOLVE_NO_DATASOURCE.getErrorCode(), ex.getErrorCode(),
                "no match must explicitly fail with resolve-not-found");
    }

    /** querySpace 为 null/空 → 显式失败抛 ERR_RESOLVE_NO_DATASOURCE（不静默返回 null）。 */
    @Test
    public void testResolveNullQuerySpaceThrows() {
        IEntityDao<NopMetaDataSource> dsDao = daoProvider.daoFor(NopMetaDataSource.class);
        assertThrows(NopException.class, () -> resolver.resolveActiveOrThrow(dsDao, null));
        assertThrows(NopException.class, () -> resolver.resolveActiveOrThrow(dsDao, "  "));
    }

    /** 匹配到 DISABLED 数据源 → 显式失败抛 ERR_RESOLVE_DATASOURCE_DISABLED（不静默返回 DISABLED 当作可用）。 */
    @Test
    public void testResolveDisabledThrows() {
        saveDataSource("ds-resolve-disabled", "qs_resolve_disabled", "jdbc", "DISABLED");
        IEntityDao<NopMetaDataSource> dsDao = daoProvider.daoFor(NopMetaDataSource.class);
        NopException ex = assertThrows(NopException.class,
                () -> resolver.resolveActiveOrThrow(dsDao, "qs_resolve_disabled"));
        assertEquals(MetaDataSourceResolver.ERR_RESOLVE_DATASOURCE_DISABLED.getErrorCode(), ex.getErrorCode(),
                "DISABLED datasource must explicitly fail with resolve-disabled");
    }

    /** 多个数据源匹配同一 querySpace → 取首条（findFirstByQuery，§4.4 D2，首版不记 warning）。 */
    @Test
    public void testResolveMultiMatchReturnsFirst() {
        saveDataSource("ds-multi-a", "qs_multi", "jdbc", "ACTIVE");
        saveDataSource("ds-multi-b", "qs_multi", "jdbc", "ACTIVE");
        IEntityDao<NopMetaDataSource> dsDao = daoProvider.daoFor(NopMetaDataSource.class);
        // 两条 ACTIVE 数据源匹配同一 querySpace → 解析成功（取首条，非失败）
        NopMetaDataSource ds = resolver.resolveActiveOrThrow(dsDao, "qs_multi");
        assertEquals("qs_multi", ds.getQuerySpace(), "multi-match must resolve to first ACTIVE datasource");
        assertEquals("ACTIVE", ds.getStatus(), "multi-match must resolve to an ACTIVE datasource");
    }

    private void saveDataSource(String id, String querySpace, String datasourceType, String status) {
        IEntityDao<NopMetaDataSource> dao = daoProvider.daoFor(NopMetaDataSource.class);
        NopMetaDataSource ds = dao.newEntity();
        ds.setDataSourceId(id);
        ds.setQuerySpace(querySpace);
        ds.setName(id);
        ds.setDatasourceType(datasourceType);
        ds.setConnectionConfig("{\"jdbcUrl\":\"jdbc:h2:mem:x;DB_CLOSE_DELAY=-1\",\"username\":\"sa\",\"password\":\"\"}");
        ds.setStatus(status);
        ds.setVersion(1L);
        ds.setCreatedBy("autotest");
        ds.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ds.setCreateTime(now);
        ds.setUpdateTime(now);
        dao.saveEntity(ds);
    }
}
