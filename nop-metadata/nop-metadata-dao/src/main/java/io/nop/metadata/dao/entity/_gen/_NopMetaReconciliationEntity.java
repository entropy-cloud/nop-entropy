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

import io.nop.metadata.dao.entity.NopMetaReconciliationEntity;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  对账实体: nop_meta_recon_entity
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaReconciliationEntity extends DynamicOrmEntity{
    
    /* 对账实体ID: RECON_ENTITY_ID VARCHAR */
    public static final String PROP_NAME_reconEntityId = "reconEntityId";
    public static final int PROP_ID_reconEntityId = 1;
    
    /* 实体ID: ENTITY_ID VARCHAR */
    public static final String PROP_NAME_entityId = "entityId";
    public static final int PROP_ID_entityId = 2;
    
    /* 实体名: ENTITY_NAME VARCHAR */
    public static final String PROP_NAME_entityName = "entityName";
    public static final int PROP_ID_entityName = 3;
    
    /* 实体类型: ENTITY_TYPE VARCHAR */
    public static final String PROP_NAME_entityType = "entityType";
    public static final int PROP_ID_entityType = 4;
    
    /* 标识符空间: IDENTIFIER_SPACE VARCHAR */
    public static final String PROP_NAME_identifierSpace = "identifierSpace";
    public static final int PROP_ID_identifierSpace = 5;
    
    /* 实体属性: PROPERTIES VARCHAR */
    public static final String PROP_NAME_properties = "properties";
    public static final int PROP_ID_properties = 6;
    
    /* 最后同步时间: LAST_SYNCED_AT TIMESTAMP */
    public static final String PROP_NAME_lastSyncedAt = "lastSyncedAt";
    public static final int PROP_ID_lastSyncedAt = 7;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 8;
    
    /* 数据版本: DEL_VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 9;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 10;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 11;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 12;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 13;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 14;
    

    private static int _PROP_ID_BOUND = 15;

    
    /* component:  */
    public static final String PROP_NAME_propertiesComponent = "propertiesComponent";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_reconEntityId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_reconEntityId};

    private static final String[] PROP_ID_TO_NAME = new String[15];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_reconEntityId] = PROP_NAME_reconEntityId;
          PROP_NAME_TO_ID.put(PROP_NAME_reconEntityId, PROP_ID_reconEntityId);
      
          PROP_ID_TO_NAME[PROP_ID_entityId] = PROP_NAME_entityId;
          PROP_NAME_TO_ID.put(PROP_NAME_entityId, PROP_ID_entityId);
      
          PROP_ID_TO_NAME[PROP_ID_entityName] = PROP_NAME_entityName;
          PROP_NAME_TO_ID.put(PROP_NAME_entityName, PROP_ID_entityName);
      
          PROP_ID_TO_NAME[PROP_ID_entityType] = PROP_NAME_entityType;
          PROP_NAME_TO_ID.put(PROP_NAME_entityType, PROP_ID_entityType);
      
          PROP_ID_TO_NAME[PROP_ID_identifierSpace] = PROP_NAME_identifierSpace;
          PROP_NAME_TO_ID.put(PROP_NAME_identifierSpace, PROP_ID_identifierSpace);
      
          PROP_ID_TO_NAME[PROP_ID_properties] = PROP_NAME_properties;
          PROP_NAME_TO_ID.put(PROP_NAME_properties, PROP_ID_properties);
      
          PROP_ID_TO_NAME[PROP_ID_lastSyncedAt] = PROP_NAME_lastSyncedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_lastSyncedAt, PROP_ID_lastSyncedAt);
      
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

    
    /* 对账实体ID: RECON_ENTITY_ID */
    private java.lang.String _reconEntityId;
    
    /* 实体ID: ENTITY_ID */
    private java.lang.String _entityId;
    
    /* 实体名: ENTITY_NAME */
    private java.lang.String _entityName;
    
    /* 实体类型: ENTITY_TYPE */
    private java.lang.String _entityType;
    
    /* 标识符空间: IDENTIFIER_SPACE */
    private java.lang.String _identifierSpace;
    
    /* 实体属性: PROPERTIES */
    private java.lang.String _properties;
    
    /* 最后同步时间: LAST_SYNCED_AT */
    private java.sql.Timestamp _lastSyncedAt;
    
    /* 扩展配置: EXT_CONFIG */
    private java.lang.String _extConfig;
    
    /* 数据版本: DEL_VERSION */
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
    

    public _NopMetaReconciliationEntity(){
        // for debug
    }

    protected NopMetaReconciliationEntity newInstance(){
        NopMetaReconciliationEntity entity = new NopMetaReconciliationEntity();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaReconciliationEntity cloneInstance() {
        NopMetaReconciliationEntity entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaReconciliationEntity";
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
    
        return buildSimpleId(PROP_ID_reconEntityId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_reconEntityId;
          
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
        
            case PROP_ID_reconEntityId:
               return getReconEntityId();
        
            case PROP_ID_entityId:
               return getEntityId();
        
            case PROP_ID_entityName:
               return getEntityName();
        
            case PROP_ID_entityType:
               return getEntityType();
        
            case PROP_ID_identifierSpace:
               return getIdentifierSpace();
        
            case PROP_ID_properties:
               return getProperties();
        
            case PROP_ID_lastSyncedAt:
               return getLastSyncedAt();
        
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
        
            case PROP_ID_reconEntityId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_reconEntityId));
               }
               setReconEntityId(typedValue);
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
        
            case PROP_ID_entityName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entityName));
               }
               setEntityName(typedValue);
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
        
            case PROP_ID_identifierSpace:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_identifierSpace));
               }
               setIdentifierSpace(typedValue);
               break;
            }
        
            case PROP_ID_properties:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_properties));
               }
               setProperties(typedValue);
               break;
            }
        
            case PROP_ID_lastSyncedAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_lastSyncedAt));
               }
               setLastSyncedAt(typedValue);
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
        
            case PROP_ID_reconEntityId:{
               onInitProp(propId);
               this._reconEntityId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_entityId:{
               onInitProp(propId);
               this._entityId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entityName:{
               onInitProp(propId);
               this._entityName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entityType:{
               onInitProp(propId);
               this._entityType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_identifierSpace:{
               onInitProp(propId);
               this._identifierSpace = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_properties:{
               onInitProp(propId);
               this._properties = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_lastSyncedAt:{
               onInitProp(propId);
               this._lastSyncedAt = (java.sql.Timestamp)value;
               
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
     * 对账实体ID: RECON_ENTITY_ID
     */
    public final java.lang.String getReconEntityId(){
         onPropGet(PROP_ID_reconEntityId);
         return _reconEntityId;
    }

    /**
     * 对账实体ID: RECON_ENTITY_ID
     */
    public final void setReconEntityId(java.lang.String value){
        if(onPropSet(PROP_ID_reconEntityId,value)){
            this._reconEntityId = value;
            internalClearRefs(PROP_ID_reconEntityId);
            orm_id();
        }
    }
    
    /**
     * 实体ID: ENTITY_ID
     */
    public final java.lang.String getEntityId(){
         onPropGet(PROP_ID_entityId);
         return _entityId;
    }

    /**
     * 实体ID: ENTITY_ID
     */
    public final void setEntityId(java.lang.String value){
        if(onPropSet(PROP_ID_entityId,value)){
            this._entityId = value;
            internalClearRefs(PROP_ID_entityId);
            
        }
    }
    
    /**
     * 实体名: ENTITY_NAME
     */
    public final java.lang.String getEntityName(){
         onPropGet(PROP_ID_entityName);
         return _entityName;
    }

    /**
     * 实体名: ENTITY_NAME
     */
    public final void setEntityName(java.lang.String value){
        if(onPropSet(PROP_ID_entityName,value)){
            this._entityName = value;
            internalClearRefs(PROP_ID_entityName);
            
        }
    }
    
    /**
     * 实体类型: ENTITY_TYPE
     */
    public final java.lang.String getEntityType(){
         onPropGet(PROP_ID_entityType);
         return _entityType;
    }

    /**
     * 实体类型: ENTITY_TYPE
     */
    public final void setEntityType(java.lang.String value){
        if(onPropSet(PROP_ID_entityType,value)){
            this._entityType = value;
            internalClearRefs(PROP_ID_entityType);
            
        }
    }
    
    /**
     * 标识符空间: IDENTIFIER_SPACE
     */
    public final java.lang.String getIdentifierSpace(){
         onPropGet(PROP_ID_identifierSpace);
         return _identifierSpace;
    }

    /**
     * 标识符空间: IDENTIFIER_SPACE
     */
    public final void setIdentifierSpace(java.lang.String value){
        if(onPropSet(PROP_ID_identifierSpace,value)){
            this._identifierSpace = value;
            internalClearRefs(PROP_ID_identifierSpace);
            
        }
    }
    
    /**
     * 实体属性: PROPERTIES
     */
    public final java.lang.String getProperties(){
         onPropGet(PROP_ID_properties);
         return _properties;
    }

    /**
     * 实体属性: PROPERTIES
     */
    public final void setProperties(java.lang.String value){
        if(onPropSet(PROP_ID_properties,value)){
            this._properties = value;
            internalClearRefs(PROP_ID_properties);
            
        }
    }
    
    /**
     * 最后同步时间: LAST_SYNCED_AT
     */
    public final java.sql.Timestamp getLastSyncedAt(){
         onPropGet(PROP_ID_lastSyncedAt);
         return _lastSyncedAt;
    }

    /**
     * 最后同步时间: LAST_SYNCED_AT
     */
    public final void setLastSyncedAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_lastSyncedAt,value)){
            this._lastSyncedAt = value;
            internalClearRefs(PROP_ID_lastSyncedAt);
            
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
     * 数据版本: DEL_VERSION
     */
    public final java.lang.Long getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: DEL_VERSION
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
    
   private io.nop.orm.component.JsonOrmComponent _propertiesComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_propertiesComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_propertiesComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_properties);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getPropertiesComponent(){
      if(_propertiesComponent == null){
          _propertiesComponent = new io.nop.orm.component.JsonOrmComponent();
          _propertiesComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_propertiesComponent);
      }
      return _propertiesComponent;
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
