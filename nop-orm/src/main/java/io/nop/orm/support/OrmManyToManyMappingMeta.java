package io.nop.orm.support;

import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.orm.OrmConstants;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.orm.model.OrmRelationType;

import java.util.List;

import static io.nop.core.reflect.utils.BeanReflectHelper.getValueByFactoryMethod;

public class OrmManyToManyMappingMeta {
    private final IEntityModel mappingTable;
    private final IEntityRelationModel refProp1;
    private final IEntityRelationModel refProp2;

    public OrmManyToManyMappingMeta(IEntityModel mappingTable) {
        this.mappingTable = mappingTable;
        List<? extends IEntityRelationModel> rels = mappingTable.getToOneRelations();
        this.refProp1 = CollectionHelper.first(rels);
        this.refProp2 = CollectionHelper.last(rels);
    }

    public boolean isOneToOne() {
        return getRelationType() == OrmRelationType.o2o;
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

    public String getRefSetPropName1() {
        String refSetName = (String) mappingTable.prop_get(OrmModelConstants.ORM_MANY_TO_MANY_REF_SET_NAME1);
        if (StringHelper.isEmpty(refSetName))
            refSetName = "related" + StringHelper.capitalize(refProp2.getName()) + "List";
        return refSetName;
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

    public String getRefSetPropName1_label() {
        return getRefSetPropName1() + "_label";
    }

    public String getRefSetPropName1_ids() {
        return getRefSetPropName1() + "_ids";
    }

    public String getRefSetPropName2() {
        String refSetName = (String) mappingTable.prop_get(OrmModelConstants.ORM_MANY_TO_MANY_REF_SET_NAME2);
        if (StringHelper.isEmpty(refSetName))
            refSetName = "related" + StringHelper.capitalize(refProp1.getName()) + "List";
        return refSetName;
    }

    public String getRefSetPropName2_label() {
        return getRefSetPropName2() + "_label";
    }

    public String getRefSetPropName2_ids() {
        return getRefSetPropName2() + "_ids";
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

    public String getRefLabelPropName1() {
        String labelProp = refProp1.getRefEntityModel().getLabelProp();
        if (StringHelper.isEmpty(labelProp))
            labelProp = OrmModelConstants.PROP_ID;
        return labelProp;
    }

    public String getRefLabelPropName2() {
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

    public OrmRelationType getRelationType() {
        return getValueByFactoryMethod(OrmRelationType.class, mappingTable,
                OrmConstants.EXT_ORM_RELATION_TYPE);
    }
}