package io.nop.sys.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.sys.dao.entity.NopSysLock;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  资源锁: nop_sys_lock
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopSysLock extends DynamicOrmEntity{
    
    /* 锁名称: LOCK_NAME VARCHAR */
    public static final String PROP_NAME_lockName = "lockName";
    public static final int PROP_ID_lockName = 1;
    
    /* 分组: LOCK_GROUP VARCHAR */
    public static final String PROP_NAME_lockGroup = "lockGroup";
    public static final int PROP_ID_lockGroup = 2;
    
    /* 锁定时间: LOCK_TIME TIMESTAMP */
    public static final String PROP_NAME_lockTime = "lockTime";
    public static final int PROP_ID_lockTime = 3;
    
    /* 过期时间: EXPIRE_AT TIMESTAMP */
    public static final String PROP_NAME_expireAt = "expireAt";
    public static final int PROP_ID_expireAt = 4;
    
    /* 锁定原因: LOCK_REASON VARCHAR */
    public static final String PROP_NAME_lockReason = "lockReason";
    public static final int PROP_ID_lockReason = 5;
    
    /* 锁的持有者: HOLDER_ID VARCHAR */
    public static final String PROP_NAME_holderId = "holderId";
    public static final int PROP_ID_holderId = 6;
    
    /* 持有者地址: HOLDER_ADDER VARCHAR */
    public static final String PROP_NAME_holderAdder = "holderAdder";
    public static final int PROP_ID_holderAdder = 7;
    
    /* 应用ID: APP_ID VARCHAR */
    public static final String PROP_NAME_appId = "appId";
    public static final int PROP_ID_appId = 8;
    
    /* 数据版本: VERSION INTEGER */
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
    

    private static int _PROP_ID_BOUND = 14;

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_lockName,PROP_NAME_lockGroup);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_lockName,PROP_ID_lockGroup};

    private static final String[] PROP_ID_TO_NAME = new String[14];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_lockName] = PROP_NAME_lockName;
          PROP_NAME_TO_ID.put(PROP_NAME_lockName, PROP_ID_lockName);
      
          PROP_ID_TO_NAME[PROP_ID_lockGroup] = PROP_NAME_lockGroup;
          PROP_NAME_TO_ID.put(PROP_NAME_lockGroup, PROP_ID_lockGroup);
      
          PROP_ID_TO_NAME[PROP_ID_lockTime] = PROP_NAME_lockTime;
          PROP_NAME_TO_ID.put(PROP_NAME_lockTime, PROP_ID_lockTime);
      
          PROP_ID_TO_NAME[PROP_ID_expireAt] = PROP_NAME_expireAt;
          PROP_NAME_TO_ID.put(PROP_NAME_expireAt, PROP_ID_expireAt);
      
          PROP_ID_TO_NAME[PROP_ID_lockReason] = PROP_NAME_lockReason;
          PROP_NAME_TO_ID.put(PROP_NAME_lockReason, PROP_ID_lockReason);
      
          PROP_ID_TO_NAME[PROP_ID_holderId] = PROP_NAME_holderId;
          PROP_NAME_TO_ID.put(PROP_NAME_holderId, PROP_ID_holderId);
      
          PROP_ID_TO_NAME[PROP_ID_holderAdder] = PROP_NAME_holderAdder;
          PROP_NAME_TO_ID.put(PROP_NAME_holderAdder, PROP_ID_holderAdder);
      
          PROP_ID_TO_NAME[PROP_ID_appId] = PROP_NAME_appId;
          PROP_NAME_TO_ID.put(PROP_NAME_appId, PROP_ID_appId);
      
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
      
    }

    
    /* 锁名称: LOCK_NAME */
    private java.lang.String _lockName;
    
    /* 分组: LOCK_GROUP */
    private java.lang.String _lockGroup;
    
    /* 锁定时间: LOCK_TIME */
    private java.sql.Timestamp _lockTime;
    
    /* 过期时间: EXPIRE_AT */
    private java.sql.Timestamp _expireAt;
    
    /* 锁定原因: LOCK_REASON */
    private java.lang.String _lockReason;
    
    /* 锁的持有者: HOLDER_ID */
    private java.lang.String _holderId;
    
    /* 持有者地址: HOLDER_ADDER */
    private java.lang.String _holderAdder;
    
    /* 应用ID: APP_ID */
    private java.lang.String _appId;
    
    /* 数据版本: VERSION */
    private java.lang.Integer _version;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 修改人: UPDATED_BY */
    private java.lang.String _updatedBy;
    
    /* 修改时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    

    public _NopSysLock(){
        // for debug
    }

    protected NopSysLock newInstance(){
        NopSysLock entity = new NopSysLock();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopSysLock cloneInstance() {
        NopSysLock entity = newInstance();
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
      return "io.nop.sys.dao.entity.NopSysLock";
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
    
        return buildCompositeId(PK_PROP_NAMES,PK_PROP_IDS);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_lockName || propId == PROP_ID_lockGroup;
          
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
        
            case PROP_ID_lockName:
               return getLockName();
        
            case PROP_ID_lockGroup:
               return getLockGroup();
        
            case PROP_ID_lockTime:
               return getLockTime();
        
            case PROP_ID_expireAt:
               return getExpireAt();
        
            case PROP_ID_lockReason:
               return getLockReason();
        
            case PROP_ID_holderId:
               return getHolderId();
        
            case PROP_ID_holderAdder:
               return getHolderAdder();
        
            case PROP_ID_appId:
               return getAppId();
        
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
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_lockName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_lockName));
               }
               setLockName(typedValue);
               break;
            }
        
            case PROP_ID_lockGroup:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_lockGroup));
               }
               setLockGroup(typedValue);
               break;
            }
        
            case PROP_ID_lockTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_lockTime));
               }
               setLockTime(typedValue);
               break;
            }
        
            case PROP_ID_expireAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_expireAt));
               }
               setExpireAt(typedValue);
               break;
            }
        
            case PROP_ID_lockReason:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_lockReason));
               }
               setLockReason(typedValue);
               break;
            }
        
            case PROP_ID_holderId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_holderId));
               }
               setHolderId(typedValue);
               break;
            }
        
            case PROP_ID_holderAdder:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_holderAdder));
               }
               setHolderAdder(typedValue);
               break;
            }
        
            case PROP_ID_appId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_appId));
               }
               setAppId(typedValue);
               break;
            }
        
            case PROP_ID_version:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
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
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_lockName:{
               onInitProp(propId);
               this._lockName = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_lockGroup:{
               onInitProp(propId);
               this._lockGroup = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_lockTime:{
               onInitProp(propId);
               this._lockTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_expireAt:{
               onInitProp(propId);
               this._expireAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_lockReason:{
               onInitProp(propId);
               this._lockReason = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_holderId:{
               onInitProp(propId);
               this._holderId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_holderAdder:{
               onInitProp(propId);
               this._holderAdder = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_appId:{
               onInitProp(propId);
               this._appId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_version:{
               onInitProp(propId);
               this._version = (java.lang.Integer)value;
               
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
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 锁名称: LOCK_NAME
     */
    public java.lang.String getLockName(){
         onPropGet(PROP_ID_lockName);
         return _lockName;
    }

    /**
     * 锁名称: LOCK_NAME
     */
    public void setLockName(java.lang.String value){
        if(onPropSet(PROP_ID_lockName,value)){
            this._lockName = value;
            internalClearRefs(PROP_ID_lockName);
            orm_id();
        }
    }
    
    /**
     * 分组: LOCK_GROUP
     */
    public java.lang.String getLockGroup(){
         onPropGet(PROP_ID_lockGroup);
         return _lockGroup;
    }

    /**
     * 分组: LOCK_GROUP
     */
    public void setLockGroup(java.lang.String value){
        if(onPropSet(PROP_ID_lockGroup,value)){
            this._lockGroup = value;
            internalClearRefs(PROP_ID_lockGroup);
            orm_id();
        }
    }
    
    /**
     * 锁定时间: LOCK_TIME
     */
    public java.sql.Timestamp getLockTime(){
         onPropGet(PROP_ID_lockTime);
         return _lockTime;
    }

    /**
     * 锁定时间: LOCK_TIME
     */
    public void setLockTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_lockTime,value)){
            this._lockTime = value;
            internalClearRefs(PROP_ID_lockTime);
            
        }
    }
    
    /**
     * 过期时间: EXPIRE_AT
     */
    public java.sql.Timestamp getExpireAt(){
         onPropGet(PROP_ID_expireAt);
         return _expireAt;
    }

    /**
     * 过期时间: EXPIRE_AT
     */
    public void setExpireAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_expireAt,value)){
            this._expireAt = value;
            internalClearRefs(PROP_ID_expireAt);
            
        }
    }
    
    /**
     * 锁定原因: LOCK_REASON
     */
    public java.lang.String getLockReason(){
         onPropGet(PROP_ID_lockReason);
         return _lockReason;
    }

    /**
     * 锁定原因: LOCK_REASON
     */
    public void setLockReason(java.lang.String value){
        if(onPropSet(PROP_ID_lockReason,value)){
            this._lockReason = value;
            internalClearRefs(PROP_ID_lockReason);
            
        }
    }
    
    /**
     * 锁的持有者: HOLDER_ID
     */
    public java.lang.String getHolderId(){
         onPropGet(PROP_ID_holderId);
         return _holderId;
    }

    /**
     * 锁的持有者: HOLDER_ID
     */
    public void setHolderId(java.lang.String value){
        if(onPropSet(PROP_ID_holderId,value)){
            this._holderId = value;
            internalClearRefs(PROP_ID_holderId);
            
        }
    }
    
    /**
     * 持有者地址: HOLDER_ADDER
     */
    public java.lang.String getHolderAdder(){
         onPropGet(PROP_ID_holderAdder);
         return _holderAdder;
    }

    /**
     * 持有者地址: HOLDER_ADDER
     */
    public void setHolderAdder(java.lang.String value){
        if(onPropSet(PROP_ID_holderAdder,value)){
            this._holderAdder = value;
            internalClearRefs(PROP_ID_holderAdder);
            
        }
    }
    
    /**
     * 应用ID: APP_ID
     */
    public java.lang.String getAppId(){
         onPropGet(PROP_ID_appId);
         return _appId;
    }

    /**
     * 应用ID: APP_ID
     */
    public void setAppId(java.lang.String value){
        if(onPropSet(PROP_ID_appId,value)){
            this._appId = value;
            internalClearRefs(PROP_ID_appId);
            
        }
    }
    
    /**
     * 数据版本: VERSION
     */
    public java.lang.Integer getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
     */
    public void setVersion(java.lang.Integer value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
        }
    }
    
    /**
     * 创建人: CREATED_BY
     */
    public java.lang.String getCreatedBy(){
         onPropGet(PROP_ID_createdBy);
         return _createdBy;
    }

    /**
     * 创建人: CREATED_BY
     */
    public void setCreatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_createdBy,value)){
            this._createdBy = value;
            internalClearRefs(PROP_ID_createdBy);
            
        }
    }
    
    /**
     * 创建时间: CREATE_TIME
     */
    public java.sql.Timestamp getCreateTime(){
         onPropGet(PROP_ID_createTime);
         return _createTime;
    }

    /**
     * 创建时间: CREATE_TIME
     */
    public void setCreateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_createTime,value)){
            this._createTime = value;
            internalClearRefs(PROP_ID_createTime);
            
        }
    }
    
    /**
     * 修改人: UPDATED_BY
     */
    public java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 修改人: UPDATED_BY
     */
    public void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
        }
    }
    
    /**
     * 修改时间: UPDATE_TIME
     */
    public java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 修改时间: UPDATE_TIME
     */
    public void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
        }
    }
    
}
// resume CPD analysis - CPD-ON
