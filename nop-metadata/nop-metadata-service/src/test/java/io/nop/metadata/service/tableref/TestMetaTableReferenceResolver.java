package io.nop.metadata.service.tableref;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.datasource.MetaDataSourceResolver;
import io.nop.metadata.service.field.MetaTableFieldResolver;
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
public class TestMetaTableReferenceResolver extends JunitBaseTestCase {

    public TestMetaTableReferenceResolver() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IDaoProvider daoProvider;

    private final MetaTableReferenceResolver resolver = new MetaTableReferenceResolver(
            new MetaDataSourceResolver(), new MetaTableFieldResolver(new SqlSelectFieldExtractor()));

    private IOrmTemplate orm() {
        return ((IOrmEntityDao<NopMetaTable>) daoProvider.daoFor(NopMetaTable.class)).getOrmTemplate();
    }

    // ===== external 三态 =====

    @Test
    public void testResolveExternalFound() {
        saveDataSource("ds-ext-ok", "qs_ext_ok", "jdbc", "ACTIVE");
        NopMetaTable table = saveTable("EXT_T", _NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL, "qs_ext_ok");
        TableReference ref = resolve(table);
        assertEquals(TableReference.Kind.EXTERNAL, ref.getKind(), "external table resolves to EXTERNAL");
        assertNotNull(ref.getDataSource(), "external ref must carry dataSource");
        assertEquals("EXT_T", ref.getPhysicalTableName());
        assertFalse(ref.isSubquery(), "external is not subquery");
    }

    @Test
    public void testResolveExternalDisabledThrows() {
        saveDataSource("ds-ext-disabled", "qs_ext_disabled", "jdbc", "DISABLED");
        NopMetaTable table = saveTable("EXT_D", _NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL, "qs_ext_disabled");
        assertThrows(NopException.class, () -> resolve(table),
                "DISABLED datasource must explicitly fail (no silent pass)");
    }

    @Test
    public void testResolveExternalNoDataSourceThrows() {
        NopMetaTable table = saveTable("EXT_NDS", _NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL, "qs_ext_missing");
        assertThrows(NopException.class, () -> resolve(table),
                "missing datasource must explicitly fail (no silent null)");
    }

    // ===== sql 三态 =====

    @Test
    public void testResolveSqlFound() {
        saveDataSource("ds-sql-ok", "qs_sql_ok", "jdbc", "ACTIVE");
        NopMetaTable table = saveSqlTable("SELECT id, name FROM nop_meta_module", "qs_sql_ok");
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
        NopMetaTable table = saveSqlTable("SELECT id FROM nop_meta_module", "qs_sql_missing");
        assertThrows(NopException.class, () -> resolve(table),
                "missing datasource for sql table must explicitly fail");
    }

    @Test
    public void testResolveSqlEmptySourceThrows() {
        saveDataSource("ds-sql-empty", "qs_sql_empty", "jdbc", "ACTIVE");
        NopMetaTable table = saveSqlTable("  ", "qs_sql_empty");
        assertThrows(NopException.class, () -> resolve(table),
                "empty sourceSql must explicitly fail (no silent null)");
    }

    // ===== entity 三态 =====

    @Test
    public void testResolveEntityFound() {
        // 使用平台已注册实体 io.nop.metadata.dao.entity.NopMetaModule 作为 fixture（本模块自身实体，已注册于运行时）
        NopMetaEntity entity = saveMetaEntity("io.nop.metadata.dao.entity.NopMetaModule", "NOP_META_MODULE", "default");
        NopMetaTable table = saveEntityTable(entity.getMetaEntityId());
        TableReference ref = resolve(table);
        assertEquals(TableReference.Kind.ENTITY, ref.getKind(), "entity table resolves to ENTITY");
        assertNotNull(ref.getEntity(), "entity ref must carry NopMetaEntity");
        assertEquals("NOP_META_MODULE", ref.getPhysicalTableName(), "physicalTableName from entity.tableName");
        assertEquals("default", ref.getPlatformQuerySpace(), "platformQuerySpace from entity.querySpace");
        assertFalse(ref.isSubquery(), "entity is not subquery");
    }

