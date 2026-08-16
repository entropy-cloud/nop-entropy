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

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * R4.2（plan-2026-08-05-1625-1）：UK_NOP_META_TABLE_MODULE_NAME 扩展 metaSchema 维度
     * （多 schema 同名表可共存，metaSchema null 语义裁定 = 路径 A 保持可空）。
     */
    @Test
    public void testTableUniqueKeyIncludesMetaSchemaDimension() {
        OrmEntityModel table = entityModel("io.nop.metadata.dao.entity.NopMetaTable");
        assertTrue(hasUniqueKeyColumn(table, "UK_NOP_META_TABLE_MODULE_NAME", "META_SCHEMA"),
                "UK_NOP_META_TABLE_MODULE_NAME must include metaSchema dimension (multi-schema support)");

        for (String dialect : new String[]{"mysql", "oracle", "postgresql"}) {
            String sql = DdlSqlCreator.forDialect(dialect).createTable(table, false);
            String schemaCol = "postgresql".equals(dialect) ? "meta_schema" : "META_SCHEMA";
            String ukLine = null;
            for (String line : sql.split("\\n")) {
                if (line.contains("UK_NOP_META_TABLE_MODULE_NAME") && line.contains("unique")) {
                    ukLine = line;
                    break;
                }
            }
            assertTrue(ukLine != null && ukLine.contains(schemaCol),
                    dialect + " DDL unique constraint must include metaSchema column, actual: " + sql);
        }
    }

    /**
     * R4.3（plan-2026-08-05-1625-2）：NopMetaQualityResult 新增复合 UK
     * UK_NOP_META_QUALITY_RESULT_CP_RUN_RULE (checkpointId, runId, qualityRuleId)——防同 runId 重复写行。
     * 断言三方言 DDL 物化发射（防 R3.19 类零 UK 发射复发）+ 模型层 UK 声明存在。
     */
    @Test
    public void testNopMetaQualityResultUniqueKeyEmission() {
        OrmEntityModel result = entityModel("io.nop.metadata.dao.entity.NopMetaQualityResult");
        assertNotNull(result, "NopMetaQualityResult model must be loaded");
        assertTrue(hasUniqueKeyColumn(result, "UK_NOP_META_QUALITY_RESULT_CP_RUN_RULE", "CHECKPOINT_ID"),
                "UK must include checkpointId dimension");
        assertTrue(hasUniqueKeyColumn(result, "UK_NOP_META_QUALITY_RESULT_CP_RUN_RULE", "RUN_ID"),
                "UK must include runId dimension");
        assertTrue(hasUniqueKeyColumn(result, "UK_NOP_META_QUALITY_RESULT_CP_RUN_RULE", "QUALITY_RULE_ID"),
                "UK must include qualityRuleId dimension");

        for (String dialect : new String[]{"mysql", "oracle", "postgresql"}) {
            String sql = DdlSqlCreator.forDialect(dialect).createTable(result, false);
            assertTrue(sql.contains("UK_NOP_META_QUALITY_RESULT_CP_RUN_RULE"),
                    dialect + " DDL must emit constraint UK_NOP_META_QUALITY_RESULT_CP_RUN_RULE, actual: " + sql);
            String runIdCol = "postgresql".equals(dialect) ? "run_id" : "RUN_ID";
            String ruleCol = "postgresql".equals(dialect) ? "quality_rule_id" : "QUALITY_RULE_ID";
            String ukLine = null;
            for (String line : sql.split("\\n")) {
                if (line.contains("UK_NOP_META_QUALITY_RESULT_CP_RUN_RULE") && line.contains("unique")) {
                    ukLine = line;
                    break;
                }
            }
            assertTrue(ukLine != null && ukLine.contains(runIdCol) && ukLine.contains(ruleCol),
                    dialect + " DDL unique constraint must include (CHECKPOINT_ID,RUN_ID,QUALITY_RULE_ID), actual: "
                            + sql);
        }
    }

    /**
     * P2-29（plan-2026-08-16-0920-1）：删除冗余 per-scope FQN UK
     * （UK_NOP_META_GLOSSARY_TERM_G_FQN / UK_NOP_META_TAG_CLS_FQN）——非 NULL FQN 行
     * 全局 (fullyQualifiedName) UK 逻辑蕴含 per-scope UK，删除后约束语义不变。
     * 断言：模型 UK 集 = 仅全局 FQN UK；三方言 DDL 发射全局 UK 且不再发射 per-scope 名
     * （防止删除后生成物残留或全局 UK 误删）。
     */
    @Test
    public void testFqnUniqueKeySetAfterP229Adjudication() {
        OrmEntityModel term = entityModel("io.nop.metadata.dao.entity.NopMetaGlossaryTerm");
        assertNotNull(term, "NopMetaGlossaryTerm model must be loaded");
        assertTrue(hasUniqueKey(term, "UK_NOP_META_GLOSSARY_TERM_FQN"),
                "global FQN UK must be kept on NopMetaGlossaryTerm");
        assertFalse(hasUniqueKey(term, "UK_NOP_META_GLOSSARY_TERM_G_FQN"),
                "redundant per-glossary FQN UK must be removed (P2-29)");

        OrmEntityModel tag = entityModel("io.nop.metadata.dao.entity.NopMetaTag");
        assertNotNull(tag, "NopMetaTag model must be loaded");
        assertTrue(hasUniqueKey(tag, "UK_NOP_META_TAG_FQN"),
                "global FQN UK must be kept on NopMetaTag");
        assertFalse(hasUniqueKey(tag, "UK_NOP_META_TAG_CLS_FQN"),
                "redundant per-classification FQN UK must be removed (P2-29)");

        for (String dialect : new String[]{"mysql", "oracle", "postgresql"}) {
            String termSql = DdlSqlCreator.forDialect(dialect).createTable(term, false);
            assertTrue(termSql.contains("UK_NOP_META_GLOSSARY_TERM_FQN"),
                    dialect + " DDL must emit global FQN UK for glossary term, actual: " + termSql);
            assertTrue(!termSql.contains("UK_NOP_META_GLOSSARY_TERM_G_FQN"),
                    dialect + " DDL must not emit removed per-glossary FQN UK, actual: " + termSql);

            String tagSql = DdlSqlCreator.forDialect(dialect).createTable(tag, false);
            assertTrue(tagSql.contains("UK_NOP_META_TAG_FQN"),
                    dialect + " DDL must emit global FQN UK for tag, actual: " + tagSql);
            assertTrue(!tagSql.contains("UK_NOP_META_TAG_CLS_FQN"),
                    dialect + " DDL must not emit removed per-classification FQN UK, actual: " + tagSql);
        }
    }

    private OrmEntityModel entityModel(String name) {
        return (OrmEntityModel) orm.getOrmModel().getEntityModel(name);
    }

    private static boolean hasUniqueKey(OrmEntityModel model, String ukName) {
        if (model == null || model.getUniqueKeys() == null) {
            return false;
        }
        for (OrmUniqueKeyModel uk : model.getUniqueKeys()) {
            if (ukName.equals(uk.getName())) {
                return true;
            }
        }
        return false;
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
