package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmUniqueKeyModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-MA6.6-001 / MA7.3-01（MR3 R3.19）DDL UK 发射回归测试：
 *
 * <p>修复前：模型 36 个 unique-key 全部无 {@code constraint} 属性，ddl.xlib
 * {@code TableUniqueConstraints} 以 {@code uniqueKey.constraint} 非空为发射条件（:82），
 * 导致所有 DDL 生成路径（deploy/sql 快照与 initDatabaseSchema）零 UK 发射。
 * 修复后：36 个 UK 均补 {@code constraint} 属性（model-first），本测试直接用
 * {@link DdlSqlCreator} 断言三方言 DDL 实际包含 UK 约束文本（Anti-Hollow：断言
 * 生成产物而非仅源模型声明）。
 *
 * <p>UK_NOP_META_ORM_MODEL_MODULE_NAME / UK_NOP_META_TABLE_MODULE_NAME 同时补
 * isDelta 列维度（MA7.3-01 双重存储相容性裁决），断言其列清单含 IS_DELTA。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaDdlUniqueKeyEmission extends JunitBaseTestCase {

    @Inject
    IOrmTemplate orm;

    @Test
    public void testCreateTableEmitsUniqueKeyForAllThreeDialects() {
        OrmEntityModel table = entityModel("io.nop.metadata.dao.entity.NopMetaTable");
        assertNotNull(table, "NopMetaTable model must be loaded");
        assertNotNull(table.getUniqueKeys(), "NopMetaTable must declare unique keys");
        assertTrue(table.getUniqueKeys().stream().anyMatch(uk -> "UK_NOP_META_TABLE_MODULE_NAME".equals(uk.getName())),
                "NopMetaTable must keep UK_NOP_META_TABLE_MODULE_NAME");

        for (String dialect : new String[]{"mysql", "oracle", "postgresql"}) {
            String sql = DdlSqlCreator.forDialect(dialect).createTable(table, false);
            assertTrue(sql.contains("UK_NOP_META_TABLE_MODULE_NAME"),
                    dialect + " DDL must emit constraint UK_NOP_META_TABLE_MODULE_NAME, actual: " + sql);
            assertTrue(sql.contains("unique"),
                    dialect + " DDL must emit a UNIQUE constraint, actual: " + sql);
        }
    }

    @Test
    public void testNopMetaOrmModelUniqueKeyEmission() {
        OrmEntityModel ormModel = entityModel("io.nop.metadata.dao.entity.NopMetaOrmModel");
        assertNotNull(ormModel, "NopMetaOrmModel model must be loaded");

        String sql = DdlSqlCreator.forDialect("mysql").createTable(ormModel, false);
        assertTrue(sql.contains("UK_NOP_META_ORM_MODEL_MODULE_NAME"),
                "mysql DDL must emit constraint UK_NOP_META_ORM_MODEL_MODULE_NAME, actual: " + sql);
    }

    @Test
    public void testDualStorageUniqueKeysIncludeIsDeltaDimension() {
        OrmEntityModel ormModel = entityModel("io.nop.metadata.dao.entity.NopMetaOrmModel");
        assertTrue(hasUniqueKeyColumn(ormModel, "UK_NOP_META_ORM_MODEL_MODULE_NAME", "IS_DELTA"),
                "UK_NOP_META_ORM_MODEL_MODULE_NAME must include isDelta dimension (dual storage)");

        OrmEntityModel table = entityModel("io.nop.metadata.dao.entity.NopMetaTable");
        assertTrue(hasUniqueKeyColumn(table, "UK_NOP_META_TABLE_MODULE_NAME", "IS_DELTA"),
                "UK_NOP_META_TABLE_MODULE_NAME must include isDelta dimension (dual storage)");

        OrmEntityModel module = entityModel("io.nop.metadata.dao.entity.NopMetaModule");
        assertTrue(hasUniqueKeyColumn(module, "UK_NOP_META_MODULE_ID_VER", "MODULE_ID"),
                "UK_NOP_META_MODULE_ID_VER must still be emitted on (moduleId, moduleVersion)");
    }

    private OrmEntityModel entityModel(String name) {
        return (OrmEntityModel) orm.getOrmModel().getEntityModel(name);
    }

    private static boolean hasUniqueKeyColumn(OrmEntityModel model, String ukName, String columnCode) {
        if (model == null || model.getUniqueKeys() == null) {
            return false;
        }
        for (OrmUniqueKeyModel uk : model.getUniqueKeys()) {
            if (ukName.equals(uk.getName()) && uk.getColumnModels() != null) {
                for (OrmColumnModel col : uk.getColumnModels()) {
                    if (columnCode.equals(col.getCode())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
