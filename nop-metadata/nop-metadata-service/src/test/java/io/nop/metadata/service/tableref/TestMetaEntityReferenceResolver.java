package io.nop.metadata.service.tableref;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.service.datasource.MetaDataSourceResolver;
import io.nop.metadata.service.field.MetaEntityFieldResolver;
import io.nop.metadata.service.sqlview.SqlSelectFieldExtractor;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.dao.IOrmEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证共享 table-reference 解析器（架构基线 §4.4 D3）：external/entity/sql 三态 found / not-found（显式失败） /
 * 实体未注册（显式失败）/ DISABLED 路径，全部显式行为，无静默返回 null 或空集。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestMetaEntityReferenceResolver extends JunitBaseTestCase {

    public TestMetaEntityReferenceResolver() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IDaoProvider daoProvider;

    private final MetaEntityReferenceResolver resolver = new MetaEntityReferenceResolver(
            new MetaDataSourceResolver(), new MetaEntityFieldResolver(new SqlSelectFieldExtractor()));

    private IOrmTemplate orm() {
        return ((IOrmEntityDao<NopMetaEntity>) daoProvider.daoFor(NopMetaEntity.class)).getOrmTemplate();
    }

    // ===== external 三态 =====

    @Test
    public void testResolveExternalFound() {
        saveDataSource("ds-ext-ok", "qs_ext_ok", "jdbc", "ACTIVE");
        NopMetaEntity table = saveTable("EXT_T", _NopMetadataCoreConstants.ENTITY_KIND_EXTERNAL, "qs_ext_ok");
        TableReference ref = resolve(table);
        assertEquals(TableReference.Kind.EXTERNAL, ref.getKind(), "external table resolves to EXTERNAL");
        assertNotNull(ref.getDataSource(), "external ref must carry dataSource");
        assertEquals("EXT_T", ref.getPhysicalTableName());
        assertFalse(ref.isSubquery(), "external is not subquery");
    }

    @Test
    public void testResolveExternalDisabledThrows() {
        saveDataSource("ds-ext-disabled", "qs_ext_disabled", "jdbc", "DISABLED");
        NopMetaEntity table = saveTable("EXT_D", _NopMetadataCoreConstants.ENTITY_KIND_EXTERNAL, "qs_ext_disabled");
        assertThrows(NopException.class, () -> resolve(table),
                "DISABLED datasource must explicitly fail (no silent pass)");
    }

    @Test
    public void testResolveExternalNoDataSourceThrows() {
        NopMetaEntity table = saveTable("EXT_NDS", _NopMetadataCoreConstants.ENTITY_KIND_EXTERNAL, "qs_ext_missing");
        assertThrows(NopException.class, () -> resolve(table),
                "missing datasource must explicitly fail (no silent null)");
    }

    // ===== sql 三态 =====

    @Test
    public void testResolveSqlFound() {
        saveDataSource("ds-sql-ok", "qs_sql_ok", "jdbc", "ACTIVE");
        NopMetaEntity table = saveSqlTable("SELECT id, name FROM nop_meta_module", "qs_sql_ok");
        TableReference ref = resolve(table);
        assertEquals(TableReference.Kind.SQL, ref.getKind(), "sql table resolves to SQL");
        assertNotNull(ref.getDataSource(), "sql ref must carry dataSource");
        assertNotNull(ref.getSourceSql(), "sql ref must carry sourceSql");
        assertTrue(ref.isSubquery(), "sql is subquery");
        assertNotNull(ref.getFields(), "sql ref must carry AST-parsed fields");
        assertEquals(2, ref.getFields().size(), "fields count must match SELECT output");
    }

    @Test
    public void testResolveSqlNoDataSourceThrows() {
        NopMetaEntity table = saveSqlTable("SELECT id FROM nop_meta_module", "qs_sql_missing");
        assertThrows(NopException.class, () -> resolve(table),
                "missing datasource for sql table must explicitly fail");
    }

    @Test
    public void testResolveSqlEmptySourceThrows() {
        saveDataSource("ds-sql-empty", "qs_sql_empty", "jdbc", "ACTIVE");
        NopMetaEntity table = saveSqlTable("  ", "qs_sql_empty");
        assertThrows(NopException.class, () -> resolve(table),
                "empty sourceSql must explicitly fail (no silent null)");
    }

    // ===== entity 三态 =====

    @Test
    public void testResolveEntityFound() {
        // 使用平台已注册实体 io.nop.metadata.dao.entity.NopMetaModule 作为 fixture（本模块自身实体，已注册于运行时）
        NopMetaEntity entity = saveMetaEntity("io.nop.metadata.dao.entity.NopMetaModule", "NOP_META_MODULE", "default");
        NopMetaEntity table = saveEntityTable(entity.getMetaEntityId());
        TableReference ref = resolve(table);
        assertEquals(TableReference.Kind.ENTITY, ref.getKind(), "entity table resolves to ENTITY");
        assertNotNull(ref.getEntity(), "entity ref must carry NopMetaEntity");
        assertEquals("NOP_META_MODULE", ref.getPhysicalTableName(), "physicalTableName from entity.tableName");
        assertEquals("default", ref.getPlatformQuerySpace(), "platformQuerySpace from entity.querySpace");
        assertFalse(ref.isSubquery(), "entity is not subquery");
    }

    @Test
    public void testResolveEntityNameMissingThrows() {
        // plan 2261 概念缩减：原"entity 表 baseEntityId 为 null"状态随表/实体归并消失，
        // 等价失败态 = PHYSICAL 实体缺 entityName → 解析显式失败（不静默空集）。
        // 内存对象即可（resolver 只读传入对象），不落库以免触发 entityName mandatory 约束。
        NopMetaEntity table = new NopMetaEntity();
        table.setMetaEntityId("ent-null-" + System.nanoTime());
        table.setEntityKind(_NopMetadataCoreConstants.ENTITY_KIND_PHYSICAL);
        table.setTableName("ENT_NULL");
        table.setDisplayName("ent-null");
        assertThrows(NopException.class, () -> resolve(table),
                "missing entityName must explicitly fail (no silent empty set)");
    }

    @Test
    public void testResolveEntityNotRegisteredThrows() {
        // 实体名不在运行时 IOrmSessionFactory 注册表 → isValidEntityName=false → 显式失败
        NopMetaEntity entity = saveMetaEntity("not.a.registered.entity", "NOP_FAKE", "default");
        NopMetaEntity table = saveEntityTable(entity.getMetaEntityId());
        assertThrows(NopException.class, () -> resolve(table),
                "unregistered entity must explicitly fail (no silent empty set)");
    }

    @Test
    public void testResolveEntityTableNameEmptyThrows() {
        // 实体已注册但 tableName 为空 → 显式失败。
        // 内存对象即可（resolver 只读传入对象），不落库——避免污染 saveMetaEntity 按
        // entityName 复用的共享 fixture 行（plan 2261 后实体行自身即解析目标）。
        NopMetaEntity table = new NopMetaEntity();
        table.setMetaEntityId("ent-empty-tbl-" + System.nanoTime());
        table.setEntityKind(_NopMetadataCoreConstants.ENTITY_KIND_PHYSICAL);
        table.setEntityName("io.nop.metadata.dao.entity.NopMetaModule");
        table.setTableName("");
        table.setQuerySpace("default");
        assertThrows(NopException.class, () -> resolve(table),
                "empty entity.tableName must explicitly fail");
    }

    // ===== 未知 entityKind =====

    @Test
    public void testResolveUnknownEntityKindThrows() {
        NopMetaEntity table = saveTable("UNK_T", "weird-type", "qs_unknown");
        assertThrows(NopException.class, () -> resolve(table),
                "unknown entityKind must explicitly fail (no silent skip)");
    }

    // ===== helpers =====

    private TableReference resolve(NopMetaEntity table) {
        return resolver.resolve(table,
                daoProvider.daoFor(NopMetaDataSource.class),
                daoProvider.daoFor(NopMetaEntity.class),
                daoProvider.daoFor(NopMetaEntityField.class),
                orm());
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

    private String ensureModuleId() {
        IEntityDao<NopMetaModule> dao = daoProvider.daoFor(NopMetaModule.class);
        // MR3 P2-MA6.6-001：UK 发射后 moduleId+version 有 DB 唯一约束，重复调用必须幂等（查重后插入）
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaModule.PROP_NAME_moduleId, "nop/test-resolver"));
        NopMetaModule existing = dao.findFirstByQuery(q);
        if (existing != null) {
            return existing.getMetaModuleId();
        }
        NopMetaModule m = dao.newEntity();
        m.setModuleId("nop/test-resolver");
        m.setModuleName("test-resolver");
        m.setDisplayName("test");
        m.setModuleVersion(1L);
        m.setStatus(_NopMetadataCoreConstants.MODULE_STATUS_RELEASED);
        m.setImportedAt(new Timestamp(System.currentTimeMillis()));
        dao.saveEntity(m);
        return m.getMetaModuleId();
    }

    private NopMetaEntity saveTable(String tableName, String entityKind, String querySpace) {
        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity t = dao.newEntity();
        t.setMetaModuleId(ensureModuleId());
        t.setOrmModelId("orm_" + tableName + "_" + System.nanoTime());
        t.setIsDelta((byte) 0);
        t.setEntityName(tableName);
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setEntityKind(entityKind);
        if (querySpace != null) {
            t.setQuerySpace(querySpace);
        }
        t.setVersion(1L);
        dao.saveEntity(t);
        return t;
    }

    private NopMetaEntity saveSqlTable(String sourceSql, String querySpace) {
        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        String tableName = "SQL_V_" + System.nanoTime();
        NopMetaEntity t = dao.newEntity();
        t.setMetaModuleId(ensureModuleId());
        t.setOrmModelId("orm_" + tableName);
        t.setIsDelta((byte) 0);
        t.setEntityName(tableName);
        t.setTableName(tableName);
        t.setDisplayName("sql-view");
        t.setEntityKind(_NopMetadataCoreConstants.ENTITY_KIND_SQL_VIEW);
        t.setQuerySpace(querySpace);
        t.setSourceSql(sourceSql);
        t.setVersion(1L);
        dao.saveEntity(t);
        return t;
    }

    private NopMetaEntity saveMetaEntity(String entityName, String tableName, String querySpace) {
        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        // UK_NOP_META_ENTITY_MODEL_NAME（ormModelId,entityName）发射后需幂等：同一 entityName 复用已有行
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaEntity.PROP_NAME_entityName, entityName));
        NopMetaEntity existing = dao.findFirstByQuery(q);
        if (existing != null) {
            return existing;
        }
        NopMetaEntity e = dao.newEntity();
        e.setMetaModuleId(ensureModuleId());
        e.setOrmModelId(ensureOrmModelId());
        e.setIsDelta((byte) 0);
        e.setEntityKind(_NopMetadataCoreConstants.ENTITY_KIND_PHYSICAL);
        e.setEntityName(entityName);
        e.setDisplayName(entityName);
        e.setTableName(tableName);
        e.setQuerySpace(querySpace);
        e.setVersion(1L);
        dao.saveEntity(e);
        return e;
    }

    private String ensureOrmModelId() {
        IEntityDao<io.nop.metadata.dao.entity.NopMetaOrmModel> dao =
                daoProvider.daoFor(io.nop.metadata.dao.entity.NopMetaOrmModel.class);
        // UK_NOP_META_ORM_MODEL_MODULE_NAME（metaModuleId,modelName,isDelta）发射后需幂等
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(io.nop.metadata.dao.entity.NopMetaOrmModel.PROP_NAME_modelName, "test-model"));
        io.nop.metadata.dao.entity.NopMetaOrmModel existing = dao.findFirstByQuery(q);
        if (existing != null) {
            return existing.getOrmModelId();
        }
        io.nop.metadata.dao.entity.NopMetaOrmModel m = dao.newEntity();
        m.setMetaModuleId(ensureModuleId());
        m.setModelName("test-model");
        m.setIsDelta((byte) 0);
        m.setVersion(1L);
        dao.saveEntity(m);
        return m.getOrmModelId();
    }

    private NopMetaEntity saveEntityTable(String baseEntityId) {
        // plan 2261 概念缩减：实体行自身即解析目标（原独立"表行→baseEntityId"已删除）
        return daoProvider.daoFor(NopMetaEntity.class).getEntityById(baseEntityId);
    }
}
