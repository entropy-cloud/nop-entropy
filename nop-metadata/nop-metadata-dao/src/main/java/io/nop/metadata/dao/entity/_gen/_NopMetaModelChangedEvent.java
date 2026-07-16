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

import io.nop.metadata.dao.entity.NopMetaModelChangedEvent;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  元数据变更事件: nop_meta_model_changed_event
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaModelChangedEvent extends DynamicOrmEntity{
    
    /* 事件ID: MODEL_CHANGED_EVENT_ID VARCHAR */
    public static final String PROP_NAME_modelChangedEventId = "modelChangedEventId";
    public static final int PROP_ID_modelChangedEventId = 1;
    
    /* 事件类型: EVENT_TYPE VARCHAR */
    public static final String PROP_NAME_eventType = "eventType";
    public static final int PROP_ID_eventType = 2;
    
    /* 实体类型: ENTITY_TYPE VARCHAR */
    public static final String PROP_NAME_entityType = "entityType";
    public static final int PROP_ID_entityType = 3;
    
    /* 实体ID: ENTITY_ID VARCHAR */
    public static final String PROP_NAME_entityId = "entityId";
    public static final int PROP_ID_entityId = 4;
    
    /* 实体名称: ENTITY_NAME VARCHAR */
    public static final String PROP_NAME_entityName = "entityName";
    public static final int PROP_ID_entityName = 5;
    
    /* 变更来源: CHANGE_SOURCE VARCHAR */
    public static final String PROP_NAME_changeSource = "changeSource";
    public static final int PROP_ID_changeSource = 6;
    
    /* 变更前快照: BEFORE_SNAPSHOT VARCHAR */
    public static final String PROP_NAME_beforeSnapshot = "beforeSnapshot";
    public static final int PROP_ID_beforeSnapshot = 7;
    
    /* 变更后快照: AFTER_SNAPSHOT VARCHAR */
    public static final String PROP_NAME_afterSnapshot = "afterSnapshot";
    public static final int PROP_ID_afterSnapshot = 8;
    
    /* 操作人: CHANGED_BY VARCHAR */
    public static final String PROP_NAME_changedBy = "changedBy";
    public static final int PROP_ID_changedBy = 9;
    
    /* 变更时间: CHANGE_TIME TIMESTAMP */
    public static final String PROP_NAME_changeTime = "changeTime";
    public static final int PROP_ID_changeTime = 10;
    
    /* 事务ID: TRANSACTION_ID VARCHAR */
    public static final String PROP_NAME_transactionId = "transactionId";
    public static final int PROP_ID_transactionId = 11;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 12;
    
    /* 数据版本: DEL_VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 13;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 14;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 15;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 16;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 17;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 18;
    

    private static int _PROP_ID_BOUND = 19;

    
    /* component:  */
    public static final String PROP_NAME_beforeSnapshotComponent = "beforeSnapshotComponent";
    
    /* component:  */
    public static final String PROP_NAME_afterSnapshotComponent = "afterSnapshotComponent";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_modelChangedEventId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_modelChangedEventId};

    private static final String[] PROP_ID_TO_NAME = new String[19];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_modelChangedEventId] = PROP_NAME_modelChangedEventId;
          PROP_NAME_TO_ID.put(PROP_NAME_modelChangedEventId, PROP_ID_modelChangedEventId);
      
          PROP_ID_TO_NAME[PROP_ID_eventType] = PROP_NAME_eventType;
          PROP_NAME_TO_ID.put(PROP_NAME_eventType, PROP_ID_eventType);
      
          PROP_ID_TO_NAME[PROP_ID_entityType] = PROP_NAME_entityType;
          PROP_NAME_TO_ID.put(PROP_NAME_entityType, PROP_ID_entityType);
      
          PROP_ID_TO_NAME[PROP_ID_entityId] = PROP_NAME_entityId;
          PROP_NAME_TO_ID.put(PROP_NAME_entityId, PROP_ID_entityId);
      
          PROP_ID_TO_NAME[PROP_ID_entityName] = PROP_NAME_entityName;
          PROP_NAME_TO_ID.put(PROP_NAME_entityName, PROP_ID_entityName);
      
          PROP_ID_TO_NAME[PROP_ID_changeSource] = PROP_NAME_changeSource;
          PROP_NAME_TO_ID.put(PROP_NAME_changeSource, PROP_ID_changeSource);
      
          PROP_ID_TO_NAME[PROP_ID_beforeSnapshot] = PROP_NAME_beforeSnapshot;
          PROP_NAME_TO_ID.put(PROP_NAME_beforeSnapshot, PROP_ID_beforeSnapshot);
      
          PROP_ID_TO_NAME[PROP_ID_afterSnapshot] = PROP_NAME_afterSnapshot;
          PROP_NAME_TO_ID.put(PROP_NAME_afterSnapshot, PROP_ID_afterSnapshot);
      
          PROP_ID_TO_NAME[PROP_ID_changedBy] = PROP_NAME_changedBy;
          PROP_NAME_TO_ID.put(PROP_NAME_changedBy, PROP_ID_changedBy);
      
          PROP_ID_TO_NAME[PROP_ID_changeTime] = PROP_NAME_changeTime;
          PROP_NAME_TO_ID.put(PROP_NAME_changeTime, PROP_ID_changeTime);
      
          PROP_ID_TO_NAME[PROP_ID_transactionId] = PROP_NAME_transactionId;
          PROP_NAME_TO_ID.put(PROP_NAME_transactionId, PROP_ID_transactionId);
      
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

    
    /* 事件ID: MODEL_CHANGED_EVENT_ID */
    private java.lang.String _modelChangedEventId;
    
    /* 事件类型: EVENT_TYPE */
    private java.lang.String _eventType;
    
    /* 实体类型: ENTITY_TYPE */
    private java.lang.String _entityType;
    
    /* 实体ID: ENTITY_ID */
    private java.lang.String _entityId;
    
    /* 实体名称: ENTITY_NAME */
    private java.lang.String _entityName;
    
    /* 变更来源: CHANGE_SOURCE */
    private java.lang.String _changeSource;
    
    /* 变更前快照: BEFORE_SNAPSHOT */
    private java.lang.String _beforeSnapshot;
    
    /* 变更后快照: AFTER_SNAPSHOT */
    private java.lang.String _afterSnapshot;
    
    /* 操作人: CHANGED_BY */
    private java.lang.String _changedBy;
    
    /* 变更时间: CHANGE_TIME */
    private java.sql.Timestamp _changeTime;
    
    /* 事务ID: TRANSACTION_ID */
    private java.lang.String _transactionId;
    
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
    

    public _NopMetaModelChangedEvent(){
        // for debug
    }

    protected NopMetaModelChangedEvent newInstance(){
        NopMetaModelChangedEvent entity = new NopMetaModelChangedEvent();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaModelChangedEvent cloneInstance() {
        NopMetaModelChangedEvent entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaModelChangedEvent";
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
    
        return buildSimpleId(PROP_ID_modelChangedEventId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_modelChangedEventId;
          
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
        
            case PROP_ID_modelChangedEventId:
               return getModelChangedEventId();
        
            case PROP_ID_eventType:
               return getEventType();
        
            case PROP_ID_entityType:
               return getEntityType();
        
            case PROP_ID_entityId:
               return getEntityId();
        
            case PROP_ID_entityName:
               return getEntityName();
        
            case PROP_ID_changeSource:
               return getChangeSource();
        
            case PROP_ID_beforeSnapshot:
               return getBeforeSnapshot();
        
            case PROP_ID_afterSnapshot:
               return getAfterSnapshot();
        
            case PROP_ID_changedBy:
               return getChangedBy();
        
            case PROP_ID_changeTime:
               return getChangeTime();
        
            case PROP_ID_transactionId:
               return getTransactionId();
        
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
        
            case PROP_ID_modelChangedEventId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_modelChangedEventId));
               }
               setModelChangedEventId(typedValue);
               break;
            }
        
            case PROP_ID_eventType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_eventType));
               }
               setEventType(typedValue);
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
        
            case PROP_ID_entityName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entityName));
               }
               setEntityName(typedValue);
               break;
            }
        
            case PROP_ID_changeSource:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_changeSource));
               }
               setChangeSource(typedValue);
               break;
            }
        
            case PROP_ID_beforeSnapshot:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_beforeSnapshot));
               }
               setBeforeSnapshot(typedValue);
               break;
            }
        
            case PROP_ID_afterSnapshot:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_afterSnapshot));
               }
               setAfterSnapshot(typedValue);
               break;
            }
        
            case PROP_ID_changedBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_changedBy));
               }
               setChangedBy(typedValue);
               break;
            }
        
            case PROP_ID_changeTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_changeTime));
               }
               setChangeTime(typedValue);
               break;
            }
        
            case PROP_ID_transactionId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_transactionId));
               }
               setTransactionId(typedValue);
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
        
            case PROP_ID_modelChangedEventId:{
               onInitProp(propId);
               this._modelChangedEventId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_eventType:{
               onInitProp(propId);
               this._eventType = (java.lang.String)value;
               
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
        
            case PROP_ID_entityName:{
               onInitProp(propId);
               this._entityName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_changeSource:{
               onInitProp(propId);
               this._changeSource = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_beforeSnapshot:{
               onInitProp(propId);
               this._beforeSnapshot = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_afterSnapshot:{
               onInitProp(propId);
               this._afterSnapshot = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_changedBy:{
               onInitProp(propId);
               this._changedBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_changeTime:{
               onInitProp(propId);
               this._changeTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_transactionId:{
               onInitProp(propId);
               this._transactionId = (java.lang.String)value;
               
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
     * 事件ID: MODEL_CHANGED_EVENT_ID
     */
    public final java.lang.String getModelChangedEventId(){
         onPropGet(PROP_ID_modelChangedEventId);
         return _modelChangedEventId;
    }

    /**
     * 事件ID: MODEL_CHANGED_EVENT_ID
     */
    public final void setModelChangedEventId(java.lang.String value){
        if(onPropSet(PROP_ID_modelChangedEventId,value)){
            this._modelChangedEventId = value;
            internalClearRefs(PROP_ID_modelChangedEventId);
            orm_id();
        }
    }
    
    /**
     * 事件类型: EVENT_TYPE
     */
    public final java.lang.String getEventType(){
         onPropGet(PROP_ID_eventType);
         return _eventType;
    }

    /**
     * 事件类型: EVENT_TYPE
     */
    public final void setEventType(java.lang.String value){
        if(onPropSet(PROP_ID_eventType,value)){
            this._eventType = value;
            internalClearRefs(PROP_ID_eventType);
            
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
     * 实体名称: ENTITY_NAME
     */
    public final java.lang.String getEntityName(){
         onPropGet(PROP_ID_entityName);
         return _entityName;
    }

    /**
     * 实体名称: ENTITY_NAME
     */
    public final void setEntityName(java.lang.String value){
        if(onPropSet(PROP_ID_entityName,value)){
            this._entityName = value;
            internalClearRefs(PROP_ID_entityName);
            
        }
    }
    
    /**
     * 变更来源: CHANGE_SOURCE
     */
    public final java.lang.String getChangeSource(){
         onPropGet(PROP_ID_changeSource);
         return _changeSource;
    }

    /**
     * 变更来源: CHANGE_SOURCE
     */
    public final void setChangeSource(java.lang.String value){
        if(onPropSet(PROP_ID_changeSource,value)){
            this._changeSource = value;
            internalClearRefs(PROP_ID_changeSource);
            
        }
    }
    
    /**
     * 变更前快照: BEFORE_SNAPSHOT
     */
    public final java.lang.String getBeforeSnapshot(){
         onPropGet(PROP_ID_beforeSnapshot);
         return _beforeSnapshot;
    }

    /**
     * 变更前快照: BEFORE_SNAPSHOT
     */
    public final void setBeforeSnapshot(java.lang.String value){
        if(onPropSet(PROP_ID_beforeSnapshot,value)){
            this._beforeSnapshot = value;
            internalClearRefs(PROP_ID_beforeSnapshot);
            
        }
    }
    
    /**
     * 变更后快照: AFTER_SNAPSHOT
     */
    public final java.lang.String getAfterSnapshot(){
         onPropGet(PROP_ID_afterSnapshot);
         return _afterSnapshot;
    }

    /**
     * 变更后快照: AFTER_SNAPSHOT
     */
    public final void setAfterSnapshot(java.lang.String value){
        if(onPropSet(PROP_ID_afterSnapshot,value)){
            this._afterSnapshot = value;
            internalClearRefs(PROP_ID_afterSnapshot);
            
        }
    }
    
    /**
     * 操作人: CHANGED_BY
     */
    public final java.lang.String getChangedBy(){
         onPropGet(PROP_ID_changedBy);
         return _changedBy;
    }

    /**
     * 操作人: CHANGED_BY
     */
    public final void setChangedBy(java.lang.String value){
        if(onPropSet(PROP_ID_changedBy,value)){
            this._changedBy = value;
            internalClearRefs(PROP_ID_changedBy);
            
        }
    }
    
    /**
     * 变更时间: CHANGE_TIME
     */
    public final java.sql.Timestamp getChangeTime(){
         onPropGet(PROP_ID_changeTime);
         return _changeTime;
    }

    /**
     * 变更时间: CHANGE_TIME
     */
    public final void setChangeTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_changeTime,value)){
            this._changeTime = value;
            internalClearRefs(PROP_ID_changeTime);
            
        }
    }
    
    /**
     * 事务ID: TRANSACTION_ID
     */
    public final java.lang.String getTransactionId(){
         onPropGet(PROP_ID_transactionId);
         return _transactionId;
    }

    /**
     * 事务ID: TRANSACTION_ID
     */
    public final void setTransactionId(java.lang.String value){
        if(onPropSet(PROP_ID_transactionId,value)){
            this._transactionId = value;
            internalClearRefs(PROP_ID_transactionId);
            
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
    
   private io.nop.orm.component.JsonOrmComponent _beforeSnapshotComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_beforeSnapshotComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_beforeSnapshotComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_beforeSnapshot);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getBeforeSnapshotComponent(){
      if(_beforeSnapshotComponent == null){
          _beforeSnapshotComponent = new io.nop.orm.component.JsonOrmComponent();
          _beforeSnapshotComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_beforeSnapshotComponent);
      }
      return _beforeSnapshotComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _afterSnapshotComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_afterSnapshotComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_afterSnapshotComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_afterSnapshot);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getAfterSnapshotComponent(){
      if(_afterSnapshotComponent == null){
          _afterSnapshotComponent = new io.nop.orm.component.JsonOrmComponent();
          _afterSnapshotComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_afterSnapshotComponent);
      }
      return _afterSnapshotComponent;
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
