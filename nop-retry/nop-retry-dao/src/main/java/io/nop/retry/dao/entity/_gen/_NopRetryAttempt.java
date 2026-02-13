package io.nop.retry.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.retry.dao.entity.NopRetryAttempt;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  重试尝试: nop_retry_attempt
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopRetryAttempt extends DynamicOrmEntity{
    
    /* 主键: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 记录ID: RECORD_ID VARCHAR */
    public static final String PROP_NAME_recordId = "recordId";
    public static final int PROP_ID_recordId = 2;
    
    /* 尝试序号: ATTEMPT_NO INTEGER */
    public static final String PROP_NAME_attemptNo = "attemptNo";
    public static final int PROP_ID_attemptNo = 3;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 4;
    
    /* 开始时间: START_TIME TIMESTAMP */
    public static final String PROP_NAME_startTime = "startTime";
    public static final int PROP_ID_startTime = 5;
    
    /* 结束时间: END_TIME TIMESTAMP */
    public static final String PROP_NAME_endTime = "endTime";
    public static final int PROP_ID_endTime = 6;
    
    /* 持续时间(毫秒): DURATION_MS BIGINT */
    public static final String PROP_NAME_durationMs = "durationMs";
    public static final int PROP_ID_durationMs = 7;
    
    /* 错误码: ERROR_CODE VARCHAR */
    public static final String PROP_NAME_errorCode = "errorCode";
    public static final int PROP_ID_errorCode = 8;
    
    /* 错误消息: ERROR_MESSAGE VARCHAR */
    public static final String PROP_NAME_errorMessage = "errorMessage";
    public static final int PROP_ID_errorMessage = 9;
    
    /* 错误堆栈: ERROR_STACK VARCHAR */
    public static final String PROP_NAME_errorStack = "errorStack";
    public static final int PROP_ID_errorStack = 10;
    
    /* 客户端地址: CLIENT_ADDRESS VARCHAR */
    public static final String PROP_NAME_clientAddress = "clientAddress";
    public static final int PROP_ID_clientAddress = 11;
    
    /* 原因: REASON VARCHAR */
    public static final String PROP_NAME_reason = "reason";
    public static final int PROP_ID_reason = 12;
    
    /* 请求参数快照: REQUEST_PAYLOAD_SNAPSHOT VARCHAR */
    public static final String PROP_NAME_requestPayloadSnapshot = "requestPayloadSnapshot";
    public static final int PROP_ID_requestPayloadSnapshot = 13;
    
    /* 版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 14;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 15;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 16;
    
    /* 更新人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 17;
    
    /* 更新时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 18;
    

    private static int _PROP_ID_BOUND = 19;

    
    /* relation: 记录 */
    public static final String PROP_NAME_record = "record";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[19];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_recordId] = PROP_NAME_recordId;
          PROP_NAME_TO_ID.put(PROP_NAME_recordId, PROP_ID_recordId);
      
          PROP_ID_TO_NAME[PROP_ID_attemptNo] = PROP_NAME_attemptNo;
          PROP_NAME_TO_ID.put(PROP_NAME_attemptNo, PROP_ID_attemptNo);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_startTime] = PROP_NAME_startTime;
          PROP_NAME_TO_ID.put(PROP_NAME_startTime, PROP_ID_startTime);
      
          PROP_ID_TO_NAME[PROP_ID_endTime] = PROP_NAME_endTime;
          PROP_NAME_TO_ID.put(PROP_NAME_endTime, PROP_ID_endTime);
      
          PROP_ID_TO_NAME[PROP_ID_durationMs] = PROP_NAME_durationMs;
          PROP_NAME_TO_ID.put(PROP_NAME_durationMs, PROP_ID_durationMs);
      
          PROP_ID_TO_NAME[PROP_ID_errorCode] = PROP_NAME_errorCode;
          PROP_NAME_TO_ID.put(PROP_NAME_errorCode, PROP_ID_errorCode);
      
          PROP_ID_TO_NAME[PROP_ID_errorMessage] = PROP_NAME_errorMessage;
          PROP_NAME_TO_ID.put(PROP_NAME_errorMessage, PROP_ID_errorMessage);
      
          PROP_ID_TO_NAME[PROP_ID_errorStack] = PROP_NAME_errorStack;
          PROP_NAME_TO_ID.put(PROP_NAME_errorStack, PROP_ID_errorStack);
      
          PROP_ID_TO_NAME[PROP_ID_clientAddress] = PROP_NAME_clientAddress;
          PROP_NAME_TO_ID.put(PROP_NAME_clientAddress, PROP_ID_clientAddress);
      
          PROP_ID_TO_NAME[PROP_ID_reason] = PROP_NAME_reason;
          PROP_NAME_TO_ID.put(PROP_NAME_reason, PROP_ID_reason);
      
          PROP_ID_TO_NAME[PROP_ID_requestPayloadSnapshot] = PROP_NAME_requestPayloadSnapshot;
          PROP_NAME_TO_ID.put(PROP_NAME_requestPayloadSnapshot, PROP_ID_requestPayloadSnapshot);
      
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
    
    /* 记录ID: RECORD_ID */
    private java.lang.String _recordId;
    
    /* 尝试序号: ATTEMPT_NO */
    private java.lang.Integer _attemptNo;
    
    /* 状态: STATUS */
    private java.lang.Integer _status;
    
    /* 开始时间: START_TIME */
    private java.sql.Timestamp _startTime;
    
    /* 结束时间: END_TIME */
    private java.sql.Timestamp _endTime;
    
    /* 持续时间(毫秒): DURATION_MS */
    private java.lang.Long _durationMs;
    
    /* 错误码: ERROR_CODE */
    private java.lang.String _errorCode;
    
    /* 错误消息: ERROR_MESSAGE */
    private java.lang.String _errorMessage;
    
    /* 错误堆栈: ERROR_STACK */
    private java.lang.String _errorStack;
    
    /* 客户端地址: CLIENT_ADDRESS */
    private java.lang.String _clientAddress;
    
    /* 原因: REASON */
    private java.lang.String _reason;
    
    /* 请求参数快照: REQUEST_PAYLOAD_SNAPSHOT */
    private java.lang.String _requestPayloadSnapshot;
    
    /* 版本: VERSION */
    private java.lang.Integer _version;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 更新人: UPDATED_BY */
    private java.lang.String _updatedBy;
    
    /* 更新时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    

    public _NopRetryAttempt(){
        // for debug
    }

    protected NopRetryAttempt newInstance(){
        NopRetryAttempt entity = new NopRetryAttempt();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopRetryAttempt cloneInstance() {
        NopRetryAttempt entity = newInstance();
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
      return "io.nop.retry.dao.entity.NopRetryAttempt";
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
        
            case PROP_ID_recordId:
               return getRecordId();
        
            case PROP_ID_attemptNo:
               return getAttemptNo();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_startTime:
               return getStartTime();
        
            case PROP_ID_endTime:
               return getEndTime();
        
            case PROP_ID_durationMs:
               return getDurationMs();
        
            case PROP_ID_errorCode:
               return getErrorCode();
        
            case PROP_ID_errorMessage:
               return getErrorMessage();
        
            case PROP_ID_errorStack:
               return getErrorStack();
        
            case PROP_ID_clientAddress:
               return getClientAddress();
        
            case PROP_ID_reason:
               return getReason();
        
            case PROP_ID_requestPayloadSnapshot:
               return getRequestPayloadSnapshot();
        
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
        
            case PROP_ID_recordId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_recordId));
               }
               setRecordId(typedValue);
               break;
            }
        
            case PROP_ID_attemptNo:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_attemptNo));
               }
               setAttemptNo(typedValue);
               break;
            }
        
            case PROP_ID_status:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_status));
               }
               setStatus(typedValue);
               break;
            }
        
            case PROP_ID_startTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_startTime));
               }
               setStartTime(typedValue);
               break;
            }
        
            case PROP_ID_endTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_endTime));
               }
               setEndTime(typedValue);
               break;
            }
        
            case PROP_ID_durationMs:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_durationMs));
               }
               setDurationMs(typedValue);
               break;
            }
        
            case PROP_ID_errorCode:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_errorCode));
               }
               setErrorCode(typedValue);
               break;
            }
        
            case PROP_ID_errorMessage:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_errorMessage));
               }
               setErrorMessage(typedValue);
               break;
            }
        
            case PROP_ID_errorStack:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_errorStack));
               }
               setErrorStack(typedValue);
               break;
            }
        
            case PROP_ID_clientAddress:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_clientAddress));
               }
               setClientAddress(typedValue);
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
        
            case PROP_ID_requestPayloadSnapshot:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_requestPayloadSnapshot));
               }
               setRequestPayloadSnapshot(typedValue);
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
        
            case PROP_ID_sid:{
               onInitProp(propId);
               this._sid = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_recordId:{
               onInitProp(propId);
               this._recordId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_attemptNo:{
               onInitProp(propId);
               this._attemptNo = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_startTime:{
               onInitProp(propId);
               this._startTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_endTime:{
               onInitProp(propId);
               this._endTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_durationMs:{
               onInitProp(propId);
               this._durationMs = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_errorCode:{
               onInitProp(propId);
               this._errorCode = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_errorMessage:{
               onInitProp(propId);
               this._errorMessage = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_errorStack:{
               onInitProp(propId);
               this._errorStack = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_clientAddress:{
               onInitProp(propId);
               this._clientAddress = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_reason:{
               onInitProp(propId);
               this._reason = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_requestPayloadSnapshot:{
               onInitProp(propId);
               this._requestPayloadSnapshot = (java.lang.String)value;
               
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
     * 记录ID: RECORD_ID
     */
    public final java.lang.String getRecordId(){
         onPropGet(PROP_ID_recordId);
         return _recordId;
    }

    /**
     * 记录ID: RECORD_ID
     */
    public final void setRecordId(java.lang.String value){
        if(onPropSet(PROP_ID_recordId,value)){
            this._recordId = value;
            internalClearRefs(PROP_ID_recordId);
            
        }
    }
    
    /**
     * 尝试序号: ATTEMPT_NO
     */
    public final java.lang.Integer getAttemptNo(){
         onPropGet(PROP_ID_attemptNo);
         return _attemptNo;
    }

    /**
     * 尝试序号: ATTEMPT_NO
     */
    public final void setAttemptNo(java.lang.Integer value){
        if(onPropSet(PROP_ID_attemptNo,value)){
            this._attemptNo = value;
            internalClearRefs(PROP_ID_attemptNo);
            
        }
    }
    
    /**
     * 状态: STATUS
     */
    public final java.lang.Integer getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 状态: STATUS
     */
    public final void setStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    /**
     * 开始时间: START_TIME
     */
    public final java.sql.Timestamp getStartTime(){
         onPropGet(PROP_ID_startTime);
         return _startTime;
    }

    /**
     * 开始时间: START_TIME
     */
    public final void setStartTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_startTime,value)){
            this._startTime = value;
            internalClearRefs(PROP_ID_startTime);
            
        }
    }
    
    /**
     * 结束时间: END_TIME
     */
    public final java.sql.Timestamp getEndTime(){
         onPropGet(PROP_ID_endTime);
         return _endTime;
    }

    /**
     * 结束时间: END_TIME
     */
    public final void setEndTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_endTime,value)){
            this._endTime = value;
            internalClearRefs(PROP_ID_endTime);
            
        }
    }
    
    /**
     * 持续时间(毫秒): DURATION_MS
     */
    public final java.lang.Long getDurationMs(){
         onPropGet(PROP_ID_durationMs);
         return _durationMs;
    }

    /**
     * 持续时间(毫秒): DURATION_MS
     */
    public final void setDurationMs(java.lang.Long value){
        if(onPropSet(PROP_ID_durationMs,value)){
            this._durationMs = value;
            internalClearRefs(PROP_ID_durationMs);
            
        }
    }
    
    /**
     * 错误码: ERROR_CODE
     */
    public final java.lang.String getErrorCode(){
         onPropGet(PROP_ID_errorCode);
         return _errorCode;
    }

    /**
     * 错误码: ERROR_CODE
     */
    public final void setErrorCode(java.lang.String value){
        if(onPropSet(PROP_ID_errorCode,value)){
            this._errorCode = value;
            internalClearRefs(PROP_ID_errorCode);
            
        }
    }
    
    /**
     * 错误消息: ERROR_MESSAGE
     */
    public final java.lang.String getErrorMessage(){
         onPropGet(PROP_ID_errorMessage);
         return _errorMessage;
    }

    /**
     * 错误消息: ERROR_MESSAGE
     */
    public final void setErrorMessage(java.lang.String value){
        if(onPropSet(PROP_ID_errorMessage,value)){
            this._errorMessage = value;
            internalClearRefs(PROP_ID_errorMessage);
            
        }
    }
    
    /**
     * 错误堆栈: ERROR_STACK
     */
    public final java.lang.String getErrorStack(){
         onPropGet(PROP_ID_errorStack);
         return _errorStack;
    }

    /**
     * 错误堆栈: ERROR_STACK
     */
    public final void setErrorStack(java.lang.String value){
        if(onPropSet(PROP_ID_errorStack,value)){
            this._errorStack = value;
            internalClearRefs(PROP_ID_errorStack);
            
        }
    }
    
    /**
     * 客户端地址: CLIENT_ADDRESS
     */
    public final java.lang.String getClientAddress(){
         onPropGet(PROP_ID_clientAddress);
         return _clientAddress;
    }

    /**
     * 客户端地址: CLIENT_ADDRESS
     */
    public final void setClientAddress(java.lang.String value){
        if(onPropSet(PROP_ID_clientAddress,value)){
            this._clientAddress = value;
            internalClearRefs(PROP_ID_clientAddress);
            
        }
    }
    
    /**
     * 原因: REASON
     */
    public final java.lang.String getReason(){
         onPropGet(PROP_ID_reason);
         return _reason;
    }

    /**
     * 原因: REASON
     */
    public final void setReason(java.lang.String value){
        if(onPropSet(PROP_ID_reason,value)){
            this._reason = value;
            internalClearRefs(PROP_ID_reason);
            
        }
    }
    
    /**
     * 请求参数快照: REQUEST_PAYLOAD_SNAPSHOT
     */
    public final java.lang.String getRequestPayloadSnapshot(){
         onPropGet(PROP_ID_requestPayloadSnapshot);
         return _requestPayloadSnapshot;
    }

    /**
     * 请求参数快照: REQUEST_PAYLOAD_SNAPSHOT
     */
    public final void setRequestPayloadSnapshot(java.lang.String value){
        if(onPropSet(PROP_ID_requestPayloadSnapshot,value)){
            this._requestPayloadSnapshot = value;
            internalClearRefs(PROP_ID_requestPayloadSnapshot);
            
        }
    }
    
    /**
     * 版本: VERSION
     */
    public final java.lang.Integer getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 版本: VERSION
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
     * 更新人: UPDATED_BY
     */
    public final java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 更新人: UPDATED_BY
     */
    public final void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
        }
    }
    
    /**
     * 更新时间: UPDATE_TIME
     */
    public final java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 更新时间: UPDATE_TIME
     */
    public final void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
        }
    }
    
    /**
     * 记录
     */
    public final io.nop.retry.dao.entity.NopRetryRecord getRecord(){
       return (io.nop.retry.dao.entity.NopRetryRecord)internalGetRefEntity(PROP_NAME_record);
    }

    public final void setRecord(io.nop.retry.dao.entity.NopRetryRecord refEntity){
   
           if(refEntity == null){
           
                   this.setRecordId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_record, refEntity,()->{
           
                           this.setRecordId(refEntity.getSid());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
