package io.nop.metadata.dao.model;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.metadata.dao.entity.NopMetaDict;
import io.nop.metadata.dao.entity.NopMetaDictItem;
import io.nop.metadata.dao.entity.NopMetaDomain;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaEntityIndex;
import io.nop.metadata.dao.entity.NopMetaEntityRelation;
import io.nop.metadata.dao.entity.NopMetaEntityUniqueKey;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaOrmModel;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.model.OrmDomainModel;
import io.nop.orm.model.OrmIndexColumnModel;
import io.nop.orm.model.OrmIndexModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.orm.model.OrmUniqueKeyModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将平台 {@link IOrmModel}（解析自 model/*.orm.xml）拆解为 nop_metadata 的结构化实体。
 * 仅负责 new + 填充属性，不负责持久化与外键串联（由调用方 BizModel 负责）。
 */
public class OrmModelImporter {

    public NopMetaModule buildModule(IOrmModel ormModel, long moduleVersion) {
        NopMetaModule module = new NopMetaModule();
        String appName = str(ormModel.prop_get(OrmModelConstants.EXT_APP_NAME));
        module.setModuleName(appName != null ? appName : "unknown");
        module.setModuleId(appName != null ? appName.replace('-', '/') : "unknown");
        module.setDisplayName(appName != null ? appName : "unknown");
        module.setModuleVersion(moduleVersion);
        module.setStatus("DRAFTING");
        module.setBaseModuleId(null);
        module.setMavenGroupId(str(ormModel.prop_get(OrmModelConstants.EXT_MAVEN_GROUP_ID)));
        module.setMavenArtifactId(str(ormModel.prop_get(OrmModelConstants.EXT_MAVEN_ARTIFACT_ID)));
        module.setMavenVersion(str(ormModel.prop_get("ext:mavenVersion")));
        module.setImportedAt(new java.sql.Timestamp(CoreMetrics.currentTimeMillis()));
        return module;
    }

    public NopMetaOrmModel buildOrmModel(IOrmModel ormModel, String sourceContent, boolean isDelta) {
        NopMetaOrmModel model = new NopMetaOrmModel();
        String appName = str(ormModel.prop_get(OrmModelConstants.EXT_APP_NAME));
        model.setModelName(appName != null ? appName : "unknown");
        model.setIsDelta(b(isDelta));
        model.setSourceContent(sourceContent);
        model.setImportedAt(new java.sql.Timestamp(CoreMetrics.currentTimeMillis()));
        return model;
    }

    public NopMetaEntity buildEntity(IEntityModel em, boolean isDelta) {
        NopMetaEntity entity = new NopMetaEntity();
        entity.setIsDelta(b(isDelta));
        entity.setEntityName(em.getName());
        entity.setTableName(em.getTableName());
        entity.setDisplayName(em.getDisplayName() != null ? em.getDisplayName() : StringHelper.simpleClassName(em.getName()));
        entity.setClassName(em.getClassName());
        entity.setTagSet(joinTags(em.getTagSet()));
        entity.setQuerySpace(em.getQuerySpace());
        entity.setPersistDriver(em.getPersistDriver());
        entity.setUseTenant(b(em.isUseTenant()));
        entity.setUseRevision(b(em.isUseRevision()));
        entity.setUseLogicalDelete(b(em.isUseLogicalDelete()));
        entity.setNotGenCode(b(em.getTagSet() != null && em.getTagSet().contains("not-gen")));
        return entity;
    }

    public NopMetaEntityField buildField(IColumnModel col, boolean isDelta) {
        NopMetaEntityField field = new NopMetaEntityField();
        field.setIsDelta(b(isDelta));
        field.setFieldName(col.getName());
        field.setColumnCode(col.getCode());
        field.setPropId(col.getPropId());
        StdDataType dataType = col.getStdDataType();
        field.setStdDataType(dataType != null ? dataType.getName() : null);
        StdSqlType sqlType = col.getStdSqlType();
        field.setStdSqlType(sqlType != null ? sqlType.getName() : null);
        field.setPrecision(col.getPrecision());
        field.setScale(col.getScale());
        field.setMandatory(b(col.isMandatory()));
        field.setPrimaryField(b(col.isPrimary()));
        field.setInsertable(b(col.isInsertable()));
        field.setUpdatable(b(col.isUpdatable()));
        field.setDomain(col.getDomain());
        field.setStdDomain(col.getStdDomain());
        field.setDefaultValue(col.getDefaultValue());
        field.setTagSet(joinTags(col.getTagSet()));
        field.setDisplayName(col.getDisplayName());
        field.setComment(col.getComment());
        return field;
    }

    public NopMetaEntityRelation buildRelation(IEntityRelationModel rel, boolean isDelta) {
        NopMetaEntityRelation relation = new NopMetaEntityRelation();
        relation.setIsDelta(b(isDelta));
        relation.setRelationName(rel.getName());
        relation.setRelationType(rel.isOneToOne() ? "to-one" : "to-many");
        relation.setRefEntityName(rel.getRefEntityName());
        relation.setRefPropName(rel.getRefPropName());
        relation.setTagSet(joinTags(rel.getTagSet()));
        relation.setJoinConditions(buildJoinConditionsJson(rel));
        return relation;
    }

