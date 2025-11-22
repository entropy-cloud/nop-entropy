package io.nop.orm.support;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.OrmModelConstants;

import java.util.List;
import java.util.Set;

/**
 * 专用于多对多关联的中间表MappingTable。
 * 1. MappingTable中refProp1指向实体Entity1，而refProp2指向实体Entity2。
 * 2. Entity1上的reverseRefProp1指向MappingTable, Entity2上的reverseRefProp2指向MappingTable
 * 3. Entity1上的mappingProp1指向Entity2，而Entity2上的mappingProp2指向Entity1。也就是说mappingProp对应于多对多关联属性。
 */
public class OrmMappingTableMeta {
    private final IEntityModel mappingTable;
    private final IEntityRelationModel refProp1;
    private final IEntityRelationModel refProp2;

    public OrmMappingTableMeta(IEntityModel mappingTable) {
        this.mappingTable = mappingTable;
        List<? extends IEntityRelationModel> rels = mappingTable.getToOneRelations();
        this.refProp1 = CollectionHelper.first(rels);
        this.refProp2 = CollectionHelper.last(rels);

        // Guard.checkArgument(rels.size() == 2, "mappingTable must contains two to-one relations");
    }

    /**
     * 中间表具有mapping标签或者many-to-many标签
     */
    public static boolean isMappingTable(IEntityModel entityModel) {
        return entityModel.containsTag(OrmModelConstants.TAG_MAPPING) || entityModel.containsTag(OrmModelConstants.TAG_MANY_TO_MANY);
    }

    public boolean isOneToOne() {
        return mappingTable.containsTag(OrmModelConstants.TAG_ONE_TO_ONE);
    }

    public boolean isManyToMany() {
        return mappingTable.containsTag(OrmModelConstants.TAG_MANY_TO_MANY);
    }

    public IEntityModel getMappingTable() {
        return mappingTable;
    }

    public IEntityRelationModel getRefProp1() {
        return refProp1;
    }

    public IEntityRelationModel getRefProp2() {
        return refProp2;
    }

    public String getReverseRefPropName1() {
        if (refProp1 == null)
            return null;
        return refProp1.getRefPropName();
    }

    public String getReverseRefPropName2() {
        if (refProp2 == null)
            return null;
        return refProp2.getRefPropName();
    }

    public String getMappingPropName1() {
        String refSetName = (String) mappingTable.prop_get(OrmModelConstants.ORM_MAPPING_PROP_NAME1);
        if (StringHelper.isEmpty(refSetName)) {
            if (isOneToOne()) {
                refSetName = "related" + StringHelper.capitalize(refProp2.getName());
            } else {
                refSetName = "related" + StringHelper.capitalize(refProp2.getName()) + "List";
            }
        }
        return refSetName;
    }

    public String getMappingPropName2() {
        String refSetName = (String) mappingTable.prop_get(OrmModelConstants.ORM_MAPPING_PROP_NAME2);
        if (StringHelper.isEmpty(refSetName)) {
            if (isOneToOne()) {
                refSetName = "related" + StringHelper.capitalize(refProp1.getName());
            } else {
                refSetName = "related" + StringHelper.capitalize(refProp1.getName()) + "List";
            }
        }
        return refSetName;
    }

    public String getMappingPropDisplayName1() {
        String displayName = (String) mappingTable.prop_get(OrmModelConstants.ORM_MAPPING_PROP_DISPLAY_NAME1);
        if (displayName == null)
            displayName = getMappingPropName1();
        return displayName;
    }

    public String getMappingPropDisplayName2() {
        String displayName = (String) mappingTable.prop_get(OrmModelConstants.ORM_MAPPING_PROP_DISPLAY_NAME2);
        if (displayName == null)
            displayName = getMappingPropName2();
        return displayName;
    }

    public String getMappingPropEnDisplayName1() {
        String displayName = (String) mappingTable.prop_get(OrmModelConstants.ORM_MAPPING_PROP_EN_DISPLAY_NAME1);
        return displayName;
    }

    public String getMappingPropEnDisplayName2() {
        String displayName = (String) mappingTable.prop_get(OrmModelConstants.ORM_MAPPING_PROP_EN_DISPLAY_NAME1);
        return displayName;
    }

