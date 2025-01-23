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

import io.nop.sys.dao.entity.NopSysEvent;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  事件队列: nop_sys_event
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopSysEvent extends DynamicOrmEntity{
    
    /* 事件ID: EVENT_ID BIGINT */
    public static final String PROP_NAME_eventId = "eventId";
    public static final int PROP_ID_eventId = 1;
    
    /* 事件主题: EVENT_TOPIC VARCHAR */
    public static final String PROP_NAME_eventTopic = "eventTopic";
    public static final int PROP_ID_eventTopic = 2;
    
    /* 事件名称: EVENT_NAME VARCHAR */
    public static final String PROP_NAME_eventName = "eventName";
    public static final int PROP_ID_eventName = 3;
    
    /* 事件元数据: EVENT_HEADERS JSON */
    public static final String PROP_NAME_eventHeaders = "eventHeaders";
    public static final int PROP_ID_eventHeaders = 4;
    
    /* 数据: EVENT_DATA JSON */
    public static final String PROP_NAME_eventData = "eventData";
    public static final int PROP_ID_eventData = 5;
    
    /* 字段选择: SELECTION VARCHAR */
    public static final String PROP_NAME_selection = "selection";
    public static final int PROP_ID_selection = 6;
    
    /* 事件时间: EVENT_TIME TIMESTAMP */
    public static final String PROP_NAME_eventTime = "eventTime";
    public static final int PROP_ID_eventTime = 7;
    
    /* 事件状态: EVENT_STATUS INTEGER */
    public static final String PROP_NAME_eventStatus = "eventStatus";
    public static final int PROP_ID_eventStatus = 8;
    
    /* 处理时间: PROCESS_TIME TIMESTAMP */
    public static final String PROP_NAME_processTime = "processTime";
    public static final int PROP_ID_processTime = 9;
    
    /* 调度时间: SCHEDULE_TIME TIMESTAMP */
    public static final String PROP_NAME_scheduleTime = "scheduleTime";
    public static final int PROP_ID_scheduleTime = 10;
    
    /* 是否广播: IS_BROADCAST BOOLEAN */
    public static final String PROP_NAME_isBroadcast = "isBroadcast";
    public static final int PROP_ID_isBroadcast = 11;
    
    /* 业务对象名: BIZ_OBJ_NAME VARCHAR */
    public static final String PROP_NAME_bizObjName = "bizObjName";
    public static final int PROP_ID_bizObjName = 12;
    
    /* 业务标识: BIZ_KEY VARCHAR */
    public static final String PROP_NAME_bizKey = "bizKey";
    public static final int PROP_ID_bizKey = 13;
    
    /* 业务日期: BIZ_DATE DATE */
    public static final String PROP_NAME_bizDate = "bizDate";
    public static final int PROP_ID_bizDate = 14;
    
    /* 数据分区: PARTITION_INDEX INTEGER */
    public static final String PROP_NAME_partitionIndex = "partitionIndex";
    public static final int PROP_ID_partitionIndex = 15;
    
    /* 重试次数: RETRY_TIMES INTEGER */
    public static final String PROP_NAME_retryTimes = "retryTimes";
    public static final int PROP_ID_retryTimes = 16;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 17;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 18;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 19;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 20;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 21;
    

    private static int _PROP_ID_BOUND = 22;

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_eventId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_eventId};

    private static final String[] PROP_ID_TO_NAME = new String[22];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_eventId] = PROP_NAME_eventId;
          PROP_NAME_TO_ID.put(PROP_NAME_eventId, PROP_ID_eventId);
      
          PROP_ID_TO_NAME[PROP_ID_eventTopic] = PROP_NAME_eventTopic;
          PROP_NAME_TO_ID.put(PROP_NAME_eventTopic, PROP_ID_eventTopic);
      
          PROP_ID_TO_NAME[PROP_ID_eventName] = PROP_NAME_eventName;
          PROP_NAME_TO_ID.put(PROP_NAME_eventName, PROP_ID_eventName);
      
          PROP_ID_TO_NAME[PROP_ID_eventHeaders] = PROP_NAME_eventHeaders;
          PROP_NAME_TO_ID.put(PROP_NAME_eventHeaders, PROP_ID_eventHeaders);
      
          PROP_ID_TO_NAME[PROP_ID_eventData] = PROP_NAME_eventData;
          PROP_NAME_TO_ID.put(PROP_NAME_eventData, PROP_ID_eventData);
      
          PROP_ID_TO_NAME[PROP_ID_selection] = PROP_NAME_selection;
          PROP_NAME_TO_ID.put(PROP_NAME_selection, PROP_ID_selection);
      
          PROP_ID_TO_NAME[PROP_ID_eventTime] = PROP_NAME_eventTime;
          PROP_NAME_TO_ID.put(PROP_NAME_eventTime, PROP_ID_eventTime);
      
          PROP_ID_TO_NAME[PROP_ID_eventStatus] = PROP_NAME_eventStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_eventStatus, PROP_ID_eventStatus);
      
          PROP_ID_TO_NAME[PROP_ID_processTime] = PROP_NAME_processTime;
          PROP_NAME_TO_ID.put(PROP_NAME_processTime, PROP_ID_processTime);
      
          PROP_ID_TO_NAME[PROP_ID_scheduleTime] = PROP_NAME_scheduleTime;
          PROP_NAME_TO_ID.put(PROP_NAME_scheduleTime, PROP_ID_scheduleTime);
      
          PROP_ID_TO_NAME[PROP_ID_isBroadcast] = PROP_NAME_isBroadcast;
          PROP_NAME_TO_ID.put(PROP_NAME_isBroadcast, PROP_ID_isBroadcast);
      
          PROP_ID_TO_NAME[PROP_ID_bizObjName] = PROP_NAME_bizObjName;
          PROP_NAME_TO_ID.put(PROP_NAME_bizObjName, PROP_ID_bizObjName);
      
          PROP_ID_TO_NAME[PROP_ID_bizKey] = PROP_NAME_bizKey;
          PROP_NAME_TO_ID.put(PROP_NAME_bizKey, PROP_ID_bizKey);
      
          PROP_ID_TO_NAME[PROP_ID_bizDate] = PROP_NAME_bizDate;
          PROP_NAME_TO_ID.put(PROP_NAME_bizDate, PROP_ID_bizDate);
      
          PROP_ID_TO_NAME[PROP_ID_partitionIndex] = PROP_NAME_partitionIndex;
          PROP_NAME_TO_ID.put(PROP_NAME_partitionIndex, PROP_ID_partitionIndex);
      
          PROP_ID_TO_NAME[PROP_ID_retryTimes] = PROP_NAME_retryTimes;
          PROP_NAME_TO_ID.put(PROP_NAME_retryTimes, PROP_ID_retryTimes);
      
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

    
    /* 事件ID: EVENT_ID */
    private java.lang.Long _eventId;
    
    /* 事件主题: EVENT_TOPIC */
    private java.lang.String _eventTopic;
    
    /* 事件名称: EVENT_NAME */
    private java.lang.String _eventName;
    
    /* 事件元数据: EVENT_HEADERS */
    private java.lang.String _eventHeaders;
    
    /* 数据: EVENT_DATA */
    private java.lang.String _eventData;
    
    /* 字段选择: SELECTION */
    private java.lang.String _selection;
    
    /* 事件时间: EVENT_TIME */
    private java.sql.Timestamp _eventTime;
    
    /* 事件状态: EVENT_STATUS */
    private java.lang.Integer _eventStatus;
    
    /* 处理时间: PROCESS_TIME */
    private java.sql.Timestamp _processTime;
    
    /* 调度时间: SCHEDULE_TIME */
    private java.sql.Timestamp _scheduleTime;
    
    /* 是否广播: IS_BROADCAST */
    private java.lang.Boolean _isBroadcast;
    
    /* 业务对象名: BIZ_OBJ_NAME */
    private java.lang.String _bizObjName;
    
    /* 业务标识: BIZ_KEY */
    private java.lang.String _bizKey;
    
    /* 业务日期: BIZ_DATE */
    private java.time.LocalDate _bizDate;
    
    /* 数据分区: PARTITION_INDEX */
    private java.lang.Integer _partitionIndex;
    
    /* 重试次数: RETRY_TIMES */
    private java.lang.Integer _retryTimes;
    
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
    

    public _NopSysEvent(){
        // for debug
    }

    protected NopSysEvent newInstance(){
        NopSysEvent entity = new NopSysEvent();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopSysEvent cloneInstance() {
        NopSysEvent entity = newInstance();
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
      return "io.nop.sys.dao.entity.NopSysEvent";
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
    
        return buildSimpleId(PROP_ID_eventId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_eventId;
          
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
        
            case PROP_ID_eventId:
               return getEventId();
        
            case PROP_ID_eventTopic:
               return getEventTopic();
        
            case PROP_ID_eventName:
               return getEventName();
        
            case PROP_ID_eventHeaders:
               return getEventHeaders();
        
            case PROP_ID_eventData:
               return getEventData();
        
            case PROP_ID_selection:
               return getSelection();
        
            case PROP_ID_eventTime:
               return getEventTime();
        
            case PROP_ID_eventStatus:
               return getEventStatus();
        
            case PROP_ID_processTime:
               return getProcessTime();
        
            case PROP_ID_scheduleTime:
               return getScheduleTime();
        
            case PROP_ID_isBroadcast:
               return getIsBroadcast();
        
            case PROP_ID_bizObjName:
               return getBizObjName();
        
            case PROP_ID_bizKey:
               return getBizKey();
        
            case PROP_ID_bizDate:
               return getBizDate();
        
            case PROP_ID_partitionIndex:
               return getPartitionIndex();
        
            case PROP_ID_retryTimes:
               return getRetryTimes();
        
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
        
            case PROP_ID_eventId:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_eventId));
               }
               setEventId(typedValue);
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
        
            case PROP_ID_eventName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_eventName));
               }
               setEventName(typedValue);
               break;
            }
        
            case PROP_ID_eventHeaders:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_eventHeaders));
               }
               setEventHeaders(typedValue);
               break;
            }
        
            case PROP_ID_eventData:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_eventData));
               }
               setEventData(typedValue);
               break;
            }
        
            case PROP_ID_selection:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_selection));
               }
               setSelection(typedValue);
               break;
            }
        
            case PROP_ID_eventTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_eventTime));
               }
               setEventTime(typedValue);
               break;
            }
        
            case PROP_ID_eventStatus:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_eventStatus));
               }
               setEventStatus(typedValue);
               break;
            }
        
            case PROP_ID_processTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_processTime));
               }
               setProcessTime(typedValue);
               break;
            }
        
            case PROP_ID_scheduleTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_scheduleTime));
               }
               setScheduleTime(typedValue);
               break;
            }
        
            case PROP_ID_isBroadcast:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_isBroadcast));
               }
               setIsBroadcast(typedValue);
               break;
            }
        
            case PROP_ID_bizObjName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bizObjName));
               }
               setBizObjName(typedValue);
               break;
            }
        
            case PROP_ID_bizKey:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bizKey));
               }
               setBizKey(typedValue);
               break;
            }
        
            case PROP_ID_bizDate:{
               java.time.LocalDate typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDate(value,
                       err-> newTypeConversionError(PROP_NAME_bizDate));
               }
               setBizDate(typedValue);
               break;
            }
        
            case PROP_ID_partitionIndex:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_partitionIndex));
               }
               setPartitionIndex(typedValue);
               break;
            }
        
            case PROP_ID_retryTimes:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_retryTimes));
               }
               setRetryTimes(typedValue);
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
        
            case PROP_ID_eventId:{
               onInitProp(propId);
               this._eventId = (java.lang.Long)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_eventTopic:{
               onInitProp(propId);
               this._eventTopic = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_eventName:{
               onInitProp(propId);
               this._eventName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_eventHeaders:{
               onInitProp(propId);
               this._eventHeaders = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_eventData:{
               onInitProp(propId);
               this._eventData = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_selection:{
               onInitProp(propId);
               this._selection = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_eventTime:{
               onInitProp(propId);
               this._eventTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_eventStatus:{
               onInitProp(propId);
               this._eventStatus = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_processTime:{
               onInitProp(propId);
               this._processTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_scheduleTime:{
               onInitProp(propId);
               this._scheduleTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_isBroadcast:{
               onInitProp(propId);
               this._isBroadcast = (java.lang.Boolean)value;
               
               break;
            }
        
            case PROP_ID_bizObjName:{
               onInitProp(propId);
               this._bizObjName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_bizKey:{
               onInitProp(propId);
               this._bizKey = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_bizDate:{
               onInitProp(propId);
               this._bizDate = (java.time.LocalDate)value;
               
               break;
            }
        
            case PROP_ID_partitionIndex:{
               onInitProp(propId);
               this._partitionIndex = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_retryTimes:{
               onInitProp(propId);
               this._retryTimes = (java.lang.Integer)value;
               
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
     * 事件ID: EVENT_ID
     */
    public final java.lang.Long getEventId(){
         onPropGet(PROP_ID_eventId);
         return _eventId;
    }

    /**
     * 事件ID: EVENT_ID
     */
    public final void setEventId(java.lang.Long value){
        if(onPropSet(PROP_ID_eventId,value)){
            this._eventId = value;
            internalClearRefs(PROP_ID_eventId);
            orm_id();
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
     * 事件名称: EVENT_NAME
     */
    public final java.lang.String getEventName(){
         onPropGet(PROP_ID_eventName);
         return _eventName;
    }

    /**
     * 事件名称: EVENT_NAME
     */
    public final void setEventName(java.lang.String value){
        if(onPropSet(PROP_ID_eventName,value)){
            this._eventName = value;
            internalClearRefs(PROP_ID_eventName);
            
        }
    }
    
    /**
     * 事件元数据: EVENT_HEADERS
     */
    public final java.lang.String getEventHeaders(){
         onPropGet(PROP_ID_eventHeaders);
         return _eventHeaders;
    }

    /**
     * 事件元数据: EVENT_HEADERS
     */
    public final void setEventHeaders(java.lang.String value){
        if(onPropSet(PROP_ID_eventHeaders,value)){
            this._eventHeaders = value;
            internalClearRefs(PROP_ID_eventHeaders);
            
        }
    }
    
    /**
     * 数据: EVENT_DATA
     */
    public final java.lang.String getEventData(){
         onPropGet(PROP_ID_eventData);
         return _eventData;
    }

    /**
     * 数据: EVENT_DATA
     */
    public final void setEventData(java.lang.String value){
        if(onPropSet(PROP_ID_eventData,value)){
            this._eventData = value;
            internalClearRefs(PROP_ID_eventData);
            
        }
    }
    
    /**
     * 字段选择: SELECTION
     */
    public final java.lang.String getSelection(){
         onPropGet(PROP_ID_selection);
         return _selection;
    }

    /**
     * 字段选择: SELECTION
     */
    public final void setSelection(java.lang.String value){
        if(onPropSet(PROP_ID_selection,value)){
            this._selection = value;
            internalClearRefs(PROP_ID_selection);
            
        }
    }
    
    /**
     * 事件时间: EVENT_TIME
     */
    public final java.sql.Timestamp getEventTime(){
         onPropGet(PROP_ID_eventTime);
         return _eventTime;
    }

    /**
     * 事件时间: EVENT_TIME
     */
    public final void setEventTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_eventTime,value)){
            this._eventTime = value;
            internalClearRefs(PROP_ID_eventTime);
            
        }
    }
    
    /**
     * 事件状态: EVENT_STATUS
     */
    public final java.lang.Integer getEventStatus(){
         onPropGet(PROP_ID_eventStatus);
         return _eventStatus;
    }

    /**
     * 事件状态: EVENT_STATUS
     */
    public final void setEventStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_eventStatus,value)){
            this._eventStatus = value;
            internalClearRefs(PROP_ID_eventStatus);
            
        }
    }
    
    /**
     * 处理时间: PROCESS_TIME
     */
    public final java.sql.Timestamp getProcessTime(){
         onPropGet(PROP_ID_processTime);
         return _processTime;
    }

    /**
     * 处理时间: PROCESS_TIME
     */
    public final void setProcessTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_processTime,value)){
            this._processTime = value;
            internalClearRefs(PROP_ID_processTime);
            
        }
    }
    
    /**
     * 调度时间: SCHEDULE_TIME
     */
    public final java.sql.Timestamp getScheduleTime(){
         onPropGet(PROP_ID_scheduleTime);
         return _scheduleTime;
    }

    /**
     * 调度时间: SCHEDULE_TIME
     */
    public final void setScheduleTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_scheduleTime,value)){
            this._scheduleTime = value;
            internalClearRefs(PROP_ID_scheduleTime);
            
        }
    }
    
    /**
     * 是否广播: IS_BROADCAST
     */
    public final java.lang.Boolean getIsBroadcast(){
         onPropGet(PROP_ID_isBroadcast);
         return _isBroadcast;
    }

    /**
     * 是否广播: IS_BROADCAST
     */
    public final void setIsBroadcast(java.lang.Boolean value){
        if(onPropSet(PROP_ID_isBroadcast,value)){
            this._isBroadcast = value;
            internalClearRefs(PROP_ID_isBroadcast);
            
        }
    }
    
    /**
     * 业务对象名: BIZ_OBJ_NAME
     */
    public final java.lang.String getBizObjName(){
         onPropGet(PROP_ID_bizObjName);
         return _bizObjName;
    }

    /**
     * 业务对象名: BIZ_OBJ_NAME
     */
    public final void setBizObjName(java.lang.String value){
        if(onPropSet(PROP_ID_bizObjName,value)){
            this._bizObjName = value;
            internalClearRefs(PROP_ID_bizObjName);
            
        }
    }
    
    /**
     * 业务标识: BIZ_KEY
     */
    public final java.lang.String getBizKey(){
         onPropGet(PROP_ID_bizKey);
         return _bizKey;
    }

    /**
     * 业务标识: BIZ_KEY
     */
    public final void setBizKey(java.lang.String value){
        if(onPropSet(PROP_ID_bizKey,value)){
            this._bizKey = value;
            internalClearRefs(PROP_ID_bizKey);
            
        }
    }
    
    /**
     * 业务日期: BIZ_DATE
     */
    public final java.time.LocalDate getBizDate(){
         onPropGet(PROP_ID_bizDate);
         return _bizDate;
    }

    /**
     * 业务日期: BIZ_DATE
     */
    public final void setBizDate(java.time.LocalDate value){
        if(onPropSet(PROP_ID_bizDate,value)){
            this._bizDate = value;
            internalClearRefs(PROP_ID_bizDate);
            
        }
    }
    
    /**
     * 数据分区: PARTITION_INDEX
     */
    public final java.lang.Integer getPartitionIndex(){
         onPropGet(PROP_ID_partitionIndex);
         return _partitionIndex;
    }

    /**
     * 数据分区: PARTITION_INDEX
     */
    public final void setPartitionIndex(java.lang.Integer value){
        if(onPropSet(PROP_ID_partitionIndex,value)){
            this._partitionIndex = value;
            internalClearRefs(PROP_ID_partitionIndex);
            
        }
    }
    
    /**
     * 重试次数: RETRY_TIMES
     */
    public final java.lang.Integer getRetryTimes(){
         onPropGet(PROP_ID_retryTimes);
         return _retryTimes;
    }

    /**
     * 重试次数: RETRY_TIMES
     */
    public final void setRetryTimes(java.lang.Integer value){
        if(onPropSet(PROP_ID_retryTimes,value)){
            this._retryTimes = value;
            internalClearRefs(PROP_ID_retryTimes);
            
        }
    }
    
    /**
     * 数据版本: VERSION
     */
    public final java.lang.Integer getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
     */
    public final void setVersion(java.lang.Integer value){
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
