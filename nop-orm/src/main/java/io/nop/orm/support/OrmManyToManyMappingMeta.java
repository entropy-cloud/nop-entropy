package io.nop.orm.support;

import io.nop.commons.util.CollectionHelper;
import io.nop.orm.OrmConstants;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.OrmRelationType;

import java.util.List;

import static io.nop.core.reflect.utils.BeanReflectHelper.getValueByFactoryMethod;

public class OrmManyToManyMappingMeta {
    private final IEntityModel mappingTable;
    private final IEntityPropModel refProp1;
    private final IEntityPropModel refProp2;

    public OrmManyToManyMappingMeta(IEntityModel mappingTable) {
        this.mappingTable = mappingTable;
        List<? extends IEntityRelationModel> rels = mappingTable.getToOneRelations();
        this.refProp1 = CollectionHelper.first(rels);
        this.refProp2 = CollectionHelper.last(rels);
    }

    public IEntityModel getMappingTable() {
        return mappingTable;
    }

    public IEntityPropModel getRefProp1() {
        return refProp1;
    }

    public IEntityPropModel getRefProp2() {
        return refProp2;
    }

    public String getRefEntityName1() {
        return refProp1.getOwnerEntityModel().getName();
    }

    public String getRefEntityName2() {
        return refProp2.getOwnerEntityModel().getName();
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