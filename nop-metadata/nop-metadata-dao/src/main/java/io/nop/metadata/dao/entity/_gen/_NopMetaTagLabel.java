package io.nop.metadata.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.metadata.dao.entity.NopMetaTagLabel;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  语义标注: nop_meta_tag_label
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaTagLabel extends DynamicOrmEntity{
    
    /* 标注ID: TAG_LABEL_ID VARCHAR */
    public static final String PROP_NAME_tagLabelId = "tagLabelId";
    public static final int PROP_ID_tagLabelId = 1;
    
    /* 标注来源: SOURCE VARCHAR */
    public static final String PROP_NAME_source = "source";
    public static final int PROP_ID_source = 2;
    
    /* 标签ID: TAG_ID VARCHAR */
    public static final String PROP_NAME_tagId = "tagId";
    public static final int PROP_ID_tagId = 3;
    
    /* 业务术语ID: GLOSSARY_TERM_ID VARCHAR */
    public static final String PROP_NAME_glossaryTermId = "glossaryTermId";
    public static final int PROP_ID_glossaryTermId = 4;
    
    /* 标注类型: LABEL_TYPE VARCHAR */
    public static final String PROP_NAME_labelType = "labelType";
    public static final int PROP_ID_labelType = 5;
    
    /* 标注状态: STATE VARCHAR */
    public static final String PROP_NAME_state = "state";
    public static final int PROP_ID_state = 6;
    
    /* 资产类型: ENTITY_TYPE VARCHAR */
    public static final String PROP_NAME_entityType = "entityType";
    public static final int PROP_ID_entityType = 7;
    
    /* 资产ID: ENTITY_ID VARCHAR */
    public static final String PROP_NAME_entityId = "entityId";
    public static final int PROP_ID_entityId = 8;
    
    /* 标注人: APPLIED_BY VARCHAR */
    public static final String PROP_NAME_appliedBy = "appliedBy";
    public static final int PROP_ID_appliedBy = 9;
    
    /* 标注时间: APPLIED_AT TIMESTAMP */
    public static final String PROP_NAME_appliedAt = "appliedAt";
    public static final int PROP_ID_appliedAt = 10;
    
    /* 标注理由: REASON VARCHAR */
    public static final String PROP_NAME_reason = "reason";
    public static final int PROP_ID_reason = 11;
    
    /* 扩展元数据: METADATA VARCHAR */
    public static final String PROP_NAME_metadata = "metadata";
    public static final int PROP_ID_metadata = 12;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 13;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 14;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 15;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 16;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 17;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 18;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 19;
    

    private static int _PROP_ID_BOUND = 20;

    
    /* relation: 标签 */
    public static final String PROP_NAME_tag = "tag";
    
    /* component:  */
    public static final String PROP_NAME_metadataComponent = "metadataComponent";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_tagLabelId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_tagLabelId};

    private static final String[] PROP_ID_TO_NAME = new String[20];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_tagLabelId] = PROP_NAME_tagLabelId;
          PROP_NAME_TO_ID.put(PROP_NAME_tagLabelId, PROP_ID_tagLabelId);
      
          PROP_ID_TO_NAME[PROP_ID_source] = PROP_NAME_source;
          PROP_NAME_TO_ID.put(PROP_NAME_source, PROP_ID_source);
      
          PROP_ID_TO_NAME[PROP_ID_tagId] = PROP_NAME_tagId;
          PROP_NAME_TO_ID.put(PROP_NAME_tagId, PROP_ID_tagId);
      
          PROP_ID_TO_NAME[PROP_ID_glossaryTermId] = PROP_NAME_glossaryTermId;
          PROP_NAME_TO_ID.put(PROP_NAME_glossaryTermId, PROP_ID_glossaryTermId);
      
          PROP_ID_TO_NAME[PROP_ID_labelType] = PROP_NAME_labelType;
          PROP_NAME_TO_ID.put(PROP_NAME_labelType, PROP_ID_labelType);
      
          PROP_ID_TO_NAME[PROP_ID_state] = PROP_NAME_state;
          PROP_NAME_TO_ID.put(PROP_NAME_state, PROP_ID_state);
      
          PROP_ID_TO_NAME[PROP_ID_entityType] = PROP_NAME_entityType;
          PROP_NAME_TO_ID.put(PROP_NAME_entityType, PROP_ID_entityType);
      
          PROP_ID_TO_NAME[PROP_ID_entityId] = PROP_NAME_entityId;
          PROP_NAME_TO_ID.put(PROP_NAME_entityId, PROP_ID_entityId);
      
          PROP_ID_TO_NAME[PROP_ID_appliedBy] = PROP_NAME_appliedBy;
          PROP_NAME_TO_ID.put(PROP_NAME_appliedBy, PROP_ID_appliedBy);
      
          PROP_ID_TO_NAME[PROP_ID_appliedAt] = PROP_NAME_appliedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_appliedAt, PROP_ID_appliedAt);
      
          PROP_ID_TO_NAME[PROP_ID_reason] = PROP_NAME_reason;
          PROP_NAME_TO_ID.put(PROP_NAME_reason, PROP_ID_reason);
      
          PROP_ID_TO_NAME[PROP_ID_metadata] = PROP_NAME_metadata;
          PROP_NAME_TO_ID.put(PROP_NAME_metadata, PROP_ID_metadata);
      
          PROP_ID_TO_NAME[PROP_ID_extConfig] = PROP_NAME_extConfig;
          PROP_NAME_TO_ID.put(PROP_NAME_extConfig, PROP_ID_extConfig);
      
          PROP_ID_TO_NAME[PROP_ID_version] = PROP_NAME_version;
          PROP_NAME_TO_ID.put(PROP_NAME_version, PROP_ID_version);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
          PROP_ID_TO_NAME[PROP_ID_updatedBy] = PROP_NAME_updatedBy;
          PROP_NAME_TO_ID.put(PROP_NAME_updatedBy, PROP_ID_updatedBy);
      
          PROP_ID_TO_NAME[PROP_ID_updateTime] = PROP_NAME_updateTime;
          PROP_NAME_TO_ID.put(PROP_NAME_updateTime, PROP_ID_updateTime);
      
          PROP_ID_TO_NAME[PROP_ID_remark] = PROP_NAME_remark;
          PROP_NAME_TO_ID.put(PROP_NAME_remark, PROP_ID_remark);
      
    }

    
    /* 标注ID: TAG_LABEL_ID */
    private java.lang.String _tagLabelId;
    
    /* 标注来源: SOURCE */
    private java.lang.String _source;
    
    /* 标签ID: TAG_ID */
    private java.lang.String _tagId;
    
    /* 业务术语ID: GLOSSARY_TERM_ID */
    private java.lang.String _glossaryTermId;
    
    /* 标注类型: LABEL_TYPE */
    private java.lang.String _labelType;
    
    /* 标注状态: STATE */
    private java.lang.String _state;
    
    /* 资产类型: ENTITY_TYPE */
    private java.lang.String _entityType;
    
    /* 资产ID: ENTITY_ID */
    private java.lang.String _entityId;
    
    /* 标注人: APPLIED_BY */
    private java.lang.String _appliedBy;
    
    /* 标注时间: APPLIED_AT */
    private java.sql.Timestamp _appliedAt;
    
    /* 标注理由: REASON */
    private java.lang.String _reason;
    
    /* 扩展元数据: METADATA */
    private java.lang.String _metadata;
    
    /* 扩展配置: EXT_CONFIG */
    private java.lang.String _extConfig;
    
    /* 数据版本: VERSION */
    private java.lang.Long _version;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 修改人: UPDATED_BY */
    private java.lang.String _updatedBy;
    
    /* 修改时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    
    /* 备注: REMARK */
    private java.lang.String _remark;
    

    public _NopMetaTagLabel(){
        // for debug
    }

    protected NopMetaTagLabel newInstance(){
        NopMetaTagLabel entity = new NopMetaTagLabel();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaTagLabel cloneInstance() {
        NopMetaTagLabel entity = newInstance();
        orm_forEachInitedProp((value, propId) -> {
            entity.orm_propValue(propId,value);
        });
        return entity;
    }

    @Override
    public String orm_entityName() {
      // 如果存在实体模型对象，则以模型对象上的设置为准
      IEntityModel entityModel = orm_entityModel();
      if(entityModel != null)
          return entityModel.getName();
      return "io.nop.metadata.dao.entity.NopMetaTagLabel";
    }

    @Override
    public int orm_propIdBound(){
      IEntityModel entityModel = orm_entityModel();
      if(entityModel != null)
          return entityModel.getPropIdBound();
      return _PROP_ID_BOUND;
    }

    @Override
    public Object orm_id() {
    
        return buildSimpleId(PROP_ID_tagLabelId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_tagLabelId;
          
    }

    @Override
    public String orm_propName(int propId) {
        if(propId >= PROP_ID_TO_NAME.length)
            return super.orm_propName(propId);
        String propName = PROP_ID_TO_NAME[propId];
        if(propName == null)
           return super.orm_propName(propId);
        return propName;
    }

    @Override
    public int orm_propId(String propName) {
        Integer propId = PROP_NAME_TO_ID.get(propName);
        if(propId == null)
            return super.orm_propId(propName);
        return propId;
    }

    @Override
    public Object orm_propValue(int propId) {
        switch(propId){
        
            case PROP_ID_tagLabelId:
               return getTagLabelId();
        
            case PROP_ID_source:
               return getSource();
        
            case PROP_ID_tagId:
               return getTagId();
        
            case PROP_ID_glossaryTermId:
               return getGlossaryTermId();
        
            case PROP_ID_labelType:
               return getLabelType();
        
            case PROP_ID_state:
               return getState();
        
            case PROP_ID_entityType:
               return getEntityType();
        
            case PROP_ID_entityId:
               return getEntityId();
        
            case PROP_ID_appliedBy:
               return getAppliedBy();
        
            case PROP_ID_appliedAt:
               return getAppliedAt();
        
            case PROP_ID_reason:
               return getReason();
        
            case PROP_ID_metadata:
               return getMetadata();
        
            case PROP_ID_extConfig:
               return getExtConfig();
        
            case PROP_ID_version:
               return getVersion();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
            case PROP_ID_updatedBy:
               return getUpdatedBy();
        
            case PROP_ID_updateTime:
               return getUpdateTime();
        
            case PROP_ID_remark:
               return getRemark();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_tagLabelId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tagLabelId));
               }
               setTagLabelId(typedValue);
               break;
            }
        
            case PROP_ID_source:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_source));
               }
               setSource(typedValue);
               break;
            }
        
            case PROP_ID_tagId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tagId));
               }
               setTagId(typedValue);
               break;
            }
        
            case PROP_ID_glossaryTermId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_glossaryTermId));
               }
               setGlossaryTermId(typedValue);
               break;
            }
        
            case PROP_ID_labelType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_labelType));
               }
               setLabelType(typedValue);
               break;
            }
        
            case PROP_ID_state:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_state));
               }
               setState(typedValue);
               break;
            }
        
            case PROP_ID_entityType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entityType));
               }
               setEntityType(typedValue);
               break;
            }
        
            case PROP_ID_entityId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entityId));
               }
               setEntityId(typedValue);
               break;
            }
        
            case PROP_ID_appliedBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_appliedBy));
               }
               setAppliedBy(typedValue);
               break;
            }
        
            case PROP_ID_appliedAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_appliedAt));
               }
               setAppliedAt(typedValue);
               break;
            }
        
            case PROP_ID_reason:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_reason));
               }
               setReason(typedValue);
               break;
            }
        
            case PROP_ID_metadata:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metadata));
               }
               setMetadata(typedValue);
               break;
            }
        
            case PROP_ID_extConfig:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_extConfig));
               }
               setExtConfig(typedValue);
               break;
            }
        
            case PROP_ID_version:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_version));
               }
               setVersion(typedValue);
               break;
            }
        
            case PROP_ID_createdBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_createdBy));
               }
               setCreatedBy(typedValue);
               break;
            }
        
            case PROP_ID_createTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_createTime));
               }
               setCreateTime(typedValue);
               break;
            }
        
            case PROP_ID_updatedBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_updatedBy));
               }
               setUpdatedBy(typedValue);
               break;
            }
        
            case PROP_ID_updateTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_updateTime));
               }
               setUpdateTime(typedValue);
               break;
            }
        
            case PROP_ID_remark:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_remark));
               }
               setRemark(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_tagLabelId:{
               onInitProp(propId);
               this._tagLabelId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_source:{
               onInitProp(propId);
               this._source = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_tagId:{
               onInitProp(propId);
               this._tagId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_glossaryTermId:{
               onInitProp(propId);
               this._glossaryTermId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_labelType:{
               onInitProp(propId);
               this._labelType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_state:{
               onInitProp(propId);
               this._state = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entityType:{
               onInitProp(propId);
               this._entityType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entityId:{
               onInitProp(propId);
               this._entityId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_appliedBy:{
               onInitProp(propId);
               this._appliedBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_appliedAt:{
               onInitProp(propId);
               this._appliedAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_reason:{
               onInitProp(propId);
               this._reason = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_metadata:{
               onInitProp(propId);
               this._metadata = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_extConfig:{
               onInitProp(propId);
               this._extConfig = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_version:{
               onInitProp(propId);
               this._version = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_createdBy:{
               onInitProp(propId);
               this._createdBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_createTime:{
               onInitProp(propId);
               this._createTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_updatedBy:{
               onInitProp(propId);
               this._updatedBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_updateTime:{
               onInitProp(propId);
               this._updateTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_remark:{
               onInitProp(propId);
               this._remark = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 标注ID: TAG_LABEL_ID
     */
    public final java.lang.String getTagLabelId(){
         onPropGet(PROP_ID_tagLabelId);
         return _tagLabelId;
    }

    /**
     * 标注ID: TAG_LABEL_ID
     */
    public final void setTagLabelId(java.lang.String value){
        if(onPropSet(PROP_ID_tagLabelId,value)){
            this._tagLabelId = value;
            internalClearRefs(PROP_ID_tagLabelId);
            orm_id();
        }
    }
    
    /**
     * 标注来源: SOURCE
     */
    public final java.lang.String getSource(){
         onPropGet(PROP_ID_source);
         return _source;
    }

    /**
     * 标注来源: SOURCE
     */
    public final void setSource(java.lang.String value){
        if(onPropSet(PROP_ID_source,value)){
            this._source = value;
            internalClearRefs(PROP_ID_source);
            
        }
    }
    
    /**
     * 标签ID: TAG_ID
     */
    public final java.lang.String getTagId(){
         onPropGet(PROP_ID_tagId);
         return _tagId;
    }

    /**
     * 标签ID: TAG_ID
     */
    public final void setTagId(java.lang.String value){
        if(onPropSet(PROP_ID_tagId,value)){
            this._tagId = value;
            internalClearRefs(PROP_ID_tagId);
            
        }
    }
    
    /**
     * 业务术语ID: GLOSSARY_TERM_ID
     */
    public final java.lang.String getGlossaryTermId(){
         onPropGet(PROP_ID_glossaryTermId);
         return _glossaryTermId;
    }

    /**
     * 业务术语ID: GLOSSARY_TERM_ID
     */
    public final void setGlossaryTermId(java.lang.String value){
        if(onPropSet(PROP_ID_glossaryTermId,value)){
            this._glossaryTermId = value;
            internalClearRefs(PROP_ID_glossaryTermId);
            
        }
    }
    
    /**
     * 标注类型: LABEL_TYPE
     */
    public final java.lang.String getLabelType(){
         onPropGet(PROP_ID_labelType);
         return _labelType;
    }

    /**
     * 标注类型: LABEL_TYPE
     */
    public final void setLabelType(java.lang.String value){
        if(onPropSet(PROP_ID_labelType,value)){
            this._labelType = value;
            internalClearRefs(PROP_ID_labelType);
            
        }
    }
    
    /**
     * 标注状态: STATE
     */
    public final java.lang.String getState(){
         onPropGet(PROP_ID_state);
         return _state;
    }

    /**
     * 标注状态: STATE
     */
    public final void setState(java.lang.String value){
        if(onPropSet(PROP_ID_state,value)){
            this._state = value;
            internalClearRefs(PROP_ID_state);
            
        }
    }
    
    /**
     * 资产类型: ENTITY_TYPE
     */
    public final java.lang.String getEntityType(){
         onPropGet(PROP_ID_entityType);
         return _entityType;
    }

    /**
     * 资产类型: ENTITY_TYPE
     */
    public final void setEntityType(java.lang.String value){
        if(onPropSet(PROP_ID_entityType,value)){
            this._entityType = value;
            internalClearRefs(PROP_ID_entityType);
            
        }
    }
    
    /**
     * 资产ID: ENTITY_ID
     */
    public final java.lang.String getEntityId(){
         onPropGet(PROP_ID_entityId);
         return _entityId;
    }

    /**
     * 资产ID: ENTITY_ID
     */
    public final void setEntityId(java.lang.String value){
        if(onPropSet(PROP_ID_entityId,value)){
            this._entityId = value;
            internalClearRefs(PROP_ID_entityId);
            
        }
    }
    
    /**
     * 标注人: APPLIED_BY
     */
    public final java.lang.String getAppliedBy(){
         onPropGet(PROP_ID_appliedBy);
         return _appliedBy;
    }

    /**
     * 标注人: APPLIED_BY
     */
    public final void setAppliedBy(java.lang.String value){
        if(onPropSet(PROP_ID_appliedBy,value)){
            this._appliedBy = value;
            internalClearRefs(PROP_ID_appliedBy);
            
        }
    }
    
    /**
     * 标注时间: APPLIED_AT
     */
    public final java.sql.Timestamp getAppliedAt(){
         onPropGet(PROP_ID_appliedAt);
         return _appliedAt;
    }

    /**
     * 标注时间: APPLIED_AT
     */
    public final void setAppliedAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_appliedAt,value)){
            this._appliedAt = value;
            internalClearRefs(PROP_ID_appliedAt);
            
        }
    }
    
    /**
     * 标注理由: REASON
     */
    public final java.lang.String getReason(){
         onPropGet(PROP_ID_reason);
         return _reason;
    }

    /**
     * 标注理由: REASON
     */
    public final void setReason(java.lang.String value){
        if(onPropSet(PROP_ID_reason,value)){
            this._reason = value;
            internalClearRefs(PROP_ID_reason);
            
        }
    }
    
    /**
     * 扩展元数据: METADATA
     */
    public final java.lang.String getMetadata(){
         onPropGet(PROP_ID_metadata);
         return _metadata;
    }

    /**
     * 扩展元数据: METADATA
     */
    public final void setMetadata(java.lang.String value){
        if(onPropSet(PROP_ID_metadata,value)){
            this._metadata = value;
            internalClearRefs(PROP_ID_metadata);
            
        }
    }
    
    /**
     * 扩展配置: EXT_CONFIG
     */
    public final java.lang.String getExtConfig(){
         onPropGet(PROP_ID_extConfig);
         return _extConfig;
    }

    /**
     * 扩展配置: EXT_CONFIG
     */
    public final void setExtConfig(java.lang.String value){
        if(onPropSet(PROP_ID_extConfig,value)){
            this._extConfig = value;
            internalClearRefs(PROP_ID_extConfig);
            
        }
    }
    
    /**
     * 数据版本: VERSION
     */
    public final java.lang.Long getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
     */
    public final void setVersion(java.lang.Long value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
        }
    }
    
    /**
     * 创建人: CREATED_BY
     */
    public final java.lang.String getCreatedBy(){
         onPropGet(PROP_ID_createdBy);
         return _createdBy;
    }

    /**
     * 创建人: CREATED_BY
     */
    public final void setCreatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_createdBy,value)){
            this._createdBy = value;
            internalClearRefs(PROP_ID_createdBy);
            
        }
    }
    
    /**
     * 创建时间: CREATE_TIME
     */
    public final java.sql.Timestamp getCreateTime(){
         onPropGet(PROP_ID_createTime);
         return _createTime;
    }

    /**
     * 创建时间: CREATE_TIME
     */
    public final void setCreateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_createTime,value)){
            this._createTime = value;
            internalClearRefs(PROP_ID_createTime);
            
        }
    }
    
    /**
     * 修改人: UPDATED_BY
     */
    public final java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 修改人: UPDATED_BY
     */
    public final void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
        }
    }
    
    /**
     * 修改时间: UPDATE_TIME
     */
    public final java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 修改时间: UPDATE_TIME
     */
    public final void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
        }
    }
    
    /**
     * 备注: REMARK
     */
    public final java.lang.String getRemark(){
         onPropGet(PROP_ID_remark);
         return _remark;
    }

    /**
     * 备注: REMARK
     */
    public final void setRemark(java.lang.String value){
        if(onPropSet(PROP_ID_remark,value)){
            this._remark = value;
            internalClearRefs(PROP_ID_remark);
            
        }
    }
    
    /**
     * 标签
     */
    public final io.nop.metadata.dao.entity.NopMetaTag getTag(){
       return (io.nop.metadata.dao.entity.NopMetaTag)internalGetRefEntity(PROP_NAME_tag);
    }

    public final void setTag(io.nop.metadata.dao.entity.NopMetaTag refEntity){
   
           if(refEntity == null){
           
                   this.setTagId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_tag, refEntity,()->{
           
                           this.setTagId(refEntity.getTagId());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _metadataComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_metadataComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_metadataComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_metadata);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getMetadataComponent(){
      if(_metadataComponent == null){
          _metadataComponent = new io.nop.orm.component.JsonOrmComponent();
          _metadataComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_metadataComponent);
      }
      return _metadataComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _extConfigComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_extConfigComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_extConfigComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_extConfig);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getExtConfigComponent(){
      if(_extConfigComponent == null){
          _extConfigComponent = new io.nop.orm.component.JsonOrmComponent();
          _extConfigComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_extConfigComponent);
      }
      return _extConfigComponent;
   }

}
// resume CPD analysis - CPD-ON
