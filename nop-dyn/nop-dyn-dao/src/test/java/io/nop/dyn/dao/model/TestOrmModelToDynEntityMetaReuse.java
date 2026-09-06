/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.dao.model;

import io.nop.commons.type.StdSqlType;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmJoinOnModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.orm.model.OrmToOneReferenceModel;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 对存量模块重复转换（importExcel removeNotExisting=true / generateByAI 的设计场景）时：
 * 实体名 simple/full 口径必须一致（复用存量meta而非全删重建，主键不漂移），
 * 关联元数据按relationName去重而非无条件叠加
 */
public class TestOrmModelToDynEntityMetaReuse {

    /**
     * ORM模型按全名（entityPackageName前缀+简名）导出实体，而存量entityMetas以简名为key。
     * 重导入同名实体必须复用既有meta对象，不能新建一份同名meta（主键漂移、级联元数据全部重建）
     */
    @Test
    public void testTransformExistingModuleReusesEntityMeta() {
        NopDynModule module = new NopDynModule();
        new OrmModelToDynEntityMeta(true).transformModule(buildOrmModel(true), module);

        NopDynEntityMeta first = findMeta(module, "AppEntity");
        assertNotNull(first);

        // 模拟对存量模块重导入
        new OrmModelToDynEntityMeta(true).transformModule(buildOrmModel(true), module);

        List<NopDynEntityMeta> appMetas = module.getEntityMetas().stream()
                .filter(m -> "AppEntity".equals(m.getEntityName())).collect(Collectors.toList());
        assertEquals(1, appMetas.size(), "存量实体必须被复用，不能新建同名meta");
        assertSame(first, appMetas.get(0), "同名实体必须复用既有meta对象（主键不能漂移）");
    }

    /**
     * removeNotExisting只应移除模型中已消失的实体；名字口径不一致会把仍存在的实体也误删后重建
     */
    @Test
    public void testRemoveNotExistingKeepsExistingEntity() {
        NopDynModule module = new NopDynModule();
        new OrmModelToDynEntityMeta(true).transformModule(buildOrmModel(true), module);

        NopDynEntityMeta first = findMeta(module, "AppEntity");
        assertNotNull(first);

        // 第二轮导入的模型不再包含OtherEntity
        new OrmModelToDynEntityMeta(true).transformModule(buildOrmModel(false), module);

        assertNull(findMeta(module, "OtherEntity"), "已消失的实体必须被移除");
        assertSame(first, findMeta(module, "AppEntity"), "仍然存在的实体必须原样保留，不能删除后重建");
    }

    /**
     * 重复转换时同名关联必须去重（替换旧meta），不能在同轮或跨轮叠加翻倍
     */
    @Test
    public void testRelationMetaNotDuplicatedOnRetransform() {
        NopDynModule module = new NopDynModule();
        new OrmModelToDynEntityMeta(true).transformModule(buildOrmModel(true), module);

        new OrmModelToDynEntityMeta(true).transformModule(buildOrmModel(true), module);

        long count = module.getEntityMetas().stream()
                .filter(m -> "AppEntity".equals(m.getEntityName()))
                .flatMap(m -> m.getRelationMetasForEntity().stream())
                .filter(r -> "other".equals(r.getRelationName())).count();
        assertEquals(1, count, "重复转换时同名关联必须去重，不能翻倍");
    }

    static NopDynEntityMeta findMeta(NopDynModule module, String entityName) {
        return module.getEntityMetas().stream()
                .filter(m -> entityName.equals(m.getEntityName())).findFirst().orElse(null);
    }

    /**
     * 构造与DynEntityMetaToOrmModel导出口径一致的ORM模型：实体名为entityPackageName前缀+简名
     */
    private OrmModel buildOrmModel(boolean withOtherEntity) {
        OrmEntityModel appEntity = buildEntity("test.AppEntity", "app_entity");
        OrmModel model = new OrmModel();
        model.prop_set(OrmModelConstants.EXT_ENTITY_PACKAGE_NAME, "test");

        if (withOtherEntity) {
            OrmEntityModel otherEntity = buildEntity("test.OtherEntity", "other_entity");

            OrmToOneReferenceModel rel = new OrmToOneReferenceModel();
            rel.setName("other");
            rel.setRefEntityName("test.OtherEntity");
            OrmJoinOnModel joinOn = new OrmJoinOnModel();
            joinOn.setLeftProp("otherSid");
            joinOn.setRightProp("sid");
            rel.setJoin(List.of(joinOn));
            rel.setOwnerEntityModel(appEntity);
            appEntity.addRelation(rel);

            model.setEntities(List.of(appEntity, otherEntity));
        } else {
            model.setEntities(List.of(appEntity));
        }
        model.init();
        return model;
    }

    private OrmEntityModel buildEntity(String name, String tableName) {
        OrmEntityModel entity = new OrmEntityModel();
        entity.setName(name);
        entity.setTableName(tableName);
        entity.setTagSet(new HashSet<>());
        entity.addColumn(column("sid", 1, true));
        entity.addColumn(column("name", 2, false));
        entity.addColumn(column("otherSid", 3, false));
        return entity;
    }

    private OrmColumnModel column(String name, int propId, boolean primary) {
        OrmColumnModel col = new OrmColumnModel();
        col.setName(name);
        col.setCode(name.toUpperCase());
        col.setPropId(propId);
        col.setPrimary(primary);
        col.setStdSqlType(StdSqlType.VARCHAR);
        col.setPrecision(primary ? 32 : 100);
        col.setScale(0);
        return col;
    }
}
