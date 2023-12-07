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
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public class _NopSysLock extends DynamicOrmEntity{
    
    /* 分组: LOCK_GROUP VARCHAR */
    public static final String PROP_NAME_lockGroup = "lockGroup";
    public static final int PROP_ID_lockGroup = 1;
    
    /* 锁名称: LOCK_NAME VARCHAR */
    public static final String PROP_NAME_lockName = "lockName";
    public static final int PROP_ID_lockName = 2;
    
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
    

    private static int _PROP_ID_BOUND = 9;

    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_lockGroup,PROP_NAME_lockName);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_lockGroup,PROP_ID_lockName};

    private static final String[] PROP_ID_TO_NAME = new String[9];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_lockGroup] = PROP_NAME_lockGroup;
          PROP_NAME_TO_ID.put(PROP_NAME_lockGroup, PROP_ID_lockGroup);
      
          PROP_ID_TO_NAME[PROP_ID_lockName] = PROP_NAME_lockName;
          PROP_NAME_TO_ID.put(PROP_NAME_lockName, PROP_ID_lockName);
      
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
      
    }

    
    /* 分组: LOCK_GROUP */
    private java.lang.String _lockGroup;
    
    /* 锁名称: LOCK_NAME */
    private java.lang.String _lockName;
    
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
    

    public _NopSysLock(){
    }

    protected NopSysLock newInstance(){
       return new NopSysLock();
    }

    @Override
    public NopSysLock cloneInstance() {
        NopSysLock entity = newInstance();
        orm_forEachInitedProp((value, propId) -> {
            entity.onInitProp(propId);
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
        
            return propId == PROP_ID_lockGroup || propId == PROP_ID_lockName;
          
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
        
            case PROP_ID_lockGroup:
               return getLockGroup();
        
            case PROP_ID_lockName:
               return getLockName();
        
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
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_lockGroup:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_lockGroup));
               }
               setLockGroup(typedValue);
               break;
            }
        
            case PROP_ID_lockName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_lockName));
               }
               setLockName(typedValue);
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
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_lockGroup:{
               onInitProp(propId);
               this._lockGroup = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_lockName:{
               onInitProp(propId);
               this._lockName = (java.lang.String)value;
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
        
           default:
              super.orm_internalSet(propId,value);
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
    
}
// resume CPD analysis - CPD-ON
