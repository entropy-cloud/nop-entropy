package io.nop.orm.support;

import io.nop.api.core.util.Guard;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.OrmModelConstants;

import java.util.List;

/**
 * 专用于多对多关联的中间表
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

        Guard.checkArgument(rels.size() == 2, "mappingTable must contains two to-one relations");
    }

    /**
     * 中间表具有mapping标签或者many-to-man标签
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

    public String getRefPropName1() {
        String refSetName = (String) mappingTable.prop_get(OrmModelConstants.ORM_MAPPING_PROP_NAME1);
        if (StringHelper.isEmpty(refSetName))
            refSetName = "related" + StringHelper.capitalize(refProp2.getName()) + "List";
        return refSetName;
    }

    public String getRefPropName2() {
        String refSetName = (String) mappingTable.prop_get(OrmModelConstants.ORM_MAPPING_PROP_NAME2);
        if (StringHelper.isEmpty(refSetName))
            refSetName = "related" + StringHelper.capitalize(refProp1.getName()) + "List";
        return refSetName;
    }

    public String getRefPropDisplayName1() {
        String displayName = (String) mappingTable.prop_get(OrmModelConstants.ORM_MAPPING_PROP_DISPLAY_NAME1);
        return displayName;
    }

    public String getRefPropDisplayName2() {
        String displayName = (String) mappingTable.prop_get(OrmModelConstants.ORM_MAPPING_PROP_DISPLAY_NAME2);
        return displayName;
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

    public String getRefPropName1_label() {
        return getRefPropName1() + "_label";
    }

    public String getRefPropName1_ids() {
        return getRefPropName1() + "_ids";
    }

    public String getRefPropName2_label() {
        return getRefPropName2() + "_label";
    }

    public String getRefPropName2_ids() {
        return getRefPropName2() + "_ids";
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

    public String getRefLabelProp1() {
        String labelProp = refProp1.getRefEntityModel().getLabelProp();
        if (StringHelper.isEmpty(labelProp))
            labelProp = OrmModelConstants.PROP_ID;
        return labelProp;
    }

    public String getRefLabelProp2() {
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