    public Set<String> getMappingTagSet1() {
        return ConvertHelper.toCsvSet(mappingTable.prop_get(OrmModelConstants.ORM_MAPPING_TAG_SET_1));
    }

    public Set<String> getMappingTagSet2() {
        return ConvertHelper.toCsvSet(mappingTable.prop_get(OrmModelConstants.ORM_MAPPING_TAG_SET_2));
    }

    public String getBizModuleId1() {
        IEntityModel refEntityModel1 = refProp1.getRefEntityModel();
        return (String) refEntityModel1.prop_get(OrmModelConstants.EXT_BIZ_MODULE_ID);
    }

    public String getBizModuleId2() {
        IEntityModel refEntityModel2 = refProp2.getRefEntityModel();
        return (String) refEntityModel2.prop_get(OrmModelConstants.EXT_BIZ_MODULE_ID);
    }

    public OrmMappingPropInfo getMappingPropInfo1() {
        return new OrmMappingPropInfo(getRefBizObjName2(), getMappingPropName1(),
                getMappingPropDisplayName1(), getMappingPropEnDisplayName1(),
                !isOneToOne(), getMappingPropName1_label(), getMappingPropName1_ids(),
                refProp2, getBizModuleId1(), getMappingTagSet1());
    }

    public OrmMappingPropInfo getMappingPropInfo2() {
        return new OrmMappingPropInfo(getRefBizObjName1(), getMappingPropName2(),
                getMappingPropDisplayName2(), getMappingPropEnDisplayName2(),
                !isOneToOne(), getMappingPropName2_label(), getMappingPropName2_ids(),
                refProp1, getBizModuleId2(), getMappingTagSet2());
    }

    public OrmMappingPropInfo getMappingPropInfo(IEntityRelationModel reverseRefProp) {
        if (reverseRefProp.getName().equals(getReverseRefPropName1()))
            return getMappingPropInfo1();
        if (reverseRefProp.getName().equals(getReverseRefPropName2()))
            return getMappingPropInfo2();
        if (reverseRefProp.getOwnerEntityModel().getName().equals(getRefEntityName1()))
            return getMappingPropInfo1();
        if (reverseRefProp.getOwnerEntityModel().getName().equals(getRefEntityName2()))
            return getMappingPropInfo2();
        return null;
    }

    public String getJoinPropName1() {
        if (refProp1.isSingleColumn()) {
            return refProp1.getSingleColumnJoin().getLeftProp();
        }
        return refProp1.getName();
    }

    public String getJoinPropName2() {
        if (refProp2.isSingleColumn()) {
            return refProp2.getSingleColumnJoin().getLeftProp();
        }
        return refProp2.getName();
    }

    public String getMappingPropName1_label() {
        return getMappingPropName1() + "_label";
    }

    public String getMappingPropName1_ids() {
        if (isOneToOne())
            return getMappingPropName1() + "_id";

        return getMappingPropName1() + "_ids";
    }

    public String getMappingPropName2_label() {
        return getMappingPropName2() + "_label";
    }

    public String getMappingPropName2_ids() {
        if (isOneToOne())
            return getMappingPropName2() + "_id";

        return getMappingPropName2() + "_ids";
    }

    public String getRefEntityName1() {
        return refProp1.getRefEntityModel().getName();
    }

    public String getRefEntityName2() {
        return refProp2.getRefEntityModel().getName();
    }

    public String getRefBizObjName1() {
        return StringHelper.simpleClassName(getRefEntityName1());
    }

    public String getRefBizObjName2() {
        return StringHelper.simpleClassName(getRefEntityName2());
    }

    public String getLabelPropOfRefEntity1() {
        String labelProp = refProp1.getRefEntityModel().getLabelProp();
        if (StringHelper.isEmpty(labelProp))
            labelProp = OrmModelConstants.PROP_ID;
        return labelProp;
    }

    public String getLabelPropOfRefEntity2() {
        String labelProp = refProp2.getRefEntityModel().getLabelProp();
        if (StringHelper.isEmpty(labelProp))
            labelProp = OrmModelConstants.PROP_ID;
        return labelProp;
    }

    public String getMappingEntityName() {
        return mappingTable.getName();
    }

    public String getMappingTableName() {
        return mappingTable.getTableName();
    }
}