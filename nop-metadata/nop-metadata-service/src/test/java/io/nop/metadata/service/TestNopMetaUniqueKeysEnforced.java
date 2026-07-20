/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmUniqueKeyModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 ORM 模型新增的 unique-key 约束（plan 1250-2 Phase 2 Proof，维度04-06）。
 *
 * <p>修复前：29 个实体无自然键 UK 声明，重复数据可静默插入且无文档化的约束意图。
 * 修复后：高优先级实体（NopMetaEntity / NopMetaDictItem / NopMetaOrmModel / NopMetaDomain / NopMetaTable）等
 * 均在 ORM 模型中声明 UK，DB schema 与运行时均按此声明生成约束。
 *
 * <p>本测试通过 {@link IEntityModel#getUniqueKeys()} 验证 ORM 模型层 UK 声明存在
 * （Anti-Hollow：不只看源文件有 UK 文本，而是验证运行时解析的 entity model 确实包含 UK）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaUniqueKeysEnforced extends JunitBaseTestCase {

    public TestNopMetaUniqueKeysEnforced() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate orm;

    /** NopMetaEntity UK: (ormModelId, entityName). */
    @Test
    public void testNopMetaEntityHasNaturalUniqueKey() {
        OrmEntityModel model = (OrmEntityModel) orm.getOrmModel().getEntityModel("io.nop.metadata.dao.entity.NopMetaEntity");
        assertNotNull(model, "NopMetaEntity model must be loaded");
        assertTrue(hasUniqueKeyWithColumns(model, "ormModelId", "entityName"),
                "NopMetaEntity must declare UK on (ormModelId, entityName): " + ukNames(model));
    }

    @Test
    public void testNopMetaDictItemHasNaturalUniqueKey() {
        OrmEntityModel model = (OrmEntityModel) orm.getOrmModel().getEntityModel("io.nop.metadata.dao.entity.NopMetaDictItem");
        assertNotNull(model);
        assertTrue(hasUniqueKeyWithColumns(model, "metaDictId", "itemValue"),
                "NopMetaDictItem must declare UK on (metaDictId, itemValue): " + ukNames(model));
    }

    @Test
    public void testNopMetaOrmModelHasNaturalUniqueKey() {
        OrmEntityModel model = (OrmEntityModel) orm.getOrmModel().getEntityModel("io.nop.metadata.dao.entity.NopMetaOrmModel");
        assertNotNull(model);
        assertTrue(hasUniqueKeyWithColumns(model, "metaModuleId", "modelName"),
                "NopMetaOrmModel must declare UK on (metaModuleId, modelName): " + ukNames(model));
    }

    @Test
    public void testNopMetaDomainHasNaturalUniqueKey() {
        OrmEntityModel model = (OrmEntityModel) orm.getOrmModel().getEntityModel("io.nop.metadata.dao.entity.NopMetaDomain");
        assertNotNull(model);
        assertTrue(hasUniqueKeyWithColumns(model, "ormModelId", "domainName"),
                "NopMetaDomain must declare UK on (ormModelId, domainName): " + ukNames(model));
    }

    @Test
    public void testNopMetaTableHasNaturalUniqueKey() {
        OrmEntityModel model = (OrmEntityModel) orm.getOrmModel().getEntityModel("io.nop.metadata.dao.entity.NopMetaTable");
        assertNotNull(model);
        assertTrue(hasUniqueKeyWithColumns(model, "metaModuleId", "tableName"),
                "NopMetaTable must declare UK on (metaModuleId, tableName): " + ukNames(model));
    }

    @Test
    public void testNopMetaDictHasNaturalUniqueKey() {
        OrmEntityModel model = (OrmEntityModel) orm.getOrmModel().getEntityModel("io.nop.metadata.dao.entity.NopMetaDict");
        assertNotNull(model);
        assertTrue(hasUniqueKeyWithColumns(model, "ormModelId", "dictName"),
                "NopMetaDict must declare UK on (ormModelId, dictName): " + ukNames(model));
    }

    @Test
    public void testNopMetaPipelineHasNaturalUniqueKey() {
        OrmEntityModel model = (OrmEntityModel) orm.getOrmModel().getEntityModel("io.nop.metadata.dao.entity.NopMetaPipeline");
        assertNotNull(model);
        assertTrue(hasUniqueKeyWithColumns(model, "metaModuleId", "pipelineName"),
                "NopMetaPipeline must declare UK on (metaModuleId, pipelineName): " + ukNames(model));
    }

    @Test
    public void testNopMetaManifestHasNaturalUniqueKey() {
        OrmEntityModel model = (OrmEntityModel) orm.getOrmModel().getEntityModel("io.nop.metadata.dao.entity.NopMetaManifest");
        assertNotNull(model);
        assertTrue(hasUniqueKeyWithColumns(model, "metaModuleId", "manifestVersion"),
                "NopMetaManifest must declare UK on (metaModuleId, manifestVersion): " + ukNames(model));
    }

    // ============================ helpers ============================

    private static boolean hasUniqueKeyWithColumns(OrmEntityModel model, String... propNames) {
        List<OrmUniqueKeyModel> uks = model.getUniqueKeys();
        if (uks == null || uks.isEmpty()) {
            return false;
        }
        for (OrmUniqueKeyModel uk : uks) {
            List<OrmColumnModel> cols = uk.getColumnModels();
            if (cols == null || cols.size() != propNames.length) {
                continue;
            }
            // Order-insensitive match
            boolean allFound = true;
            for (String expectedProp : propNames) {
                boolean found = false;
                for (OrmColumnModel col : cols) {
                    if (expectedProp.equals(col.getName())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    allFound = false;
                    break;
                }
            }
            if (allFound) return true;
        }
        return false;
    }

    private static String ukNames(OrmEntityModel model) {
        StringBuilder sb = new StringBuilder("[");
        if (model.getUniqueKeys() != null) {
            for (OrmUniqueKeyModel uk : model.getUniqueKeys()) {
                if (sb.length() > 1) sb.append(", ");
                sb.append(uk.getName());
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
