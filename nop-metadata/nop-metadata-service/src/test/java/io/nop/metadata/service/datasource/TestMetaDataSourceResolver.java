package io.nop.metadata.service.datasource;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.orm.IOrmSessionFactory;
import io.nop.core.lang.sql.SQL;
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

    @Inject
    IOrmSessionFactory ormFactory;

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
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_RESOLVE_NO_DATASOURCE.getErrorCode(), ex.getErrorCode(),
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
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_RESOLVE_DISABLED.getErrorCode(), ex.getErrorCode(),
                "DISABLED datasource must explicitly fail with resolve-disabled");
    }

    /**
     * AR-03: 多个数据源匹配同一 querySpace（历史数据违反 ORM 层 UK_NOP_META_DS_QUERY_SPACE）→
     * 显式失败抛 ERR_DATASOURCE_DUPLICATE_QUERY_SPACE（拒绝取首条，防路由劫持）。
     *
     * <p>MR3 P2-MA6.6-001 后 DB 真实发射 UK 约束，无法再经 DAO 插入同 querySpace 两行；
     * 本测试先 DROP 该 UK 模拟"UK 引入前的历史数据"，插入两行后恢复约束，再验证 runtime 兜底检测生效。
     */
    @Test
    public void testResolveMultiMatchThrowsDuplicate() {
        // 模拟历史库（无 UK）：先移除约束再插入同 querySpace 两行
        ormFactory.getJdbcTemplate().executeUpdate(SQL.begin()
                .sql("ALTER TABLE NOP_META_DATA_SOURCE DROP CONSTRAINT UK_NOP_META_DS_QUERY_SPACE")
                .end());
        try {
            saveDataSource("ds-multi-a", "qs_multi", "jdbc", "ACTIVE");
            saveDataSource("ds-multi-b", "qs_multi", "jdbc", "ACTIVE");
            IEntityDao<NopMetaDataSource> dsDao = daoProvider.daoFor(NopMetaDataSource.class);
            NopException ex = assertThrows(NopException.class,
                    () -> resolver.resolveActiveOrThrow(dsDao, "qs_multi"),
                    "multi-match querySpace must explicitly fail with ERR_DATASOURCE_DUPLICATE_QUERY_SPACE");
            assertEquals(NopMetadataErrors.ERR_DATASOURCE_DUPLICATE_QUERY_SPACE.getErrorCode(),
                    ex.getErrorCode(),
                    "multi-match must explicitly fail with ERR_DATASOURCE_DUPLICATE_QUERY_SPACE");
            assertEquals("qs_multi", ex.getParam("querySpace"));
            assertEquals(2, ex.getParam("dataSourceCount"),
                    "dataSourceCount must be 2 for multi-match");
        } finally {
            // 先删除重复行（UK 不允许重复数据存在）再恢复约束
            ormFactory.getJdbcTemplate().executeUpdate(SQL.begin()
                    .sql("DELETE FROM NOP_META_DATA_SOURCE WHERE QUERY_SPACE = 'qs_multi'")
                    .end());
            ormFactory.getJdbcTemplate().executeUpdate(SQL.begin()
                    .sql("ALTER TABLE NOP_META_DATA_SOURCE ADD CONSTRAINT UK_NOP_META_DS_QUERY_SPACE UNIQUE (QUERY_SPACE)")
                    .end());
        }
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