    @Test
    public void testResolveEntityBaseNullThrows() {
        // entity 表 baseEntityId 为 null → 显式失败（不静默空集）
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable table = dao.newEntity();
        table.setMetaModuleId(ensureModuleId());
        table.setTableName("ENT_NULL");
        table.setDisplayName("ent-null");
        table.setTableType(_NopMetadataCoreConstants.TABLE_TYPE_ENTITY);
        table.setBaseEntityId(null);
        table.setVersion(1L);
        dao.saveEntity(table);
        assertThrows(NopException.class, () -> resolve(table),
                "null baseEntityId must explicitly fail (no silent empty set)");
    }

    @Test
    public void testResolveEntityNotRegisteredThrows() {
        // 实体名不在运行时 IOrmSessionFactory 注册表 → isValidEntityName=false → 显式失败
        NopMetaEntity entity = saveMetaEntity("not.a.registered.entity", "NOP_FAKE", "default");
        NopMetaTable table = saveEntityTable(entity.getMetaEntityId());
        assertThrows(NopException.class, () -> resolve(table),
                "unregistered entity must explicitly fail (no silent empty set)");
    }

    @Test
    public void testResolveEntityTableNameEmptyThrows() {
        // 实体已注册但 tableName 为空 → 显式失败
        NopMetaEntity entity = saveMetaEntity("io.nop.metadata.dao.entity.NopMetaModule", "", "default");
        NopMetaTable table = saveEntityTable(entity.getMetaEntityId());
        assertThrows(NopException.class, () -> resolve(table),
                "empty entity.tableName must explicitly fail");
    }

    // ===== 未知 tableType =====

    @Test
    public void testResolveUnknownTableTypeThrows() {
        NopMetaTable table = saveTable("UNK_T", "weird-type", "qs_unknown");
        assertThrows(NopException.class, () -> resolve(table),
                "unknown tableType must explicitly fail (no silent skip)");
    }

    // ===== helpers =====

    private TableReference resolve(NopMetaTable table) {
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

    private NopMetaTable saveTable(String tableName, String tableType, String querySpace) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(ensureModuleId());
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setTableType(tableType);
        if (querySpace != null) {
            t.setQuerySpace(querySpace);
        }
        t.setVersion(1L);
        dao.saveEntity(t);
        return t;
    }

    private NopMetaTable saveSqlTable(String sourceSql, String querySpace) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(ensureModuleId());
        t.setTableName("SQL_V_" + System.nanoTime());
        t.setDisplayName("sql-view");
        t.setTableType(_NopMetadataCoreConstants.TABLE_TYPE_SQL);
        t.setQuerySpace(querySpace);
        t.setSourceSql(sourceSql);
        t.setVersion(1L);
        dao.saveEntity(t);
        return t;
    }

    private NopMetaEntity saveMetaEntity(String entityName, String tableName, String querySpace) {
        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity e = dao.newEntity();
        e.setOrmModelId(ensureOrmModelId());
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
        io.nop.metadata.dao.entity.NopMetaOrmModel m = dao.newEntity();
        m.setMetaModuleId(ensureModuleId());
        m.setModelName("test-model");
        m.setIsDelta((byte) 0);
        m.setVersion(1L);
        dao.saveEntity(m);
        return m.getOrmModelId();
    }

    private NopMetaTable saveEntityTable(String baseEntityId) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(ensureModuleId());
        t.setTableName("ENT_" + System.nanoTime());
        t.setDisplayName("entity-table");
        t.setTableType(_NopMetadataCoreConstants.TABLE_TYPE_ENTITY);
        t.setBaseEntityId(baseEntityId);
        t.setVersion(1L);
        dao.saveEntity(t);
        return t;
    }
}