    public NopMetaEntityUniqueKey buildUniqueKey(OrmUniqueKeyModel ukModel, boolean isDelta) {
        NopMetaEntityUniqueKey uk = new NopMetaEntityUniqueKey();
        uk.setIsDelta(b(isDelta));
        uk.setUkName(ukModel.getName());
        uk.setDisplayName(ukModel.getDisplayName() != null ? ukModel.getDisplayName() : ukModel.getName());
        uk.setColumns(StringHelper.join(ukModel.getColumns(), ","));
        uk.setConstraintName(ukModel.getConstraint());
        uk.setTagSet(joinTags(ukModel.getTagSet()));
        return uk;
    }

    public NopMetaEntityIndex buildIndex(OrmIndexModel idxModel, boolean isDelta) {
        NopMetaEntityIndex idx = new NopMetaEntityIndex();
        idx.setIsDelta(b(isDelta));
        idx.setIndexName(idxModel.getName());
        idx.setDisplayName(idxModel.getDisplayName() != null ? idxModel.getDisplayName() : idxModel.getName());
        idx.setIndexType(idxModel.getIndexType());
        idx.setUniqueIndex(Boolean.TRUE.equals(idxModel.getUnique()) ? b(true) : b(false));
        idx.setIndexColumns(buildIndexColumnsJson(idxModel));
        return idx;
    }

    public NopMetaDomain buildDomain(OrmDomainModel domain, boolean isDelta) {
        NopMetaDomain metaDomain = new NopMetaDomain();
        metaDomain.setIsDelta(b(isDelta));
        metaDomain.setDomainName(domain.getName());
        metaDomain.setDisplayName(domain.getDisplayName() != null ? domain.getDisplayName() : domain.getName());
        metaDomain.setDescription(domain.getDisplayName());
        metaDomain.setStdDomain(domain.getStdDomain());
        StdDataType dataType = domain.getStdDataType();
        metaDomain.setStdDataType(dataType != null ? dataType.getName() : null);
        StdSqlType sqlType = domain.getStdSqlType();
        metaDomain.setStdSqlType(sqlType != null ? sqlType.getName() : null);
        metaDomain.setPrecision(domain.getPrecision());
        metaDomain.setScale(domain.getScale());
        return metaDomain;
    }

    public NopMetaDict buildDict(DictBean dict, boolean isDelta) {
        NopMetaDict metaDict = new NopMetaDict();
        metaDict.setIsDelta(b(isDelta));
        metaDict.setDictName(dict.getName());
        metaDict.setLabel(dict.getLabel());
        metaDict.setValueType(dict.getValueType());
        return metaDict;
    }

    public NopMetaDictItem buildDictItem(DictOptionBean option) {
        NopMetaDictItem item = new NopMetaDictItem();
        item.setItemValue(String.valueOf(option.getValue()));
        item.setItemLabel(option.getLabel());
        item.setItemCode(option.getCode());
        item.setDescription(option.getDescription());
        return item;
    }

    public NopMetaTable buildEntityTable(IEntityModel em, boolean isDelta) {
        NopMetaTable table = new NopMetaTable();
        table.setIsDelta(b(isDelta));
        table.setTableName(em.getTableName());
        table.setDisplayName(em.getDisplayName() != null ? em.getDisplayName() : StringHelper.simpleClassName(em.getName()));
        table.setTableType("entity");
        table.setQuerySpace(em.getQuerySpace());
        table.setDescription(em.getComment());
        return table;
    }

    public List<NopMetaEntityRelation> buildRelations(IEntityModel em, boolean isDelta) {
        List<NopMetaEntityRelation> relations = new ArrayList<>();
        for (IEntityRelationModel rel : em.getRelations()) {
            relations.add(buildRelation(rel, isDelta));
        }
        return relations;
    }

    public List<NopMetaDictItem> buildDictItems(DictBean dict) {
        List<NopMetaDictItem> items = new ArrayList<>();
        if (dict.getOptions() != null) {
            int order = 0;
            for (DictOptionBean opt : dict.getOptions()) {
                NopMetaDictItem item = buildDictItem(opt);
                item.setSortOrder(order++);
                items.add(item);
            }
        }
        return items;
    }

    /**
     * join 条件结构化序列化（AR-18，plan 2026-08-06-0914-3）：手拼字符串 JSON 在 left/right 值含
     * {@code "}/{@code \} 时产出非法 JSON 静默入库——统一改 {@link JsonTool#stringify}，任何输入值
     * 均产出可解析 JSON，持久化格式不变（同为 JSON 数组文本）。
     */
    private String buildJoinConditionsJson(IEntityRelationModel rel) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (IEntityJoinConditionModel join : rel.getJoin()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("leftProp", join.getLeftProp() != null ? join.getLeftProp() : StringHelper.toString(join.getLeftValue(), ""));
            m.put("rightProp", join.getRightProp() != null ? join.getRightProp() : StringHelper.toString(join.getRightValue(), ""));
            list.add(m);
        }
        return JsonTool.stringify(list);
    }

    private String buildIndexColumnsJson(OrmIndexModel idxModel) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (idxModel.getColumns() != null) {
            for (OrmIndexColumnModel col : idxModel.getColumns()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("fieldName", col.getName());
                m.put("desc", Boolean.TRUE.equals(col.getDesc()));
                list.add(m);
            }
        }
        return JsonTool.stringify(list);
    }

    static byte b(boolean v) {
        return (byte) (v ? 1 : 0);
    }

    static String str(Object v) {
        return v != null ? String.valueOf(v) : null;
    }

    static String joinTags(java.util.Set<String> tags) {
        if (tags == null || tags.isEmpty())
            return null;
        return StringHelper.join(tags, ",");
    }
}
