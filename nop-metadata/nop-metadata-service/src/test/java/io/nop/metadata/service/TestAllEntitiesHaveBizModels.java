package io.nop.metadata.service;

import io.nop.metadata.dao.entity.NopMetaBusinessDomain;
import io.nop.metadata.dao.entity.NopMetaCatalog;
import io.nop.metadata.dao.entity.NopMetaClassification;
import io.nop.metadata.dao.entity.NopMetaDataContract;
import io.nop.metadata.dao.entity.NopMetaDataProduct;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaDict;
import io.nop.metadata.dao.entity.NopMetaDictItem;
import io.nop.metadata.dao.entity.NopMetaDomain;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaEntityIndex;
import io.nop.metadata.dao.entity.NopMetaEntityRelation;
import io.nop.metadata.dao.entity.NopMetaEntityUniqueKey;
import io.nop.metadata.dao.entity.NopMetaGlossary;
import io.nop.metadata.dao.entity.NopMetaGlossaryTerm;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import io.nop.metadata.dao.entity.NopMetaManifest;
import io.nop.metadata.dao.entity.NopMetaModelChangedEvent;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaOrmModel;
import io.nop.metadata.dao.entity.NopMetaPipeline;
import io.nop.metadata.dao.entity.NopMetaProfilingResult;
import io.nop.metadata.dao.entity.NopMetaProfilingRule;
import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.dao.entity.NopMetaQualityRule;
import io.nop.metadata.dao.entity.NopMetaQualityScore;
import io.nop.metadata.dao.entity.NopMetaReconciliationConfig;
import io.nop.metadata.dao.entity.NopMetaReconciliationEntity;
import io.nop.metadata.dao.entity.NopMetaReconciliationResult;
import io.nop.metadata.dao.entity.NopMetaSemanticType;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableDimension;
import io.nop.metadata.dao.entity.NopMetaTableFilter;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.dao.entity.NopMetaTableMeasure;
import io.nop.metadata.dao.entity.NopMetaTag;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证每个实体类都有对应的 BizModel 类。
 * 作为 CRUD API codegen 的替代验证：不需要启用 codegen，
 * 只需保证手工编写的 BizModel 覆盖全部实体。
 */
public class TestAllEntitiesHaveBizModels {

    @Test
    public void testAllEntitiesHaveBizModels() {
        List<Class<?>> entities = allEntities();
        List<String> missing = new ArrayList<>();
        for (Class<?> entityClass : entities) {
            String bizClassName = entityClass.getSimpleName() + "BizModel";
            try {
                Class<?> bizClass = Class.forName(
                        "io.nop.metadata.service.entity." + bizClassName);
                assertNotNull(bizClass, "BizModel class must exist for entity: " + entityClass.getSimpleName());
            } catch (ClassNotFoundException e) {
                missing.add(entityClass.getSimpleName());
            }
        }
        if (!missing.isEmpty()) {
            throw new AssertionError("Missing BizModel for entities: " + String.join(", ", missing));
        }
    }

    private List<Class<?>> allEntities() {
        List<Class<?>> list = new ArrayList<>();
        list.add(NopMetaBusinessDomain.class);
        list.add(NopMetaCatalog.class);
        list.add(NopMetaClassification.class);
        list.add(NopMetaDataContract.class);
        list.add(NopMetaDataProduct.class);
        list.add(NopMetaDataSource.class);
        list.add(NopMetaDict.class);
        list.add(NopMetaDictItem.class);
        list.add(NopMetaDomain.class);
        list.add(NopMetaEntity.class);
        list.add(NopMetaEntityField.class);
        list.add(NopMetaEntityIndex.class);
        list.add(NopMetaEntityRelation.class);
        list.add(NopMetaEntityUniqueKey.class);
        list.add(NopMetaGlossary.class);
        list.add(NopMetaGlossaryTerm.class);
        list.add(NopMetaLineageEdge.class);
        list.add(NopMetaManifest.class);
        list.add(NopMetaModelChangedEvent.class);
        list.add(NopMetaModule.class);
        list.add(NopMetaOrmModel.class);
        list.add(NopMetaPipeline.class);
        list.add(NopMetaProfilingResult.class);
        list.add(NopMetaProfilingRule.class);
        list.add(NopMetaQualityCheckpoint.class);
        list.add(NopMetaQualityResult.class);
        list.add(NopMetaQualityRule.class);
        list.add(NopMetaQualityScore.class);
        list.add(NopMetaReconciliationConfig.class);
        list.add(NopMetaReconciliationEntity.class);
        list.add(NopMetaReconciliationResult.class);
        list.add(NopMetaSemanticType.class);
        list.add(NopMetaTable.class);
        list.add(NopMetaTableDimension.class);
        list.add(NopMetaTableFilter.class);
        list.add(NopMetaTableJoin.class);
        list.add(NopMetaTableMeasure.class);
        list.add(NopMetaTag.class);
        list.add(NopMetaTagLabel.class);
        return list;
    }
}
