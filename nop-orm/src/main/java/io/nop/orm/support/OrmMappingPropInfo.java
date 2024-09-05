package io.nop.orm.support;

import io.nop.commons.util.StringHelper;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.OrmModelConstants;

public class OrmMappingPropInfo {
    private final String propName;
    private final String displayName;
    private final String enDisplayName;
    private final String bizObjName;
    private final boolean toMany;
    private final String mappingLabelProp;
    private final String mappingIdProp;
    private final IEntityRelationModel refPropInMappingTable;
    private final String bizModuleId;

    public OrmMappingPropInfo(String bizObjName, String propName,
                              String displayName, String enDisplayName, boolean toMany,
                              String mappingLabelProp, String mappingIdProp, IEntityRelationModel refPropInMappingTable,
                              String bizModuleId) {
        this.bizObjName = bizObjName;
        this.displayName = displayName;
        this.enDisplayName = enDisplayName;
        this.propName = propName;
        this.toMany = toMany;
        this.mappingLabelProp = mappingLabelProp;
        this.mappingIdProp = mappingIdProp;
        this.refPropInMappingTable = refPropInMappingTable;
        this.bizModuleId = bizModuleId;
    }

    public IEntityRelationModel getRefPropInMappingTable() {
        return refPropInMappingTable;
    }

    public String getRefPropNameInMappingTable() {
        return refPropInMappingTable.getName();
    }

    public String getRefPropLabelInMappingTable() {
        String labelProp = refPropInMappingTable.getRefEntityModel().getLabelProp();
        if (StringHelper.isEmpty(labelProp))
            labelProp = OrmModelConstants.PROP_ID;
        return getRefPropNameInMappingTable() + "." + labelProp;
    }

    public String getRefLabelProp(){
        String labelProp = refPropInMappingTable.getRefEntityModel().getLabelProp();
        if (StringHelper.isEmpty(labelProp))
            labelProp = OrmModelConstants.PROP_ID;
        return labelProp;
    }

    public String getRefClassName() {
        return refPropInMappingTable.getRefEntityModel().getClassName();
    }

    public String getRefEntityName() {
        return refPropInMappingTable.getRefEntityName();
    }

    public String getEnDisplayName() {
        return enDisplayName;
    }

    public String getBizModuleId() {
        return bizModuleId;
    }

    public String getMappingIdProp() {
        return mappingIdProp;
    }

    public String getMappingLabelProp() {
        return mappingLabelProp;
    }

    public String getBizObjName() {
        return bizObjName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPropName() {
        return propName;
    }

    public boolean isToMany() {
        return toMany;
    }
}
