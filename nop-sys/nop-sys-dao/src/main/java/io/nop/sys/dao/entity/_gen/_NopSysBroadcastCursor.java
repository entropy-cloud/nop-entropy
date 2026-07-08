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

import io.nop.sys.dao.entity.NopSysBroadcastCursor;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  广播订阅游标: nop_sys_broadcast_cursor
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopSysBroadcastCursor extends DynamicOrmEntity{
    
    /* 主键: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 订阅者ID: SUBSCRIBER_ID VARCHAR */
    public static final String PROP_NAME_subscriberId = "subscriberId";
    public static final int PROP_ID_subscriberId = 2;
    
    /* 事件主题: EVENT_TOPIC VARCHAR */
    public static final String PROP_NAME_eventTopic = "eventTopic";
    public static final int PROP_ID_eventTopic = 3;
    
    /* 最后消费事件ID: LAST_CONSUMED_EVENT_ID BIGINT */
    public static final String PROP_NAME_lastConsumedEventId = "lastConsumedEventId";
    public static final int PROP_ID_lastConsumedEventId = 4;
    
    /* 租约持有者: LEASE_OWNER VARCHAR */
    public static final String PROP_NAME_leaseOwner = "leaseOwner";
    public static final int PROP_ID_leaseOwner = 5;
    
    /* 租约过期时间: LEASE_EXPIRE_TIME TIMESTAMP */
    public static final String PROP_NAME_leaseExpireTime = "leaseExpireTime";
    public static final int PROP_ID_leaseExpireTime = 6;
    
    /* 最后消费时间: LAST_CONSUME_TIME TIMESTAMP */
    public static final String PROP_NAME_lastConsumeTime = "lastConsumeTime";
    public static final int PROP_ID_lastConsumeTime = 7;
    
    /* 最后错误: LAST_ERROR VARCHAR */
    public static final String PROP_NAME_lastError = "lastError";
    public static final int PROP_ID_lastError = 8;
    
    /* 数据版本: VERSION BIGINT */
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

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[14];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_subscriberId] = PROP_NAME_subscriberId;
          PROP_NAME_TO_ID.put(PROP_NAME_subscriberId, PROP_ID_subscriberId);
      
          PROP_ID_TO_NAME[PROP_ID_eventTopic] = PROP_NAME_eventTopic;
          PROP_NAME_TO_ID.put(PROP_NAME_eventTopic, PROP_ID_eventTopic);
      
          PROP_ID_TO_NAME[PROP_ID_lastConsumedEventId] = PROP_NAME_lastConsumedEventId;
          PROP_NAME_TO_ID.put(PROP_NAME_lastConsumedEventId, PROP_ID_lastConsumedEventId);
      
          PROP_ID_TO_NAME[PROP_ID_leaseOwner] = PROP_NAME_leaseOwner;
          PROP_NAME_TO_ID.put(PROP_NAME_leaseOwner, PROP_ID_leaseOwner);
      
          PROP_ID_TO_NAME[PROP_ID_leaseExpireTime] = PROP_NAME_leaseExpireTime;
          PROP_NAME_TO_ID.put(PROP_NAME_leaseExpireTime, PROP_ID_leaseExpireTime);
      
          PROP_ID_TO_NAME[PROP_ID_lastConsumeTime] = PROP_NAME_lastConsumeTime;
          PROP_NAME_TO_ID.put(PROP_NAME_lastConsumeTime, PROP_ID_lastConsumeTime);
      
          PROP_ID_TO_NAME[PROP_ID_lastError] = PROP_NAME_lastError;
          PROP_NAME_TO_ID.put(PROP_NAME_lastError, PROP_ID_lastError);
      
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

    
    /* 主键: SID */
    private java.lang.String _sid;
    
    /* 订阅者ID: SUBSCRIBER_ID */
    private java.lang.String _subscriberId;
    
    /* 事件主题: EVENT_TOPIC */
    private java.lang.String _eventTopic;
    
    /* 最后消费事件ID: LAST_CONSUMED_EVENT_ID */
    private java.lang.Long _lastConsumedEventId;
    
    /* 租约持有者: LEASE_OWNER */
    private java.lang.String _leaseOwner;
    
    /* 租约过期时间: LEASE_EXPIRE_TIME */
    private java.sql.Timestamp _leaseExpireTime;
    
    /* 最后消费时间: LAST_CONSUME_TIME */
    private java.sql.Timestamp _lastConsumeTime;
    
    /* 最后错误: LAST_ERROR */
    private java.lang.String _lastError;
    
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
    

    public _NopSysBroadcastCursor(){
        // for debug
    }

    protected NopSysBroadcastCursor newInstance(){
        NopSysBroadcastCursor entity = new NopSysBroadcastCursor();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopSysBroadcastCursor cloneInstance() {
        NopSysBroadcastCursor entity = newInstance();
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
      return "io.nop.sys.dao.entity.NopSysBroadcastCursor";
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
    
        return buildSimpleId(PROP_ID_sid);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_sid;
          
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
        
            case PROP_ID_sid:
               return getSid();
        
            case PROP_ID_subscriberId:
               return getSubscriberId();
        
            case PROP_ID_eventTopic:
               return getEventTopic();
        
            case PROP_ID_lastConsumedEventId:
               return getLastConsumedEventId();
        
            case PROP_ID_leaseOwner:
               return getLeaseOwner();
        
            case PROP_ID_leaseExpireTime:
               return getLeaseExpireTime();
        
            case PROP_ID_lastConsumeTime:
               return getLastConsumeTime();
        
            case PROP_ID_lastError:
               return getLastError();
        
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
        
            case PROP_ID_sid:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sid));
               }
               setSid(typedValue);
               break;
            }
        
            case PROP_ID_subscriberId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_subscriberId));
               }
               setSubscriberId(typedValue);
               break;
            }
        
            case PROP_ID_eventTopic:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_eventTopic));
               }
               setEventTopic(typedValue);
               break;
            }
        
            case PROP_ID_lastConsumedEventId:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_lastConsumedEventId));
               }
               setLastConsumedEventId(typedValue);
               break;
            }
        
            case PROP_ID_leaseOwner:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_leaseOwner));
               }
               setLeaseOwner(typedValue);
               break;
            }
        
            case PROP_ID_leaseExpireTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_leaseExpireTime));
               }
               setLeaseExpireTime(typedValue);
               break;
            }
        
            case PROP_ID_lastConsumeTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_lastConsumeTime));
               }
               setLastConsumeTime(typedValue);
               break;
            }
        
            case PROP_ID_lastError:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_lastError));
               }
               setLastError(typedValue);
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
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_sid:{
               onInitProp(propId);
               this._sid = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_subscriberId:{
               onInitProp(propId);
               this._subscriberId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_eventTopic:{
               onInitProp(propId);
               this._eventTopic = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_lastConsumedEventId:{
               onInitProp(propId);
               this._lastConsumedEventId = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_leaseOwner:{
               onInitProp(propId);
               this._leaseOwner = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_leaseExpireTime:{
               onInitProp(propId);
               this._leaseExpireTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_lastConsumeTime:{
               onInitProp(propId);
               this._lastConsumeTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_lastError:{
               onInitProp(propId);
               this._lastError = (java.lang.String)value;
               
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
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 主键: SID
     */
    public final java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * 主键: SID
     */
    public final void setSid(java.lang.String value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }
    
    /**
     * 订阅者ID: SUBSCRIBER_ID
     */
    public final java.lang.String getSubscriberId(){
         onPropGet(PROP_ID_subscriberId);
         return _subscriberId;
    }

    /**
     * 订阅者ID: SUBSCRIBER_ID
     */
    public final void setSubscriberId(java.lang.String value){
        if(onPropSet(PROP_ID_subscriberId,value)){
            this._subscriberId = value;
            internalClearRefs(PROP_ID_subscriberId);
            
        }
    }
    
    /**
     * 事件主题: EVENT_TOPIC
     */
    public final java.lang.String getEventTopic(){
         onPropGet(PROP_ID_eventTopic);
         return _eventTopic;
    }

    /**
     * 事件主题: EVENT_TOPIC
     */
    public final void setEventTopic(java.lang.String value){
        if(onPropSet(PROP_ID_eventTopic,value)){
            this._eventTopic = value;
            internalClearRefs(PROP_ID_eventTopic);
            
        }
    }
    
    /**
     * 最后消费事件ID: LAST_CONSUMED_EVENT_ID
     */
    public final java.lang.Long getLastConsumedEventId(){
         onPropGet(PROP_ID_lastConsumedEventId);
         return _lastConsumedEventId;
    }

    /**
     * 最后消费事件ID: LAST_CONSUMED_EVENT_ID
     */
    public final void setLastConsumedEventId(java.lang.Long value){
        if(onPropSet(PROP_ID_lastConsumedEventId,value)){
            this._lastConsumedEventId = value;
            internalClearRefs(PROP_ID_lastConsumedEventId);
            
        }
    }
    
    /**
     * 租约持有者: LEASE_OWNER
     */
    public final java.lang.String getLeaseOwner(){
         onPropGet(PROP_ID_leaseOwner);
         return _leaseOwner;
    }

    /**
     * 租约持有者: LEASE_OWNER
     */
    public final void setLeaseOwner(java.lang.String value){
        if(onPropSet(PROP_ID_leaseOwner,value)){
            this._leaseOwner = value;
            internalClearRefs(PROP_ID_leaseOwner);
            
        }
    }
    
    /**
     * 租约过期时间: LEASE_EXPIRE_TIME
     */
    public final java.sql.Timestamp getLeaseExpireTime(){
         onPropGet(PROP_ID_leaseExpireTime);
         return _leaseExpireTime;
    }

    /**
     * 租约过期时间: LEASE_EXPIRE_TIME
     */
    public final void setLeaseExpireTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_leaseExpireTime,value)){
            this._leaseExpireTime = value;
            internalClearRefs(PROP_ID_leaseExpireTime);
            
        }
    }
    
    /**
     * 最后消费时间: LAST_CONSUME_TIME
     */
    public final java.sql.Timestamp getLastConsumeTime(){
         onPropGet(PROP_ID_lastConsumeTime);
         return _lastConsumeTime;
    }

    /**
     * 最后消费时间: LAST_CONSUME_TIME
     */
    public final void setLastConsumeTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_lastConsumeTime,value)){
            this._lastConsumeTime = value;
            internalClearRefs(PROP_ID_lastConsumeTime);
            
        }
    }
    
    /**
     * 最后错误: LAST_ERROR
     */
    public final java.lang.String getLastError(){
         onPropGet(PROP_ID_lastError);
         return _lastError;
    }

    /**
     * 最后错误: LAST_ERROR
     */
    public final void setLastError(java.lang.String value){
        if(onPropSet(PROP_ID_lastError,value)){
            this._lastError = value;
            internalClearRefs(PROP_ID_lastError);
            
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
    
}
// resume CPD analysis - CPD-ON
